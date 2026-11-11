package com.zuvion.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.zuvion.tv.ui.screens.LoadProgressScreen
import com.zuvion.tv.ui.screens.SplashScreen
import kotlinx.coroutines.delay
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
    val data by state.store.data.collectAsState()
    val sysDark = isSystemInDarkTheme()
    val wantDark = when (data.settings.theme) { "dark" -> true; "light" -> false; else -> sysDark }
    // O player fica sempre escuro (controles sobre o vídeo).
    Z.dark = wantDark || state.current is Screen.Player || state.current is Screen.Multi
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !Z.dark
            c.isAppearanceLightNavigationBars = !Z.dark
        }
    }

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

    var splash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2100); splash = false }

    SpaceBackground {
        // Pilha de telas: as de baixo (Início/abas e Detalhes) continuam vivas, invisíveis e sem foco.
        // Ao voltar, a tela reaparece exatamente como estava: aba, categoria, rolagem e o item focado.
        val stack = state.stack.toList()
        stack.forEachIndexed { i, sc ->
            val top = i == stack.lastIndex
            val keepAlive = sc == Screen.Main || sc is Screen.MovieDetail || sc is Screen.SeriesDetail
            if (top || keepAlive) key(i, System.identityHashCode(sc)) {
                ScreenLayer(covered = !top) { ScreenContent(state, sc) }
            }
        }

        // Progresso do carregamento da lista (TV ao vivo, Filmes, Séries, Guia).
        val lib by state.repo.library.collectAsState()
        var showProgress by remember { mutableStateOf(false) }
        LaunchedEffect(lib.refreshing) {
            if (lib.refreshing) showProgress = true
            else if (showProgress) { delay(1300); showProgress = false }
        }
        val onMain = state.current == Screen.Main || state.current == Screen.Profiles
        AnimatedVisibility(showProgress && onMain && lib.steps.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            LoadProgressScreen(state, lib.steps)
        }

        // Primeiro uso: o usuário cria o próprio perfil (nome, avatar, cor) em vez de um "Principal" automático.
        if (!splash && state.current == Screen.Main && !data.settings.profileSetupDone && data.playlists.isNotEmpty()) {
            state.store.activeProfile?.let { p ->
                com.zuvion.tv.ui.screens.ProfileEditor(
                    state, p.copy(name = if (p.name == "Principal") "" else p.name), canDelete = false, title = "Crie seu perfil",
                    onSaved = { state.store.updateSettings { it.copy(profileSetupDone = true) } }
                ) { }
            }
        }

        val upd by state.updater.state.collectAsState()
        if (upd !is UpdateState.Idle && state.current !is Screen.Player && !splash) UpdateDialog(state, upd)

        AnimatedVisibility(splash, enter = fadeIn(), exit = fadeOut(tween(450))) { SplashScreen() }

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
private fun ScreenContent(state: AppState, s: Screen) {
    when (s) {
        Screen.Setup -> SetupScreen(state)
        Screen.Profiles -> ProfilesScreen(state)
        Screen.About -> com.zuvion.tv.ui.screens.AboutScreen(state)
        Screen.Main -> MainShell(state)
        is Screen.MovieDetail -> MovieDetailScreen(state, s.item)
        is Screen.SeriesDetail -> SeriesDetailScreen(state, s.item)
        is Screen.Player -> PlayerScreen(state, s)
        is Screen.Multi -> MultiScreen(state, s)
    }
}

/**
 * Uma tela da pilha. Coberta: invisível, não recebe toque nem foco do controle.
 * Ao voltar ao topo, devolve o foco ao último item que estava selecionado nela.
 */
@Composable
private fun ScreenLayer(covered: Boolean, content: @Composable () -> Unit) {
    val fr = remember { FocusRequester() }
    val layerFocus = remember { com.zuvion.tv.ui.theme.LayerFocus() }
    layerFocus.covered = covered
    val tv = LocalIsTv.current
    var wasCovered by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(covered) {
        if (covered) { wasCovered = true; return@LaunchedEffect }
        if (wasCovered) {
            // Voltou ao topo: devolve o foco ao último item selecionado nesta tela.
            wasCovered = false
            delay(60)
            val r = layerFocus.last?.let { runCatching { it.requestFocus() } }
            if (r == null || r.isFailure) runCatching { fr.requestFocus() }
            delay(80)
            if (!hasFocus) runCatching { fr.requestFocus() }
        } else if (tv) {
            // Tela nova sem foco próprio: na TV, seleciona o primeiro item (senão as setas não respondem).
            delay(250)
            if (!hasFocus) runCatching { fr.requestFocus() }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (covered) 0f else 1f }
            .then(if (covered) Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            } else Modifier)
            .onFocusChanged { hasFocus = it.hasFocus }
    ) {
        Box(Modifier.fillMaxSize().focusRequester(fr).focusGroup()) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.zuvion.tv.ui.theme.LocalCovered provides covered,
                com.zuvion.tv.ui.theme.LocalLayerFocus provides layerFocus
            ) { content() }
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
        // A barra fica abaixo do conteúdo (não sobrepõe cartazes nem listas).
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) { TabContent(state) }
            BottomBar(state, Modifier)
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
        // Configurações ficam na engrenagem do topo da tela inicial (sem item extra no menu).
        Tab.entries.filter { it != Tab.SETTINGS }.forEach { t ->
            val selected = state.tab == t
            Focusable(
                onClick = { state.tab = t },
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                glass = selected,
                selected = selected,
                focusScale = 1.1f
            ) {
                Icon(t.icon, t.label, tint = if (selected) Z.Text else Z.Muted, modifier = Modifier.align(Alignment.Center).size(24.dp))
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
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .glass(CircleShape, strong = true)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { t ->
            val sel = state.tab == t
            Focusable({ state.tab = t }, Modifier.weight(1f).fillMaxHeight(), CircleShape, glass = false) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon, null, tint = if (sel) Z.Violet else Z.Muted, modifier = Modifier.size(22.dp))
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (sel) Z.Text else Z.Muted)
                }
            }
        }
    }
}
