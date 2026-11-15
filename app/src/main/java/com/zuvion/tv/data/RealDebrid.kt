package com.zuvion.tv.data

import com.zuvion.tv.source.Http
import com.zuvion.tv.source.arr
import com.zuvion.tv.source.int
import com.zuvion.tv.source.obj
import com.zuvion.tv.source.str
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.long

/** Converte torrents dos addons em link direto pelo Real-Debrid (com o token da API do usuário). */
object RealDebrid {
    private const val API = "https://api.real-debrid.com/rest/1.0"
    private val videoExt = listOf(".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".webm", ".wmv")

    class NotCached : Exception("Este torrent ainda não está no Real-Debrid. O download foi iniciado lá: tente de novo em alguns minutos.")

    private fun auth(token: String) = mapOf("Authorization" to "Bearer ${token.trim()}")

    /** Nome do usuário (valida o token). */
    suspend fun user(token: String): String =
        Http.getJson("$API/user", auth(token)).obj()?.str("username") ?: throw IllegalStateException("Token inválido")

    private suspend fun info(token: String, id: String): JsonObject =
        Http.getJson("$API/torrents/info/$id", auth(token)).obj() ?: throw IllegalStateException("Resposta vazia do Real-Debrid")

    /** Arquivo escolhido: o nome indicado pelo addon, senão a posição indicada, senão o maior vídeo. */
    private fun pickFile(files: List<JsonObject>, fileIdx: Int?, filename: String?): JsonObject? {
        fun path(f: JsonObject) = f.str("path").orEmpty()
        if (!filename.isNullOrBlank()) files.firstOrNull { path(it).endsWith(filename, ignoreCase = true) }?.let { return it }
        if (fileIdx != null) files.firstOrNull { it.int("id") == fileIdx + 1 }?.let { return it }
        val videos = files.filter { f -> videoExt.any { path(f).lowercase().endsWith(it) } }
        return (videos.ifEmpty { files }).maxByOrNull { (it["bytes"] as? kotlinx.serialization.json.JsonPrimitive)?.long ?: 0L }
    }

    /** Link do arquivo escolhido em um torrent já baixado: os links seguem a ordem dos arquivos selecionados. */
    private fun linkFor(info: JsonObject, fileId: Int): String? {
        val selected = info["files"].arr().orEmpty().mapNotNull { it.obj() }.filter { it.int("selected") == 1 }
        val pos = selected.indexOfFirst { it.int("id") == fileId }
        val links = info["links"].arr().orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        return links.getOrNull(if (pos >= 0) pos else 0)
    }

    suspend fun resolve(token: String, infoHash: String, fileIdx: Int?, filename: String?): String {
        val h = infoHash.lowercase()
        val headers = auth(token)
        // Reaproveita o torrent se ele já estiver na conta (evita duplicar).
        val existing = runCatching { Http.getJson("$API/torrents?limit=100", headers).arr() }.getOrNull().orEmpty()
            .mapNotNull { it.obj() }.firstOrNull { it.str("hash")?.lowercase() == h && it.str("status") == "downloaded" }
        if (existing != null) {
            val inf = info(token, existing.str("id")!!)
            val files = inf["files"].arr().orEmpty().mapNotNull { it.obj() }
            val f = pickFile(files, fileIdx, filename)
            if (f != null && f.int("selected") == 1) linkFor(inf, f.int("id"))?.let { return unrestrict(token, it) }
        }
        val added = Http.post("$API/torrents/addMagnet", mapOf("magnet" to "magnet:?xt=urn:btih:$h"), headers).let { com.zuvion.tv.data.AppJson.parseToJsonElement(it) }.obj()
        val id = added?.str("id") ?: throw IllegalStateException("O Real-Debrid não aceitou o torrent")
        var inf = info(token, id)
        // Espera a lista de arquivos (torrents no cache aparecem na hora).
        var tries = 0
        while (inf.str("status") == "magnet_conversion" && tries++ < 8) { delay(1000); inf = info(token, id) }
        val files = inf["files"].arr().orEmpty().mapNotNull { it.obj() }
        val file = pickFile(files, fileIdx, filename) ?: throw IllegalStateException("Nenhum arquivo de vídeo no torrent")
        if (inf.str("status") == "waiting_files_selection") {
            Http.post("$API/torrents/selectFiles/$id", mapOf("files" to file.int("id").toString()), headers)
        }
        tries = 0
        while (tries++ < 10) {
            inf = info(token, id)
            when (inf.str("status")) {
                "downloaded" -> return unrestrict(token, linkFor(inf, file.int("id")) ?: throw IllegalStateException("Link indisponível"))
                "magnet_error", "error", "virus", "dead" -> throw IllegalStateException("O Real-Debrid não conseguiu usar este torrent")
            }
            delay(1500)
        }
        throw NotCached()
    }

    private suspend fun unrestrict(token: String, link: String): String {
        val o = com.zuvion.tv.data.AppJson.parseToJsonElement(Http.post("$API/unrestrict/link", mapOf("link" to link), auth(token))).obj()
        return o?.str("download") ?: throw IllegalStateException("O Real-Debrid não liberou o link")
    }
}
