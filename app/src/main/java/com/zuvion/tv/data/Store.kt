package com.zuvion.tv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Persistência simples em JSON (leve, sem banco de dados). */
class Store(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "zuvion.json")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data
    private var saveJob: Job? = null

    private fun load(): AppData = runCatching {
        if (file.exists()) AppJson.decodeFromString(AppData.serializer(), file.readText()) else AppData()
    }.getOrDefault(AppData())

    fun update(block: (AppData) -> AppData) {
        _data.value = block(_data.value)
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(400)
            val tmp = File(file.parentFile, "zuvion.json.tmp")
            tmp.writeText(AppJson.encodeToString(AppData.serializer(), _data.value))
            tmp.renameTo(file)
        }
    }

    val settings: Settings get() = _data.value.settings
    fun updateSettings(block: (Settings) -> Settings) = update { it.copy(settings = block(it.settings)) }

    val activePlaylist: Playlist?
        get() = _data.value.let { d -> d.playlists.firstOrNull { it.id == d.settings.activePlaylistId } ?: d.playlists.firstOrNull() }

    val activeProfile: Profile?
        get() = _data.value.let { d -> d.profiles.firstOrNull { it.id == d.settings.activeProfileId } ?: d.profiles.firstOrNull() }

    fun ensureDefaultProfile() {
        if (_data.value.profiles.isEmpty()) {
            val p = Profile(UUID.randomUUID().toString(), "Principal", 0xFFFF8FA8)
            update { it.copy(profiles = listOf(p), settings = it.settings.copy(activeProfileId = p.id)) }
        }
    }

    private fun profileId() = activeProfile?.id ?: "default"

    fun profileData(): ProfileData = _data.value.profileData[profileId()] ?: ProfileData()

    private fun updateProfileData(block: (ProfileData) -> ProfileData) {
        val id = profileId()
        update { d -> d.copy(profileData = d.profileData + (id to block(d.profileData[id] ?: ProfileData()))) }
    }

    fun isFavorite(c: Content) = profileData().favorites.any { it.key == c.key }

    fun toggleFavorite(c: Content) = updateProfileData { pd ->
        if (pd.favorites.any { it.key == c.key }) pd.copy(favorites = pd.favorites.filterNot { it.key == c.key })
        else pd.copy(favorites = listOf(c) + pd.favorites)
    }

    fun saveProgress(c: Content, pos: Long, dur: Long) {
        if (c.kind == Kind.LIVE) return
        updateProfileData { pd ->
            val rest = pd.history.filterNot { it.item.key == c.key }
            val done = dur > 0 && pos > dur * 0.95
            val list = if (done) rest else listOf(HistoryEntry(c, pos, dur, System.currentTimeMillis())) + rest
            pd.copy(history = list.take(60))
        }
    }

    fun recentLive(c: Content) = updateProfileData { pd ->
        val e = HistoryEntry(c, 0, 0, System.currentTimeMillis())
        pd.copy(history = (listOf(e) + pd.history.filterNot { it.item.key == c.key }).take(60))
    }

    /** Tira do "Continuar assistindo" e zera o progresso. */
    fun removeProgress(c: Content) = updateProfileData { pd -> pd.copy(history = pd.history.filterNot { it.item.key == c.key }) }

    fun progressOf(c: Content): HistoryEntry? = profileData().history.firstOrNull { it.item.key == c.key }

    fun addPlaylist(p: Playlist) = update {
        it.copy(playlists = it.playlists.filterNot { x -> x.id == p.id } + p, settings = it.settings.copy(activePlaylistId = p.id))
    }

    fun removePlaylist(id: String) = update { d ->
        val rest = d.playlists.filterNot { it.id == id }
        d.copy(playlists = rest, settings = d.settings.copy(activePlaylistId = rest.firstOrNull()?.id))
    }

    fun saveProfile(p: Profile) = update { d ->
        d.copy(profiles = if (d.profiles.any { it.id == p.id }) d.profiles.map { if (it.id == p.id) p else it } else d.profiles + p)
    }

    fun removeProfile(id: String) = update { d ->
        val rest = d.profiles.filterNot { it.id == id }
        d.copy(profiles = rest, profileData = d.profileData - id, settings = d.settings.copy(activeProfileId = rest.firstOrNull()?.id))
    }
}
