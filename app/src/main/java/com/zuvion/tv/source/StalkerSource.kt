package com.zuvion.tv.source

import com.zuvion.tv.data.Category
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.EpgProgram
import com.zuvion.tv.data.Kind
import com.zuvion.tv.data.Playlist
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

/** Portal Stalker/Ministra (login por MAC, estilo MAG). */
class StalkerSource(override val playlist: Playlist) : IptvSource {
    private val portal = normalizeBase(playlist.url).removeSuffix("/c")
    private val mac = playlist.mac.trim().uppercase()
    private var token: String? = null
    private var endpoint: String? = null
    private val lock = Mutex()

    private fun headers(): Map<String, String> = buildMap {
        put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
        put("X-User-Agent", "Model: MAG250; Link: WiFi")
        put("Cookie", "mac=${URLEncoder.encode(mac, "UTF-8")}; stb_lang=en; timezone=America/Sao_Paulo")
        put("Referer", "$portal/c/")
        token?.let { put("Authorization", "Bearer $it") }
    }

    private suspend fun ensureAuth() = lock.withLock {
        if (token != null) return@withLock
        val candidates = if (portal.contains("stalker_portal"))
            listOf("$portal/server/load.php", "${portal.substringBefore("/stalker_portal")}/stalker_portal/server/load.php")
        else listOf("$portal/portal.php", "$portal/stalker_portal/server/load.php", "$portal/server/load.php")
        var lastErr: Throwable? = null
        for (ep in candidates) {
            try {
                val j = Http.getJson("$ep?type=stb&action=handshake&token=&JsHttpRequest=1-xml", headers()).obj()
                val t = j?.get("js").obj()?.str("token")
                if (t != null) {
                    endpoint = ep; token = t
                    runCatching { Http.getJson("$ep?type=stb&action=get_profile&hd=1&JsHttpRequest=1-xml", headers()) }
                    return@withLock
                }
            } catch (e: Throwable) { lastErr = e }
        }
        throw IllegalStateException("Portal não respondeu ao login por MAC" + (lastErr?.message?.let { " ($it)" } ?: ""))
    }

    private suspend fun call(query: String): JsonElement? {
        ensureAuth()
        return Http.getJson("$endpoint?$query&JsHttpRequest=1-xml", headers()).obj()?.get("js")
    }

    suspend fun authenticate() = ensureAuth()

    override suspend fun categories(kind: Kind): List<Category> {
        val q = when (kind) {
            Kind.LIVE -> "type=itv&action=get_genres"
            Kind.MOVIE -> "type=vod&action=get_categories"
            Kind.SERIES -> "type=series&action=get_categories"
            else -> return emptyList()
        }
        return runCatching { call(q) }.getOrNull().arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            if (id == "*") null else Category(id, o.str("title") ?: "Sem nome", kind)
        }
    }

    override suspend fun items(kind: Kind): List<Content>? = when (kind) {
        Kind.LIVE -> call("type=itv&action=get_all_channels").obj()?.get("data").arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Content(
                id = o.str("id") ?: return@mapNotNull null, kind = Kind.LIVE,
                name = o.str("name") ?: "Canal", playlistId = playlist.id,
                poster = o.str("logo")?.let { if (it.startsWith("http")) it else null },
                categoryId = o.str("tv_genre_id") ?: "", streamUrl = o.str("cmd"),
                epgId = o.str("xmltv_id"), number = o.int("number"),
                catchupDays = if (o.int("tv_archive") == 1) o.int("tv_archive_duration").coerceAtLeast(1) else 0
            )
        }
        else -> null // filmes/séries: carregados por categoria
    }

    override suspend fun itemsInCategory(kind: Kind, categoryId: String): List<Content> {
        if (kind == Kind.LIVE) return items(kind)!!.filter { it.categoryId == categoryId }
        val type = if (kind == Kind.SERIES) "series" else "vod"
        val out = ArrayList<Content>()
        var page = 1
        while (page <= 15) {
            val js = runCatching { call("type=$type&action=get_ordered_list&category=$categoryId&p=$page&sortby=added") }.getOrNull().obj() ?: break
            val data = js["data"].arr().orEmpty()
            data.forEach { e ->
                val o = e.obj() ?: return@forEach
                out += Content(
                    id = o.str("id") ?: return@forEach, kind = if (kind == Kind.SERIES) Kind.SERIES else Kind.MOVIE,
                    name = o.str("name") ?: "Título", playlistId = playlist.id,
                    poster = o.str("screenshot_uri"), categoryId = categoryId, streamUrl = o.str("cmd"),
                    plot = o.str("description"), year = o.str("year"), rating = o.str("rating_imdb")
                )
            }
            val total = js.int("total_items"); val per = js.int("max_page_items").coerceAtLeast(1)
            if (data.isEmpty() || page * per >= total) break
            page++
        }
        return out
    }

    override suspend fun resolveUrl(item: Content): String {
        val cmd = item.streamUrl ?: error("Sem comando de stream")
        if (cmd.startsWith("http") && !cmd.contains("localhost")) return cmd.removePrefix("ffmpeg ")
        val type = if (item.kind == Kind.LIVE) "itv" else "vod"
        val js = call("type=$type&action=create_link&cmd=${URLEncoder.encode(cmd, "UTF-8")}").obj()
        val link = js?.str("cmd") ?: error("Portal não gerou link")
        return link.substringAfter("ffmpeg ").substringAfter("auto ").trim()
    }

    override suspend fun shortEpg(item: Content): List<EpgProgram> =
        runCatching { call("type=itv&action=get_short_epg&ch_id=${item.id}&size=6") }.getOrNull().arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            EpgProgram(item.epgId ?: item.id, o.long("start_timestamp") * 1000, o.long("stop_timestamp") * 1000,
                o.str("name") ?: "", o.str("descr") ?: "")
        }
}
