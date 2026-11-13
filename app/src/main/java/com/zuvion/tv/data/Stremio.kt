package com.zuvion.tv.data

import com.zuvion.tv.source.Http
import com.zuvion.tv.source.arr
import com.zuvion.tv.source.obj
import com.zuvion.tv.source.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/** Addon no formato do Stremio (manifest.json), usado só como fonte extra de links. */
data class StremioAddon(
    val manifestUrl: String,
    val name: String,
    val types: List<String>,
    val idPrefixes: List<String>?,
    val hasStreams: Boolean
) {
    val base: String get() = manifestUrl.removeSuffix("/manifest.json")
}

data class StremioStream(val addon: String, val title: String, val detail: String, val url: String)

object Stremio {
    private val manifests = ConcurrentHashMap<String, StremioAddon>()

    /** Aceita o link de instalação (stremio://…), com ou sem /manifest.json. */
    fun normalize(input: String): String {
        var u = input.trim()
        if (u.startsWith("stremio://")) u = "https://" + u.removePrefix("stremio://")
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        u = u.substringBefore('#').trimEnd('/')
        if (!u.endsWith("/manifest.json")) u = "$u/manifest.json"
        return u
    }

    /** Lê o manifest; lança exceção se o link não for de um addon. */
    suspend fun manifest(url: String): StremioAddon {
        manifests[url]?.let { return it }
        val o = Http.getJson(url).obj() ?: throw IllegalArgumentException("Resposta vazia")
        val name = o.str("name") ?: throw IllegalArgumentException("Não é um manifest de addon")
        val types = o["types"].arr().orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
        val resources = o["resources"].arr().orEmpty()
        var streams = false
        var prefixes: List<String>? = o["idPrefixes"].arr()?.mapNotNull { (it as? JsonPrimitive)?.content }
        var resTypes: List<String>? = null
        for (r in resources) {
            when {
                r is JsonPrimitive && r.content == "stream" -> streams = true
                r.obj()?.str("name") == "stream" -> {
                    streams = true
                    r.obj()?.get("idPrefixes").arr()?.mapNotNull { (it as? JsonPrimitive)?.content }?.let { prefixes = it }
                    resTypes = r.obj()?.get("types").arr()?.mapNotNull { (it as? JsonPrimitive)?.content }
                }
            }
        }
        val a = StremioAddon(url, name, resTypes ?: types, prefixes, streams)
        manifests[url] = a
        return a
    }

    /**
     * Links de todos os addons para um filme ("movie", "tt123") ou episódio ("series", "tt123:1:2").
     * Só entram links HTTP diretos (torrents e links externos ficam de fora).
     */
    suspend fun streams(addonUrls: List<String>, type: String, id: String): List<StremioStream> = withContext(Dispatchers.IO) {
        coroutineScope {
            addonUrls.map { u ->
                async {
                    withTimeoutOrNull(20_000) {
                        runCatching {
                            val a = manifest(u)
                            if (!a.hasStreams || (a.types.isNotEmpty() && type !in a.types)) return@runCatching emptyList<StremioStream>()
                            if (a.idPrefixes != null && a.idPrefixes.none { id.startsWith(it) }) return@runCatching emptyList<StremioStream>()
                            val o = Http.getJson("${a.base}/stream/$type/$id.json").obj()
                            o?.get("streams").arr().orEmpty().mapNotNull { it.obj() }.mapNotNull { s ->
                                val url = s.str("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return@mapNotNull null
                                val name = s.str("name")?.replace('\n', ' ')?.trim()
                                val desc = (s.str("title") ?: s.str("description"))?.trim().orEmpty()
                                StremioStream(a.name, name?.ifBlank { null } ?: desc.lineSequence().firstOrNull().orEmpty().ifBlank { a.name }, desc, url)
                            }
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
    }
}
