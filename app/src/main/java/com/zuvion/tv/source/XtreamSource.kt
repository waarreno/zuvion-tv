package com.zuvion.tv.source

import android.util.Base64
import com.zuvion.tv.data.Category
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.EpgProgram
import com.zuvion.tv.data.Kind
import com.zuvion.tv.data.Playlist
import com.zuvion.tv.data.SeriesInfo
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XtreamSource(override val playlist: Playlist, private val liveFormat: String) : IptvSource {
    private val base = normalizeBase(playlist.url)
    private val u = URLEncoder.encode(playlist.username, "UTF-8")
    private val p = URLEncoder.encode(playlist.password, "UTF-8")
    // Para montar URLs de stream usamos usuário/senha crus (padrão Xtream).
    private val ru = playlist.username
    private val rp = playlist.password

    private fun api(action: String, extra: String = "") =
        "$base/player_api.php?username=$u&password=$p&action=$action$extra"

    /** Testa o login. Lança erro com mensagem amigável. */
    suspend fun authenticate() {
        val j = Http.getJson("$base/player_api.php?username=$u&password=$p").obj()
            ?: error("Resposta inválida do servidor")
        val info = j["user_info"].obj() ?: error("Servidor não reconheceu o login")
        if (info.int("auth") == 0 && info.str("auth") != null) error("Usuário ou senha incorretos")
        val status = info.str("status")
        if (status != null && !status.equals("Active", true)) error("Conta $status")
    }

    override suspend fun categories(kind: Kind): List<Category> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIE -> "get_vod_categories"
            Kind.SERIES -> "get_series_categories"
            else -> return emptyList()
        }
        return Http.getJson(api(action)).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Category(o.str("category_id") ?: return@mapNotNull null, o.str("category_name") ?: "Sem nome", kind)
        }
    }

    override suspend fun items(kind: Kind): List<Content> = when (kind) {
        Kind.LIVE -> Http.getJson(api("get_live_streams")).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            Content(
                id = id, kind = Kind.LIVE, name = o.str("name") ?: "Canal",
                playlistId = playlist.id, poster = o.str("stream_icon"),
                categoryId = o.str("category_id") ?: "",
                streamUrl = "$base/live/$ru/$rp/$id.$liveFormat",
                epgId = o.str("epg_channel_id"), number = o.int("num"),
                catchupDays = if (o.int("tv_archive") == 1) o.int("tv_archive_duration") else 0,
                added = o.long("added")
            )
        }
        Kind.MOVIE -> Http.getJson(api("get_vod_streams")).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            val ext = o.str("container_extension") ?: "mp4"
            Content(
                id = id, kind = Kind.MOVIE, name = o.str("name") ?: "Filme",
                playlistId = playlist.id, poster = o.str("stream_icon"),
                categoryId = o.str("category_id") ?: "",
                streamUrl = "$base/movie/$ru/$rp/$id.$ext", extension = ext,
                rating = o.str("rating"), added = o.long("added")
            )
        }
        Kind.SERIES -> Http.getJson(api("get_series")).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.str("series_id") ?: return@mapNotNull null
            Content(
                id = id, kind = Kind.SERIES, name = o.str("name") ?: "Série",
                playlistId = playlist.id, poster = o.str("cover"),
                categoryId = o.str("category_id") ?: "",
                rating = o.str("rating"), plot = o.str("plot"),
                year = o.str("releaseDate")?.take(4), added = o.long("last_modified")
            )
        }
        else -> emptyList()
    }

    override suspend fun movieInfo(movie: Content): Content {
        val j = runCatching { Http.getJson(api("get_vod_info", "&vod_id=${movie.id}")).obj() }.getOrNull() ?: return movie
        val info = j["info"].obj() ?: return movie
        return movie.copy(
            plot = info.str("plot") ?: info.str("description") ?: movie.plot,
            year = info.str("releasedate")?.take(4) ?: info.str("release_date")?.take(4) ?: movie.year,
            poster = movie.poster ?: info.str("movie_image"),
            rating = info.str("rating") ?: movie.rating
        )
    }

    override suspend fun seriesInfo(series: Content): SeriesInfo {
        val j = Http.getJson(api("get_series_info", "&series_id=${series.id}")).obj()
            ?: return SeriesInfo(series.plot, series.poster, emptyMap())
        val info = j["info"].obj()
        val eps = j["episodes"].obj()
        val seasons = sortedMapOf<Int, List<Content>>()
        eps?.forEach { (seasonKey, list) ->
            val season = seasonKey.toIntOrNull() ?: 0
            seasons[season] = list.arr().orEmpty().mapNotNull { e ->
                val o = e.obj() ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val ext = o.str("container_extension") ?: "mp4"
                val ei = o["info"].obj()
                Content(
                    id = id, kind = Kind.EPISODE,
                    name = o.str("title") ?: "Episódio ${o.int("episode_num")}",
                    playlistId = playlist.id,
                    poster = ei?.str("movie_image") ?: series.poster,
                    streamUrl = "$base/series/$ru/$rp/$id.$ext", extension = ext,
                    plot = ei?.str("plot"), seriesId = series.id,
                    season = season, episode = o.int("episode_num")
                )
            }.sortedBy { it.episode }
        }
        return SeriesInfo(info?.str("plot") ?: series.plot, info?.str("cover") ?: series.poster, seasons)
    }

    override fun catchupUrl(item: Content, startMs: Long, endMs: Long): String? {
        if (item.kind != Kind.LIVE || item.catchupDays <= 0) return null
        val minutes = ((endMs - startMs) / 60000).coerceAtLeast(1)
        val start = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(startMs))
        return "$base/timeshift/$ru/$rp/$minutes/$start/${item.id}.ts"
    }

    override fun epgUrl(): String = playlist.epgUrl.takeIf { it.isNotBlank() } ?: "$base/xmltv.php?username=$u&password=$p"

    override suspend fun shortEpg(item: Content): List<EpgProgram> {
        val j = runCatching { Http.getJson(api("get_short_epg", "&stream_id=${item.id}&limit=6")).obj() }.getOrNull() ?: return emptyList()
        return j["epg_listings"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            EpgProgram(
                channel = item.epgId ?: item.id,
                start = o.long("start_timestamp") * 1000,
                end = o.long("stop_timestamp") * 1000,
                title = b64(o.str("title")) ?: "",
                desc = b64(o.str("description")) ?: ""
            )
        }.filter { it.end > it.start }
    }

    private fun b64(s: String?): String? = s?.let {
        runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(it)
    }
}
