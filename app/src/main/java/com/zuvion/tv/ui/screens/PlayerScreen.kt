package com.zuvion.tv.ui.screens

import android.app.Activity
import android.view.KeyEvent as AKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import com.zuvion.tv.data.EpgProgram
import com.zuvion.tv.data.Variants
import com.zuvion.tv.data.title
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.AutoAwesome
import com.zuvion.tv.meta.SkipTimes
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.Kind
import com.zuvion.tv.player.AspectMode
import com.zuvion.tv.player.ExoEngine
import com.zuvion.tv.player.Track
import com.zuvion.tv.player.VideoEngine
import com.zuvion.tv.player.VlcEngine
import com.zuvion.tv.recording.RecordingService
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.Screen
import com.zuvion.tv.ui.components.Badge
import com.zuvion.tv.ui.components.ChannelLogo
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.IconGlassButton
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.ProgressLine
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** Mantém a tela ligada e esconde as barras do sistema durante a reprodução. */
@Composable
fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        view.keepScreenOn = true
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            ctl?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun PlayerScreen(state: AppState, s: Screen.Player) {
    val ctx = LocalContext.current
    val tv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    val list = remember(s) { s.list.ifEmpty { listOf(s.item) } }
    var index by remember(s) { mutableIntStateOf(list.indexOfFirst { it.key == s.item.key }.coerceAtLeast(0)) }
    val baseItem = list.getOrElse(index) { s.item }
    // Formato escolhido no player (SD/HD/Full HD/4K…) para este canal/filme.
    var variantPick by remember(s, index) { mutableStateOf<Content?>(null) }
    val media = variantPick ?: baseItem
    val variants = remember(baseItem.key) { state.repo.variantsOf(baseItem) }
    var showFormats by remember { mutableStateOf(false) }
    var engineName by remember { mutableStateOf(state.store.settings.engine) }
    var triedFallback by remember { mutableStateOf(false) }
    var overrideUrl by remember(s) { mutableStateOf(s.overrideUrl) }
    var subtitle by remember(s) { mutableStateOf(s.subtitle) }
    var startAt by remember(s) { mutableLongStateOf(s.startMs) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var aspect by remember { mutableStateOf(AspectMode.FIT) }
    val isLive = media.kind == Kind.LIVE && overrideUrl == null

    // Realce de imagem (só no ExoPlayer). Trocar o nível recria o motor no mesmo ponto do vídeo.
    var enhance by remember { mutableStateOf(state.store.settings.enhance) }
    var enhanceFailed by remember { mutableStateOf(false) }
    val enhanceStrength = if (enhanceFailed) 0f else when (enhance) { "soft" -> 0.35f; "strong" -> 1f; else -> 0f }
    val engine: VideoEngine = remember(engineName, enhanceStrength) {
        if (engineName == "vlc") VlcEngine(ctx) else ExoEngine(ctx, enhanceStrength)
    }
    val es by engine.state.collectAsState()
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }

    fun saveProgress() {
        if (!isLive && overrideUrl == null && engine.duration > 0) state.store.saveProgress(media, engine.position, engine.duration)
    }

    DisposableEffect(engine) {
        onDispose { saveProgress(); engine.release() }
    }

    LaunchedEffect(engine, media.key, overrideUrl) {
        val url = try {
            overrideUrl ?: (if (media.kind == Kind.RECORDING) media.streamUrl else state.repo.source?.resolveUrl(media)) ?: media.streamUrl
        } catch (e: Throwable) { null }
        if (url == null) { state.showToast("Não foi possível abrir este conteúdo"); return@LaunchedEffect }
        currentUrl = url
        engine.setAspect(aspect)
        engine.play(url, startAt)
        startAt = 0
        if (media.kind == Kind.LIVE && overrideUrl == null) state.store.recentLive(media)
    }

    // Reserva automática: se o ExoPlayer falhar, tenta o VLC.
    LaunchedEffect(es.error) {
        // Se o aparelho não aguentar o realce, tenta de novo sem ele antes de trocar de motor.
        if (es.error != null && engineName == "exo" && enhanceStrength > 0f) {
            enhanceFailed = true
            startAt = engine.position
            state.showToast("Realce de imagem indisponível neste vídeo")
            return@LaunchedEffect
        }
        if (es.error != null && engineName == "exo" && state.store.settings.autoFallbackVlc && !triedFallback) {
            triedFallback = true
            startAt = engine.position
            state.showToast("Trocando para o VLC…")
            engineName = "vlc"
        }
    }

    // Próximo episódio automaticamente
    LaunchedEffect(es.ended) {
        if (es.ended && media.kind == Kind.EPISODE && index < list.lastIndex) {
            state.store.saveProgress(media, engine.duration, engine.duration)
            index++
        }
    }

    LaunchedEffect(engine) {
        var tick = 0
        while (true) {
            pos = engine.position; dur = engine.duration
            if (++tick % 20 == 0) saveProgress()
            delay(500)
        }
    }

    ImmersiveMode()

    var overlay by remember { mutableStateOf(true) }
    var bump by remember { mutableIntStateOf(0) }
    var showChannels by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val rootFr = remember { FocusRequester() }
    val swallowOk = remember { booleanArrayOf(false) }
    val swallowBack = remember { booleanArrayOf(false) }
    val playFr = remember { FocusRequester() }
    val panelOpen = showChannels || showTracks || showInfo

    // Pular abertura / créditos (TheIntroDB)
    val skip by produceState<SkipTimes?>(null, media.key) { value = state.meta.skipTimes(media) }
    val sk = skip
    val inIntro = sk != null && sk.introStart >= 0 && pos in sk.introStart until sk.introEnd - 1000
    val creditsAt = sk?.creditsStart
    val inCredits = creditsAt != null && media.kind == Kind.EPISODE && index < list.lastIndex && pos >= creditsAt && (dur == 0L || pos < dur - 3000)
    var autoSkipped by remember(media.key) { mutableStateOf(false) }
    LaunchedEffect(inIntro) {
        if (inIntro && sk != null && !autoSkipped && state.store.settings.autoSkipIntro) {
            autoSkipped = true; engine.seekTo(sk.introEnd); state.showToast("Abertura pulada")
        }
    }
    fun doSkip() {
        if (inIntro && sk != null) engine.seekTo(sk.introEnd)
        else if (inCredits) { saveProgress(); state.store.saveProgress(media, dur, dur); index++ }
    }

    LaunchedEffect(overlay, bump, panelOpen) {
        if (overlay && !panelOpen) { delay(6000); overlay = false }
    }
    LaunchedEffect(overlay) {
        runCatching { if (overlay) playFr.requestFocus() else rootFr.requestFocus() }
    }

    fun zap(delta: Int) {
        if (list.size < 2) return
        saveProgress()
        overrideUrl = null; subtitle = null
        index = (index + delta + list.size) % list.size
        overlay = true; bump++
    }

    val (nowProg, nextProg) = if (media.kind == Kind.LIVE) state.epg.nowNext(media) else (null to null)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                val isOk = ev.key.nativeKeyCode.let { it == AKeyEvent.KEYCODE_DPAD_CENTER || it == AKeyEvent.KEYCODE_ENTER || it == AKeyEvent.KEYCODE_NUMPAD_ENTER }
                // O OK que só abriu os controles não pode "clicar" no botão de pausa ao ser solto.
                if (ev.type == KeyEventType.KeyUp) {
                    if (isOk && swallowOk[0]) { swallowOk[0] = false; return@onPreviewKeyEvent true }
                    // O "voltar" que só escondeu os controles não pode também fechar o player ao ser solto.
                    if (ev.key.nativeKeyCode == AKeyEvent.KEYCODE_BACK && swallowBack[0]) { swallowBack[0] = false; return@onPreviewKeyEvent true }
                    return@onPreviewKeyEvent false
                }
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                bump++
                val code = ev.key.nativeKeyCode
                when (code) {
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_MEDIA_PLAY, AKeyEvent.KEYCODE_MEDIA_PAUSE -> { engine.togglePause(); true }
                    AKeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); true }
                    AKeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                    AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { engine.seekBy(30_000); true }
                    AKeyEvent.KEYCODE_MEDIA_REWIND -> { engine.seekBy(-30_000); true }
                    AKeyEvent.KEYCODE_BACK -> if (overlay && !panelOpen) { overlay = false; swallowBack[0] = true; true } else false
                    else -> if (!overlay && !panelOpen && (inIntro || inCredits) &&
                        (code == AKeyEvent.KEYCODE_DPAD_CENTER || code == AKeyEvent.KEYCODE_ENTER)) { swallowOk[0] = true; doSkip(); true }
                    else if (!overlay && !panelOpen) {
                        when (code) {
                            AKeyEvent.KEYCODE_DPAD_UP -> { if (isLive) zap(-1) else overlay = true; true }
                            AKeyEvent.KEYCODE_DPAD_DOWN -> { if (isLive) zap(1) else overlay = true; true }
                            AKeyEvent.KEYCODE_DPAD_LEFT -> { if (isLive) showChannels = true else engine.seekBy(-10_000); true }
                            AKeyEvent.KEYCODE_DPAD_RIGHT -> { if (!isLive) engine.seekBy(10_000) else overlay = true; true }
                            AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER, AKeyEvent.KEYCODE_MENU -> { swallowOk[0] = isOk; overlay = true; true }
                            else -> false
                        }
                    } else false
                }
            }
            .focusRequester(rootFr)
            .focusable()
            .pointerInput(isLive) {
                detectTapGestures(
                    onTap = { overlay = !overlay; bump++ },
                    onDoubleTap = { o -> if (!isLive) engine.seekBy(if (o.x > size.width / 2) 10_000 else -10_000) }
                )
            }
    ) {
        engine.Surface(Modifier.fillMaxSize())

        if (es.buffering && es.error == null) {
            CircularProgressIndicator(color = Z.Violet, modifier = Modifier.align(Alignment.Center).size(48.dp))
        }

        if (es.error != null && (engineName == "vlc" || triedFallback || !state.store.settings.autoFallbackVlc)) {
            Column(
                Modifier.align(Alignment.Center).glass(RoundedCornerShape(28.dp), strong = true).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Não foi possível reproduzir", style = MaterialTheme.typography.titleLarge, color = Z.Text)
                Text(es.error ?: "", style = MaterialTheme.typography.bodyMedium, color = Z.Muted, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Tentar de novo", { currentUrl?.let { engine.play(it, if (isLive) 0 else engine.position) } }, primary = true)
                    PillButton(if (engineName == "vlc") "Usar ExoPlayer" else "Usar VLC", { triedFallback = true; engineName = if (engineName == "vlc") "exo" else "vlc" })
                }
            }
        }

        // Pular abertura / próximo episódio (aparece mesmo com os controles escondidos)
        AnimatedVisibility(
            (inIntro || inCredits) && !showChannels, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 32.dp, bottom = if (overlay) 230.dp else 40.dp)
        ) {
            PillButton(if (inIntro) "Pular abertura" else "Próximo episódio", { doSkip() },
                icon = Icons.Outlined.SkipNext, primary = true)
        }

        AnimatedVisibility(overlay && !showChannels, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.6f))))) {
                // Topo
                Row(
                    Modifier.fillMaxWidth().safeDrawingPadding().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!tv) IconGlassButton(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", { state.pop() }, size = 44.dp)
                    Spacer(Modifier.weight(1f))
                    if (isLive) Badge("AO VIVO", Z.Live) else if (overrideUrl != null) Badge("REPLAY", Z.Cyan)
                    es.videoSize?.let { Badge(it) }
                }

                // Barra inferior de vidro
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding()
                        .padding(if (tv) 28.dp else 12.dp)
                        .glass(RoundedCornerShape(32.dp), strong = true)
                        .padding(horizontal = if (tv) 26.dp else 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (media.kind == Kind.LIVE) ChannelLogo(media, 52.dp)
                        Column(Modifier.weight(1f)) {
                            val top = when (media.kind) {
                                Kind.LIVE -> media.title
                                Kind.EPISODE -> "Temporada ${media.season} · Episódio ${media.episode}"
                                else -> if (media.kind == Kind.MOVIE) "Filme" else ""
                            }
                            Text(top, style = MaterialTheme.typography.labelMedium, color = Z.Muted, maxLines = 1)
                            val title = subtitle ?: if (media.kind == Kind.LIVE) nowProg?.title ?: media.title else media.title
                            Text(title, style = MaterialTheme.typography.headlineMedium, color = Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isLive && nextProg != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("A seguir", style = MaterialTheme.typography.labelMedium, color = Z.Muted)
                                Text("${nextProg.title} · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextProg.start))}",
                                    style = MaterialTheme.typography.labelLarge, color = Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(260.dp))
                            }
                        }
                    }
                    // Progresso
                    if (isLive) {
                        if (nowProg != null) {
                            val f = SimpleDateFormat("HH:mm", Locale.getDefault())
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(f.format(Date(nowProg.start)), color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                                ProgressLine(nowProg.progress(), Modifier.weight(1f), height = 6.dp)
                                Text(f.format(Date(nowProg.end)), color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else if (dur > 0) {
                        SeekBar(pos, dur, onSeek = { engine.seekTo(it); pos = it }, onActivity = { bump++ })
                    }
                    // Controles
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val np = nowProg
                        if (isLive && media.catchupDays > 0 && np != null) item {
                            IconGlassButton(Icons.Outlined.History, "Assistir do início", {
                                val u = state.repo.source?.catchupUrl(media, np.start, np.end)
                                if (u != null) { overrideUrl = u; subtitle = np.title } else state.showToast("Replay indisponível")
                            })
                        }
                        if (isLive && list.size > 1) item { IconGlassButton(Icons.Outlined.FormatListBulleted, "Canais", { showChannels = true }) }
                        item { IconGlassButton(Icons.Outlined.Info, "Sinopse", { showInfo = true }) }
                        if (!isLive) item { IconGlassButton(Icons.Outlined.Replay10, "Voltar 10 segundos", { engine.seekBy(-10_000) }) }
                        item {
                            Focusable({ engine.togglePause() }, Modifier.size(64.dp).focusRequester(playFr), CircleShape, focusScale = 1.1f, glass = false) {
                                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.Icon(if (es.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (es.playing) "Pausar" else "Reproduzir", tint = Z.Ink, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        if (!isLive) item { IconGlassButton(Icons.Outlined.Forward10, "Avançar 10 segundos", { engine.seekBy(10_000) }) }
                        if (media.kind == Kind.EPISODE && index < list.lastIndex) item { IconGlassButton(Icons.Outlined.SkipNext, "Próximo episódio", { saveProgress(); index++ }) }
                        if (isLive) item {
                            val recording by RecordingService.active.collectAsState()
                            IconGlassButton(Icons.Filled.FiberManualRecord, if (recording.isNotEmpty()) "Parar gravação" else "Gravar", {
                                if (recording.isNotEmpty()) { RecordingService.stopAll(ctx); state.showToast("Gravação encerrada") }
                                else currentUrl?.let { u ->
                                    val minutes = nowProg?.let { ((it.end - System.currentTimeMillis()) / 60000).toInt() + 2 }?.coerceIn(5, 240) ?: 120
                                    RecordingService.start(ctx, u, nowProg?.title?.let { "${media.name} - $it" } ?: media.name, minutes)
                                    state.showToast("Gravando por $minutes min")
                                }
                            }, tint = Z.Live, active = recording.isNotEmpty())
                        }
                        if (variants.size > 1) item { IconGlassButton(Icons.Outlined.HighQuality, "Formato: ${Variants.label(media.name)}", { showFormats = true }) }
                        item { IconGlassButton(Icons.Outlined.Subtitles, "Áudio e legendas", { showTracks = true }) }
                        item {
                            val on = enhance != "off" && !enhanceFailed && engineName != "vlc"
                            IconGlassButton(Icons.Outlined.AutoAwesome, "Realce de imagem", {
                                if (engineName == "vlc") { state.showToast("Realce de imagem indisponível neste vídeo"); return@IconGlassButton }
                                val next = when (enhance) { "off" -> "soft"; "soft" -> "strong"; else -> "off" }
                                startAt = if (isLive) 0 else engine.position
                                enhanceFailed = false
                                enhance = next
                                state.store.updateSettings { it.copy(enhance = next) }
                                state.showToast("Realce de imagem: " + when (next) { "soft" -> "suave"; "strong" -> "forte"; else -> "desligado" })
                            }, tint = if (on) Z.Cyan else Z.Text, active = on)
                        }
                        item {
                            IconGlassButton(Icons.Outlined.AspectRatio, "Proporção: ${aspect.label}", {
                                aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size]
                                engine.setAspect(aspect); state.showToast("Proporção: ${aspect.label}")
                            })
                        }
                        if (isLive && list.size > 1) item { IconGlassButton(Icons.Outlined.GridView, "Multi-tela", { state.push(Screen.Multi(media, list)) }) }
                        item {
                            val data by state.store.data.collectAsState()
                            val fav = remember(data, media) { state.store.isFavorite(media) }
                            IconGlassButton(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favorito", { state.store.toggleFavorite(media) }, tint = if (fav) Z.Live else Z.Text)
                        }
                        item {
                            IconGlassButton(Icons.Outlined.SwapHoriz, "Trocar motor de vídeo", {
                                startAt = if (isLive) 0 else engine.position
                                triedFallback = true
                                engineName = if (engineName == "vlc") "exo" else "vlc"
                            })
                        }
                    }
                }
            }
        }

        // Painel lateral de canais
        AnimatedVisibility(showChannels, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxHeight()) {
            val ls = rememberLazyListState(initialFirstVisibleItemIndex = (index - 3).coerceAtLeast(0))
            val firstFr = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(100); runCatching { firstFr.requestFocus() } }
            LazyColumn(
                Modifier.width(340.dp).fillMaxHeight().safeDrawingPadding().padding(16.dp).glass(RoundedCornerShape(28.dp), strong = true)
                    .background(Color(0xCC0B0E22)),
                state = ls, contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(list) { i, c ->
                    val now = state.epg.nowNext(c).first
                    Focusable(
                        { overrideUrl = null; subtitle = null; index = i; showChannels = false; overlay = true; bump++ },
                        Modifier.fillMaxWidth().then(if (i == index) Modifier.focusRequester(firstFr) else Modifier),
                        RoundedCornerShape(16.dp), glass = i == index, selected = i == index, focusScale = 1.02f
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChannelLogo(c, 36.dp)
                            Column {
                                Text(c.title, style = MaterialTheme.typography.labelLarge, color = Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (now != null) Text(now.title, style = MaterialTheme.typography.labelMedium, color = Z.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            androidx.activity.compose.BackHandler { showChannels = false; runCatching { rootFr.requestFocus() } }
        }
    }

    if (showTracks) TracksDialog(engine) { showTracks = false }
    if (showFormats) {
        GlassDialog({ showFormats = false }) {
            Text("Formato", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
            Text("A escolha vale para os próximos canais e filmes também.", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
            val fr = remember { FocusRequester() }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                items(variants) { v ->
                    val sel = v.key == media.key
                    com.zuvion.tv.ui.components.Chip(Variants.label(v.name), sel, {
                        if (!sel) {
                            saveProgress()
                            startAt = if (media.kind == Kind.LIVE) 0 else engine.position
                            variantPick = v
                            triedFallback = false
                            state.store.updateSettings { it.copy(preferredFormat = Variants.label(v.name)) }
                        }
                        showFormats = false
                    }, if (sel) Modifier.focusRequester(fr) else Modifier)
                }
            }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { fr.requestFocus() } }
        }
    }
    if (showInfo) SynopsisDialog(state, media, if (isLive) nowProg else null, if (isLive) nextProg else null, subtitle) { showInfo = false }
}

/** Sinopse do programa em exibição (ao vivo) ou do filme/episódio. */
@Composable
private fun SynopsisDialog(state: AppState, media: Content, now: EpgProgram?, next: EpgProgram?, replayTitle: String?, onDismiss: () -> Unit) {
    val f = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val meta by produceState(state.meta.cached(media), media.key) {
        if (media.kind == Kind.MOVIE) value = state.meta.forContent(media)
    }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    GlassDialog(onDismiss) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (media.kind == Kind.LIVE) {
                Text(media.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
                if (now == null && replayTitle == null) {
                    Text(media.title, style = MaterialTheme.typography.headlineMedium, color = Z.Text)
                    Text("Este canal não tem guia de programação.", color = Z.Muted)
                } else {
                    Text(replayTitle ?: now?.title.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = Z.Text)
                    if (now != null && replayTitle == null) Text(f.format(Date(now.start)) + " – " + f.format(Date(now.end)), color = Z.Muted, style = MaterialTheme.typography.labelLarge)
                    Text(now?.desc?.ifBlank { null } ?: "Sem sinopse para este programa.", color = Z.Text, style = MaterialTheme.typography.bodyLarge)
                    if (next != null) {
                        Text("A SEGUIR · " + f.format(Date(next.start)), style = MaterialTheme.typography.labelSmall, color = Z.Cyan, modifier = Modifier.padding(top = 8.dp))
                        Text(next.title, style = MaterialTheme.typography.titleMedium, color = Z.Text)
                        if (next.desc.isNotBlank()) Text(next.desc, color = Z.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                    }
                }
            } else {
                val m = meta
                Text(if (media.kind == Kind.EPISODE) "TEMPORADA ${media.season} · EPISÓDIO ${media.episode}" else "FILME", style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
                Text(m?.title?.takeIf { media.kind == Kind.MOVIE } ?: media.title, style = MaterialTheme.typography.headlineMedium, color = Z.Text)
                val info = listOfNotNull(m?.year ?: media.year, (m?.rating ?: media.rating)?.let { "★ $it" }, m?.imdbRating?.let { "IMDb $it" },
                    m?.runtime?.takeIf { it > 0 }?.let { "$it min" }).joinToString("  ·  ")
                if (info.isNotBlank()) Text(info, color = Z.Muted, style = MaterialTheme.typography.labelLarge)
                val plot = m?.overview?.ifBlank { null } ?: media.plot?.ifBlank { null }
                Text(plot ?: "Sinopse não disponível.", color = Z.Text, style = MaterialTheme.typography.bodyLarge)
                val cast = m?.cast.orEmpty()
                if (cast.isNotEmpty()) Text("Elenco: " + cast.joinToString(), color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PillButton("Fechar", onDismiss, Modifier.fillMaxWidth().focusRequester(fr), primary = true)
    }
}

@Composable
private fun TracksDialog(engine: VideoEngine, onDismiss: () -> Unit) {
    var audio by remember { mutableStateOf(engine.audioTracks()) }
    var subs by remember { mutableStateOf(engine.subtitleTracks()) }
    GlassDialog(onDismiss) {
        Text("Áudio", style = MaterialTheme.typography.titleLarge, color = Z.Text)
        if (audio.isEmpty()) Text("Faixa única", color = Z.Muted)
        TrackList(audio) { engine.selectAudio(it.id); audio = engine.audioTracks() }
        Text("Legendas", style = MaterialTheme.typography.titleLarge, color = Z.Text)
        TrackList(listOf(Track("off", "Desativadas", subs.none { it.selected })) + subs) {
            engine.selectSubtitle(if (it.id == "off") null else it.id); subs = engine.subtitleTracks()
        }
        PillButton("Fechar", onDismiss, Modifier.fillMaxWidth(), primary = true)
    }
}

@Composable
private fun TrackList(tracks: List<Track>, onPick: (Track) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        items(tracks) { t -> com.zuvion.tv.ui.components.Chip(t.label, t.selected, { onPick(t) }) }
    }
}

/**
 * Barra de progresso navegável.
 * TV: com foco, ◀ ▶ movem a posição (o passo acelera ao segurar: 10 s → 30 s → 1 min → 2 min)
 * e o vídeo pula para lá quando você para de apertar (ou na hora, com OK).
 * Celular: toque ou arraste sobre a barra.
 */
@Composable
private fun SeekBar(pos: Long, dur: Long, onSeek: (Long) -> Unit, onActivity: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Long?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(1) }
    val shown = target ?: pos
    // Pula para a posição escolhida quando as teclas param.
    LaunchedEffect(target, dragging) {
        val t = target ?: return@LaunchedEffect
        if (dragging) return@LaunchedEffect
        delay(650)
        onSeek(t); target = null
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(fmtTime(shown), color = if (target != null) Z.Text else Z.Muted, style = MaterialTheme.typography.labelMedium)
        Box(
            Modifier
                .weight(1f)
                .height(28.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val repeat = ev.nativeKeyEvent.repeatCount
                    val step = when { repeat < 4 -> 10_000L; repeat < 12 -> 30_000L; repeat < 24 -> 60_000L; else -> 120_000L }
                    when (ev.key.nativeKeyCode) {
                        AKeyEvent.KEYCODE_DPAD_RIGHT -> { target = (shown + step).coerceAtMost(dur - 1000); onActivity(); true }
                        AKeyEvent.KEYCODE_DPAD_LEFT -> { target = (shown - step).coerceAtLeast(0); onActivity(); true }
                        AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER -> { target?.let { onSeek(it); target = null }; onActivity(); true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(dur) {
                    detectTapGestures { o -> onSeek((o.x / widthPx * dur).toLong().coerceIn(0, dur - 1000)); onActivity() }
                }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; target = (o.x / widthPx * dur).toLong() },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false; target = null }
                    ) { change, _ ->
                        target = (change.position.x / widthPx * dur).toLong().coerceIn(0, dur - 1000); onActivity()
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val f = if (dur > 0) (shown.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val h = if (focused || dragging) 8.dp else 6.dp
            ProgressLine(f, Modifier.fillMaxWidth(), height = h)
            if (focused || dragging || target != null) {
                // Marcador da posição
                Box(Modifier.fillMaxWidth(f).height(1.dp))
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val x = maxWidth * f - 9.dp
                    Box(
                        Modifier.padding(start = x.coerceAtLeast(0.dp)).size(18.dp)
                            .background(Color.White, CircleShape)
                            .then(if (focused) Modifier.border(3.dp, Z.Violet, CircleShape) else Modifier)
                    )
                }
            }
        }
        Text(fmtTime(dur), color = Z.Muted, style = MaterialTheme.typography.labelMedium)
    }
}
