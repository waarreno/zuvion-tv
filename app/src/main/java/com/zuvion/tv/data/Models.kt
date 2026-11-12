package com.zuvion.tv.data

import kotlinx.serialization.Serializable

@Serializable
enum class SourceType { XTREAM, M3U_URL, M3U_FILE, STALKER }

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val mac: String = "",
    val epgUrl: String = ""
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val color: Long,
    val kids: Boolean = false,
    /** Emoji do avatar; vazio = inicial do nome. */
    val avatar: String = ""
)

@Serializable
enum class Kind { LIVE, MOVIE, SERIES, EPISODE, RECORDING }

@Serializable
data class Category(val id: String, val name: String, val kind: Kind) {
    val adult: Boolean get() = isAdultName(name)
}

/** Item unificado: canal, filme, série, episódio ou gravação. */
@Serializable
data class Content(
    val id: String,
    val kind: Kind,
    val name: String,
    val playlistId: String = "",
    val poster: String? = null,
    val categoryId: String = "",
    val streamUrl: String? = null,
    val epgId: String? = null,
    val number: Int = 0,
    val catchupDays: Int = 0,
    val extension: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val plot: String? = null,
    val seriesId: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val added: Long = 0,
    /** Altura real do vídeo (quando o servidor informa), para mostrar SD/HD/Full HD/4K. */
    val videoHeight: Int = 0,
    /** Id no TMDB (preenchido pelos metadados; usado no TheIntroDB). */
    val tmdbId: Int = 0
) {
    val key: String get() = "$playlistId|$kind|$id"
}

data class SeriesInfo(
    val plot: String?,
    val poster: String?,
    val seasons: Map<Int, List<Content>>
)

data class EpgProgram(
    val channel: String,
    val start: Long,
    val end: Long,
    val title: String,
    val desc: String = ""
) {
    fun isNow(now: Long = System.currentTimeMillis()) = now in start until end
    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

@Serializable
data class HistoryEntry(
    val item: Content,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

@Serializable
data class ProfileData(
    val favorites: List<Content> = emptyList(),
    val history: List<HistoryEntry> = emptyList()
)

@Serializable
data class Settings(
    val activePlaylistId: String? = null,
    val activeProfileId: String? = null,
    val engine: String = "exo",          // exo | vlc
    val liveFormat: String = "ts",       // ts | m3u8
    val autoFallbackVlc: Boolean = true,
    val parentalPin: String? = null,
    val hideAdult: Boolean = false,
    val lastUpdateCheck: Long = 0,
    val skippedVersion: String? = null,
    val theme: String = "system",        // system | dark | light
    val refreshHours: Int = 24,          // 0 = só manual
    val libraryUpdated: Map<String, Long> = emptyMap(),
    val tmdbKey: String = "",
    val omdbKey: String = "",
    val introDbKey: String = "",
    val autoSkipIntro: Boolean = false,
    /** Último formato escolhido no player ("Full HD", "4K · H.265"…). */
    val preferredFormat: String? = null,
    /** Guia complementar (XMLTV) somado ao do provedor. */
    val extraEpgUrl: String = "",
    /** O usuário já criou/confirmou o primeiro perfil. */
    val profileSetupDone: Boolean = false,
    /** Realce de imagem: off | soft | strong. */
    val enhance: String = "off",
    /** Continuar assistindo: entra a partir de X% e sai (concluído) a partir de Y%. */
    val continueMinPct: Int = 5,
    val continueMaxPct: Int = 90
)

fun Settings.enhanceStrength(): Float = when (enhance) { "soft" -> 0.35f; "strong" -> 1f; else -> 0f }

/** Guia aberto do Brasil (XMLTV gratuito, ~260 canais). */
const val OPEN_EPG_BR = "https://epgshare01.online/epgshare01/epg_ripper_BR1.xml.gz"

@Serializable
data class AppData(
    val playlists: List<Playlist> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val profileData: Map<String, ProfileData> = emptyMap(),
    /** Resolução medida ao tocar (chave do conteúdo -> altura), para mostrar a qualidade real. */
    val videoHeights: Map<String, Int> = emptyMap(),
    val settings: Settings = Settings()
)

private val adultWords = listOf("adult", "xxx", "+18", "18+", "porn", "erotic", "erótic", "hot ", "adulto")
fun isAdultName(name: String): Boolean {
    val n = name.lowercase()
    return adultWords.any { n.contains(it) }
}
