package com.zuvion.tv.source

import android.content.Context
import com.zuvion.tv.data.Category
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.Kind
import com.zuvion.tv.data.Playlist
import com.zuvion.tv.data.SeriesInfo
import com.zuvion.tv.data.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lista M3U/M3U8 (URL ou arquivo local). Separa canais, filmes e séries por heurística. */
class M3uSource(private val context: Context, override val playlist: Playlist) : IptvSource {

    private class Parsed(
        val live: List<Content>, val movies: List<Content>,
        val series: List<Content>, val episodes: Map<String, List<Content>>,
        val cats: Map<Kind, List<Category>>, val tvgUrl: String?,
        val catchup: Map<String, String>
    )

    private var parsed: Parsed? = null
    private val lock = Mutex()

    private suspend fun data(): Parsed = lock.withLock {
        parsed ?: withContext(Dispatchers.IO) { parse() }.also { parsed = it }
    }

    private fun openReader(): BufferedReader = if (playlist.type == SourceType.M3U_FILE) {
        File(playlist.url).bufferedReader()
    } else {
        val req = Request.Builder().url(normalizeBase(playlist.url)).header("User-Agent", Http.USER_AGENT).build()
        val resp = Http.streamClient.newCall(req).execute()
        if (!resp.isSuccessful) { resp.close(); throw IOException("HTTP ${resp.code}") }
        BufferedReader(InputStreamReader(resp.body!!.byteStream(), Charsets.UTF_8))
    }

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val seRegex = Regex("""^(.*?)[\s._-]*[Ss](\d{1,2})\s*[Ee](\d{1,3})""")
    private val vodExt = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm")

    private fun parse(): Parsed {
        val live = ArrayList<Content>(); val movies = ArrayList<Content>()
        val seriesMap = LinkedHashMap<String, Content>()
        val episodes = HashMap<String, MutableList<Content>>()
        val cats = HashMap<Kind, LinkedHashMap<String, Category>>()
        val catchup = HashMap<String, String>()
        var tvgUrl: String? = null
        var attrs: Map<String, String> = emptyMap()
        var title = ""
        var idx = 0
        openReader().use { r ->
            while (true) {
                val raw = r.readLine() ?: break
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#EXTM3U")) {
                    val a = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    tvgUrl = a["url-tvg"] ?: a["x-tvg-url"]
                    continue
                }
                if (line.startsWith("#EXTINF")) {
                    attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    title = line.substringAfterLast("\",", "").ifBlank { line.substringAfterLast(",") }.trim()
                    continue
                }
                if (line.startsWith("#")) continue
                // linha de URL
                val url = line
                val name = title.ifBlank { attrs["tvg-name"] ?: "Item ${idx + 1}" }
                val group = attrs["group-title"]?.ifBlank { null } ?: "Sem categoria"
                val logo = attrs["tvg-logo"]?.ifBlank { null }
                val lower = url.lowercase().substringBefore('?')
                val ext = lower.substringAfterLast('.', "")
                val kind = when {
                    lower.contains("/series/") -> Kind.SERIES
                    lower.contains("/movie/") || ext in vodExt -> Kind.MOVIE
                    else -> Kind.LIVE
                }
                val catKind = kind
                val catId = "${catKind.name}:$group"
                cats.getOrPut(catKind) { LinkedHashMap() }.getOrPut(catId) { Category(catId, group, catKind) }
                val id = "m${idx++}"
                when (kind) {
                    Kind.LIVE -> {
                        live += Content(
                            id = id, kind = Kind.LIVE, name = name, playlistId = playlist.id, poster = logo,
                            categoryId = catId, streamUrl = url, epgId = attrs["tvg-id"]?.ifBlank { null } ?: attrs["tvg-name"],
                            number = live.size + 1,
                            catchupDays = attrs["catchup-days"]?.toIntOrNull() ?: attrs["timeshift"]?.toIntOrNull() ?: 0
                        )
                        attrs["catchup-source"]?.let { catchup[id] = it }
                    }
                    Kind.MOVIE -> movies += Content(
                        id = id, kind = Kind.MOVIE, name = name, playlistId = playlist.id, poster = logo,
                        categoryId = catId, streamUrl = url, extension = ext.ifBlank { null }
                    )
                    else -> {
                        val m = seRegex.find(name)
                        val show = m?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: name
                        val sid = "s:" + show.lowercase()
                        seriesMap.getOrPut(sid) {
                            Content(id = sid, kind = Kind.SERIES, name = show, playlistId = playlist.id, poster = logo, categoryId = catId)
                        }
                        episodes.getOrPut(sid) { ArrayList() } += Content(
                            id = id, kind = Kind.EPISODE, name = name, playlistId = playlist.id, poster = logo,
                            streamUrl = url, seriesId = sid,
                            season = m?.groupValues?.get(2)?.toIntOrNull() ?: 1,
                            episode = m?.groupValues?.get(3)?.toIntOrNull() ?: (episodes[sid]?.size ?: 0) + 1
                        )
                    }
                }
                attrs = emptyMap(); title = ""
            }
        }
        return Parsed(live, movies, seriesMap.values.toList(), episodes,
            cats.mapValues { it.value.values.toList() }, tvgUrl, catchup)
    }

    override suspend fun categories(kind: Kind): List<Category> = data().cats[kind].orEmpty()

    override suspend fun items(kind: Kind): List<Content> = data().let {
        when (kind) { Kind.LIVE -> it.live; Kind.MOVIE -> it.movies; Kind.SERIES -> it.series; else -> emptyList() }
    }

    override suspend fun seriesInfo(series: Content): SeriesInfo {
        val eps = data().episodes[series.id].orEmpty()
        return SeriesInfo(series.plot, series.poster, eps.groupBy { it.season }.toSortedMap().mapValues { e -> e.value.sortedBy { it.episode } })
    }

    override fun epgUrl(): String? = playlist.epgUrl.ifBlank { parsed?.tvgUrl ?: "" }.takeIf { it.isNotBlank() }

    override fun catchupUrl(item: Content, startMs: Long, endMs: Long): String? {
        if (item.catchupDays <= 0) return null
        val tpl = parsed?.catchup?.get(item.id)
        val startS = startMs / 1000; val endS = endMs / 1000
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        return if (tpl != null) {
            tpl.replace("{utc}", "$startS").replace("\${start}", "$startS")
                .replace("{utcend}", "$endS").replace("\${end}", "$endS")
                .replace("{lutc}", "${System.currentTimeMillis() / 1000}")
                .replace("{duration}", "${endS - startS}")
                .replace("{Y}{m}{d}{H}{M}{S}", fmt.format(Date(startMs)))
        } else {
            val url = item.streamUrl ?: return null
            url + (if (url.contains('?')) "&" else "?") + "utc=$startS&lutc=${System.currentTimeMillis() / 1000}"
        }
    }
}
