package com.zuvion.tv.meta

import android.content.Context
import android.util.LruCache
import com.zuvion.tv.BuildConfig
import com.zuvion.tv.data.AppJson
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.Kind
import com.zuvion.tv.data.Store
import com.zuvion.tv.source.Http
import com.zuvion.tv.source.arr
import com.zuvion.tv.source.int
import com.zuvion.tv.source.obj
import com.zuvion.tv.source.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer

/** Ficha de um filme ou série vinda do TMDB (com notas extras do OMDb). */
@Serializable
data class Meta(
    val tmdbId: Int = 0,
    val title: String = "",
    val overview: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: Int = 0,
    val cast: List<String> = emptyList(),
    val director: String? = null,
    val imdbId: String? = null,
    val imdbRating: String? = null,
    val rotten: String? = null,
    val metascore: String? = null,
    val omdbChecked: Boolean = false,
    val savedAt: Long = 0
) {
    val found: Boolean get() = tmdbId > 0 || imdbId != null
}

@Serializable
data class EpisodeMeta(val episode: Int, val name: String = "", val overview: String = "", val still: String? = null, val runtime: Int = 0)

/** Trechos para pular (TheIntroDB), em milissegundos. */
data class SkipTimes(val introStart: Long, val introEnd: Long, val creditsStart: Long? = null)

/**
 * Metadados: TMDB primeiro, OMDb como complemento/reserva e TheIntroDB para pular aberturas.
 * Sem chave do TMDB/OMDb, o app usa apenas o que vem do servidor IPTV.
 */
class Metadata(private val context: Context, private val store: Store) {
    private val mem = LruCache<String, Meta>(600)
    private val seasons = LruCache<String, List<EpisodeMeta>>(60)
    private val gate = Semaphore(6)
    private val dir get() = File(context.cacheDir, "meta").apply { mkdirs() }

    // A chave do usuário (Ajustes) tem prioridade; senão, a do app (secrets do GitHub).
    private val tmdbKey get() = store.settings.tmdbKey.trim().ifEmpty { BuildConfig.TMDB_KEY }
    private val omdbKey get() = store.settings.omdbKey.trim().ifEmpty { BuildConfig.OMDB_KEY }
    private val introKey get() = store.settings.introDbKey.trim().ifEmpty { BuildConfig.INTRODB_KEY }
    val enabled: Boolean get() = tmdbKey.isNotEmpty() || omdbKey.isNotEmpty()

    fun cached(c: Content): Meta? = mem.get(cacheKey(c))

    /**
     * Ficha do filme/série; null quando não há chave ou nada foi encontrado.
     * [full] = true (tela de detalhes) também consulta o OMDb, que tem cota diária pequena.
     */
    suspend fun forContent(c: Content, full: Boolean = false): Meta? {
        if (!enabled || (c.kind != Kind.MOVIE && c.kind != Kind.SERIES)) return null
        val key = cacheKey(c)
        mem.get(key)?.let { m -> if (!full || m.omdbChecked || omdbKey.isEmpty() || !m.found && tmdbKey.isNotEmpty()) return m.takeIf { it.found } }
        return withContext(Dispatchers.IO) {
            val f = File(dir, md5(key) + ".json")
            val disk = runCatching { AppJson.decodeFromString(Meta.serializer(), f.readText()) }.getOrNull()
            val fresh = disk != null && System.currentTimeMillis() - disk.savedAt < (if (disk.found) 30L else 3L) * 86_400_000L
            var meta = if (fresh) disk!! else gate.withPermit { fetch(c) } ?: return@withContext disk?.takeIf { it.found }
            var changed = !fresh
            if (full && !meta.omdbChecked && omdbKey.isNotEmpty() && (meta.found || tmdbKey.isEmpty())) {
                val (title, y) = cleanTitle(c.name)
                meta = runCatching { omdb(meta.takeIf { it.found }, title, y ?: c.year?.take(4), c.kind == Kind.SERIES) }.getOrNull()
                    ?.copy(omdbChecked = true, savedAt = meta.savedAt.takeIf { it > 0 } ?: System.currentTimeMillis()) ?: meta
                changed = true
            }
            mem.put(key, meta)
            if (changed) runCatching { f.writeText(AppJson.encodeToString(Meta.serializer(), meta)) }
            meta.takeIf { it.found }
        }
    }

    private fun cacheKey(c: Content) = "${c.kind}|${cleanTitle(c.name).first.lowercase()}|${cleanTitle(c.name).second ?: c.year.orEmpty()}"

