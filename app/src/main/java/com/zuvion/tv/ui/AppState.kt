package com.zuvion.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.zuvion.tv.ZuvionApp
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.Kind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Outlined.Home),
    LIVE("Ao vivo", Icons.Outlined.LiveTv),
    MOVIES("Filmes", Icons.Outlined.Movie),
    SERIES("Séries", Icons.Outlined.VideoLibrary),
    GUIDE("Guia", Icons.Outlined.CalendarViewWeek),
    RECORDINGS("Gravações", Icons.Outlined.RadioButtonChecked),
    SEARCH("Buscar", Icons.Outlined.Search),
    SETTINGS("Configurações", Icons.Outlined.Settings)
}

sealed interface Screen {
    data object Main : Screen
    data object Setup : Screen
    data object Profiles : Screen
    data class MovieDetail(val item: Content) : Screen
    data class SeriesDetail(val item: Content) : Screen
    data class Player(
        val item: Content,
        val list: List<Content>,
        val startMs: Long = 0,
        val overrideUrl: String? = null,
        val subtitle: String? = null
    ) : Screen
    data class Multi(val first: Content, val list: List<Content>) : Screen
}

class AppState(val app: ZuvionApp) {
    val store get() = app.store
    val repo get() = app.repo
    val epg get() = app.epg
    val updater get() = app.updater
    val meta get() = app.meta

    val stack = mutableStateListOf<Screen>()
    var tab by mutableStateOf(Tab.HOME)
    var toast by mutableStateOf<String?>(null)
        private set
    /** Categoria liberada pelo PIN nesta sessão. */
    val unlocked = mutableStateListOf<String>()

    init {
        val d = store.data.value
        when {
            d.playlists.isEmpty() -> stack.add(Screen.Setup)
            d.profiles.size > 1 -> stack.add(Screen.Profiles)
            else -> stack.add(Screen.Main)
        }
        if (d.playlists.isNotEmpty()) repo.open()
    }

    val current: Screen get() = stack.lastOrNull() ?: Screen.Main

    fun push(s: Screen) { stack.add(s) }

    fun replaceAll(s: Screen) { stack.clear(); stack.add(s) }

    fun pop(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        if (current == Screen.Main && tab != Tab.HOME) { tab = Tab.HOME; return true }
        return false
    }

    fun showToast(msg: String) {
        toast = msg
        app.scope.launch { delay(3500); if (toast == msg) toast = null }
    }

    fun open(item: Content, list: List<Content> = listOf(item)) {
        when (item.kind) {
            Kind.MOVIE -> push(Screen.MovieDetail(item))
            Kind.SERIES -> push(Screen.SeriesDetail(item))
            else -> play(item, list)
        }
    }

    fun play(item: Content, list: List<Content> = listOf(item), startMs: Long? = null, overrideUrl: String? = null, subtitle: String? = null) {
        val resume = startMs ?: store.progressOf(item)?.takeIf { it.fraction in 0.01f..0.95f }?.positionMs ?: 0
        push(Screen.Player(item, list, resume, overrideUrl, subtitle))
    }

    fun needsPin(categoryId: String, adult: Boolean): Boolean =
        adult && store.settings.parentalPin != null && categoryId !in unlocked
}
