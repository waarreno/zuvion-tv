package com.zuvion.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.Logo
import com.zuvion.tv.ui.screens.GuideScreen
import com.zuvion.tv.ui.screens.HomeScreen
import com.zuvion.tv.ui.screens.LiveScreen
import com.zuvion.tv.ui.screens.MovieDetailScreen
import com.zuvion.tv.ui.screens.MultiScreen
import com.zuvion.tv.ui.screens.PlayerScreen
import com.zuvion.tv.ui.screens.ProfilesScreen
import com.zuvion.tv.ui.screens.RecordingsScreen
import com.zuvion.tv.ui.screens.SearchScreen
import com.zuvion.tv.ui.screens.SeriesDetailScreen
import com.zuvion.tv.ui.screens.SettingsScreen
import com.zuvion.tv.ui.screens.SetupScreen
import com.zuvion.tv.ui.screens.UpdateDialog
import com.zuvion.tv.ui.screens.VodScreen
import com.zuvion.tv.data.Kind
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.SpaceBackground
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass
import com.zuvion.tv.update.UpdateState

@Composable
fun Root(state: AppState) {
    BackHandler(enabled = state.stack.size > 1 || state.tab != Tab.HOME) { state.pop() }

    // Verificação automática de atualização (no máximo a cada 6h).
    LaunchedEffect(Unit) {
        val s = state.store.settings
        if (System.currentTimeMillis() - s.lastUpdateCheck > 6 * 3600_000L) {
            val info = state.updater.check()
            state.store.updateSettings { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            if (info != null && info.version == s.skippedVersion) state.updater.dismiss()
        }
    }

    SpaceBackground {
        when (val s = state.current) {
            Screen.Setup -> SetupScreen(state)
            Screen.Profiles -> ProfilesScreen(state)
            Screen.Main -> MainShell(state)
            is Screen.MovieDetail -> MovieDetailScreen(state, s.item)
            is Screen.SeriesDetail -> SeriesDetailScreen(state, s.item)
            is Screen.Player -> PlayerScreen(state, s)
            is Screen.Multi -> MultiScreen(state, s)
        }

        val upd by state.updater.state.collectAsState()
        if (upd !is UpdateState.Idle && state.current !is Screen.Player) UpdateDialog(state, upd)

        AnimatedVisibility(
            visible = state.toast != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        ) {
            Text(
                state.toast ?: "",
                color = Z.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.glass(CircleShape, strong = true).padding(horizontal = 22.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun TabContent(state: AppState) {
    when (state.tab) {
        Tab.HOME -> HomeScreen(state)
        Tab.LIVE -> LiveScreen(state)
        Tab.MOVIES -> VodScreen(state, Kind.MOVIE)
        Tab.SERIES -> VodScreen(state, Kind.SERIES)
        Tab.GUIDE -> GuideScreen(state)
        Tab.RECORDINGS -> RecordingsScreen(state)
        Tab.SEARCH -> SearchScreen(state)
        Tab.SETTINGS -> SettingsScreen(state)
    }
}

@Composable
fun MainShell(state: AppState) {
    if (LocalIsTv.current) {
        Row(Modifier.fillMaxSize()) {
            NavRail(state)
            Box(Modifier.fillMaxSize().padding(start = 8.dp)) { TabContent(state) }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) { TabContent(state) }
            BottomBar(state, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun NavRail(state: AppState) {
    Column(
        Modifier
            .padding(start = 20.dp, top = 20.dp, bottom = 20.dp)
            .width(84.dp)
            .fillMaxHeight()
            .glass(RoundedCornerShape(32.dp))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Logo(44.dp)
        Spacer(Modifier.height(14.dp))
        Tab.entries.forEach { t ->
            if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
            val selected = state.tab == t
            Focusable(
                onClick = { state.tab = t },
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                glass = selected,
                selected = selected,
                focusScale = 1.1f
            ) {
                Icon(t.icon, t.label, tint = if (selected) Color.White else Z.Muted, modifier = Modifier.align(Alignment.Center).size(24.dp))
            }
        }
    }
}

@Composable
private fun BottomBar(state: AppState, modifier: Modifier) {
    val tabs = listOf(Tab.HOME, Tab.LIVE, Tab.MOVIES, Tab.SERIES, Tab.GUIDE)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(66.dp)
            .glass(CircleShape, strong = true)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { t ->
            val sel = state.tab == t
            Focusable({ state.tab = t }, Modifier.weight(1f).fillMaxHeight(), CircleShape, glass = false) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon, null, tint = if (sel) Color.White else Z.Muted, modifier = Modifier.size(22.dp))
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else Z.Muted)
                }
            }
        }
    }
}