    /** null = erro de rede (não grava cache); Meta() vazio = não encontrado. */
    private suspend fun fetch(c: Content): Meta? {
        val (title, yearFromName) = cleanTitle(c.name)
        val year = yearFromName ?: c.year?.take(4)?.takeIf { it.length == 4 }
        val tv = c.kind == Kind.SERIES
        var meta: Meta? = null
        var networkOk = true
        if (tmdbKey.isNotEmpty()) {
            try { meta = tmdb(title, year, tv) } catch (_: Throwable) { networkOk = false }
        }
        // Sem TMDB, o OMDb vira a fonte principal (senão ele só é consultado nos detalhes).
        if (tmdbKey.isEmpty() && omdbKey.isNotEmpty()) {
            try { meta = omdb(meta, title, year, tv)?.copy(omdbChecked = true) } catch (_: Throwable) { networkOk = false }
        }
        if (meta == null && !networkOk) return null
        return (meta ?: Meta()).copy(savedAt = System.currentTimeMillis())
    }

    private fun tmdbUrl(path: String, query: String = ""): Pair<String, Map<String, String>> {
        val bearer = tmdbKey.startsWith("eyJ")
        val q = buildString {
            append("language=pt-BR")
            if (query.isNotEmpty()) append("&").append(query)
            if (!bearer) append("&api_key=").append(tmdbKey)
        }
        val headers = if (bearer) mapOf("Authorization" to "Bearer $tmdbKey", "Accept" to "application/json") else emptyMap()
        return "https://api.themoviedb.org/3$path?$q" to headers
    }

    private suspend fun tmdbGet(path: String, query: String = ""): JsonObject? {
        val (u, h) = tmdbUrl(path, query)
        return Http.getJson(u, h).obj()
    }

