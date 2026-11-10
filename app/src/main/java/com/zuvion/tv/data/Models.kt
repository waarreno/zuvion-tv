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
    val kids: Boolean = false
)

@Serializable
enum class Kind { LIVE, MOVIE, SERIES, EPISODE, RECORDING }

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
    val added: Long = 0
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
    val skippedVersion: String? = null
)

@Serializable
data class AppData(
    val playlists: List<Playlist> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val profileData: Map<String, ProfileData> = emptyMap(),
    val settings: Settings = Settings()
)

private val adultWords = listOf("adult", "xxx", "+18", "18+", "porn", "erotic", "erótic", "hot ", "adulto")
fun isAdultName(name: String): Boolean {
    val n = name.lowercase()
    return adultWords.any { n.contains(it) }
}