    private suspend fun tmdb(title: String, year: String?, tv: Boolean): Meta? {
        val type = if (tv) "tv" else "movie"
        val q = "query=" + enc(title) + (if (year != null) (if (tv) "&first_air_date_year=$year" else "&year=$year") else "")
        var results = tmdbGet("/search/$type", q)?.get("results").arr().orEmpty()
        if (results.isEmpty() && year != null) results = tmdbGet("/search/$type", "query=" + enc(title))?.get("results").arr().orEmpty()
        val hit = results.firstOrNull()?.obj() ?: return null
        val id = hit.int("id")
        val d = tmdbGet("/$type/$id", "append_to_response=credits,external_ids") ?: hit
        var overview = d.str("overview").orEmpty()
        if (overview.isBlank()) {
            overview = runCatching { Http.getJson(tmdbUrl("/$type/$id").first.replace("language=pt-BR", "language=en-US"), tmdbUrl("/$type/$id").second).obj()?.str("overview") }.getOrNull().orEmpty()
        }
        val credits = d["credits"].obj()
        return Meta(
            tmdbId = id,
            title = d.str(if (tv) "name" else "title") ?: title,
            overview = overview,
            poster = d.str("poster_path")?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdrop = d.str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
            year = (d.str(if (tv) "first_air_date" else "release_date"))?.take(4),
            rating = d.str("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(it) },
            genres = d["genres"].arr().orEmpty().mapNotNull { it.obj()?.str("name") },
            runtime = if (tv) d["episode_run_time"].arr()?.firstOrNull()?.toString()?.toIntOrNull() ?: 0 else d.int("runtime"),
            cast = credits?.get("cast").arr().orEmpty().take(6).mapNotNull { it.obj()?.str("name") },
            director = credits?.get("crew").arr().orEmpty().mapNotNull { it.obj() }.firstOrNull { it.str("job") == "Director" }?.str("name"),
            imdbId = d.str("imdb_id") ?: d["external_ids"].obj()?.str("imdb_id")
        )
    }

    private suspend fun omdb(base: Meta?, title: String, year: String?, tv: Boolean): Meta? {
        val q = if (base?.imdbId != null) "i=${base.imdbId}"
        else "t=${enc(title)}&type=${if (tv) "series" else "movie"}" + (year?.let { "&y=$it" } ?: "")
        val o = Http.getJson("https://www.omdbapi.com/?apikey=${enc(omdbKey)}&plot=full&$q").obj() ?: return base
        if (o.str("Response") != "True") return base
        val ratings = o["Ratings"].arr().orEmpty().mapNotNull { it.obj() }
        val rotten = ratings.firstOrNull { it.str("Source") == "Rotten Tomatoes" }?.str("Value")
        val m = base ?: Meta(
            title = o.str("Title") ?: title,
            overview = "",
            poster = o.str("Poster")?.takeIf { it.startsWith("http") },
            year = o.str("Year")?.take(4),
            genres = o.str("Genre")?.split(",")?.map { it.trim() }.orEmpty(),
            runtime = o.str("Runtime")?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
            cast = o.str("Actors")?.split(",")?.map { it.trim() }.orEmpty(),
            director = o.str("Director")?.takeIf { it != "N/A" }
        )
        return m.copy(
            imdbId = m.imdbId ?: o.str("imdbID"),
            imdbRating = o.str("imdbRating")?.takeIf { it != "N/A" },
            rotten = rotten,
            metascore = o.str("Metascore")?.takeIf { it != "N/A" },
            overview = m.overview.ifBlank { o.str("Plot")?.takeIf { it != "N/A" }.orEmpty() }
        )
    }

    /** Nomes, sinopses e imagens dos episódios de uma temporada (TMDB). */
    suspend fun season(tmdbId: Int, season: Int): List<EpisodeMeta> {
        if (tmdbKey.isEmpty() || tmdbId <= 0) return emptyList()
        val key = "$tmdbId/$season"
        seasons.get(key)?.let { return it }
        return runCatching {
            val o = tmdbGet("/tv/$tmdbId/season/$season") ?: return emptyList()
            o["episodes"].arr().orEmpty().mapNotNull { it.obj() }.map { e ->
                EpisodeMeta(
                    episode = e.int("episode_number"),
                    name = e.str("name").orEmpty(),
                    overview = e.str("overview").orEmpty(),
                    still = e.str("still_path")?.let { "https://image.tmdb.org/t/p/w500$it" },
                    runtime = e.int("runtime")
                )
            }.also { seasons.put(key, it) }
        }.getOrDefault(emptyList())
    }

    /** Abertura/créditos pelo TheIntroDB (funciona sem chave; a chave é opcional). */
    suspend fun skipTimes(c: Content): SkipTimes? {
        if (c.tmdbId <= 0 || (c.kind != Kind.EPISODE && c.kind != Kind.MOVIE)) return null
        return runCatching {
            val q = if (c.kind == Kind.EPISODE) "tmdb_id=${c.tmdbId}&season=${c.season}&episode=${c.episode}" else "tmdb_id=${c.tmdbId}"
            val url = "https://api.theintrodb.org/v2/media?$q"
            val o = (runCatching { Http.getJson(url, if (introKey.isNotEmpty()) mapOf("Authorization" to "Bearer $introKey") else emptyMap()) }
                .getOrElse { Http.getJson(url) }).obj() ?: return null
            val intro = o["intro"].arr()?.firstOrNull()?.obj()
            val credits = o["credits"].arr()?.firstOrNull()?.obj()
            val start = intro?.str("start_ms")?.toLongOrNull()
            val end = intro?.str("end_ms")?.toLongOrNull()
            val cs = credits?.str("start_ms")?.toLongOrNull()
            if (start == null || end == null || end <= start) { if (cs != null) SkipTimes(-1, -1, cs) else null }
            else SkipTimes(start, end, cs)
        }.getOrNull()
    }

    companion object {
        private val noise = Regex(
            """(?i)\b(4k|uhd|fhd|full ?hd|hd|sd|1080p|720p|2160p|hdr|dual ?[áa]udio|dublado|legendado|leg|dub|nacional|cam|ts|web-?dl|bluray|multi)\b"""
        )

        /** "Fuga Mortal (2025) 4K [L]" -> ("Fuga Mortal", "2025") */
        fun cleanTitle(raw: String): Pair<String, String?> {
            var t = raw
            val year = Regex("""[\(\[]?\b(19\d{2}|20\d{2})\b[\)\]]?""").findAll(t).lastOrNull()?.groupValues?.get(1)
            t = t.replace(Regex("""^[A-Z]{2,3}\s*[|:-]\s*"""), "")          // "BR | Título"
            t = t.replace(Regex("""\[[^\]]*\]|\([^)]*\)|\{[^}]*\}"""), " ")
            if (year != null) t = t.replace(year, " ")
            t = t.replace(noise, " ")
            t = t.replace(Regex("""\bS\d{1,2}\s*E\d{1,3}\b""", RegexOption.IGNORE_CASE), " ")
            t = t.replace(Regex("""[|_.]+"""), " ").replace(Regex("""\s*-\s*$"""), "")
            t = t.replace(Regex("""\s+"""), " ").trim().trim('-', ':', ' ')
            return (t.ifBlank { raw.trim() }) to year
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        @Suppress("unused") private fun strip(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    }
}
