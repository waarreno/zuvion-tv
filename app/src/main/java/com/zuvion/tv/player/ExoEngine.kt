package com.zuvion.tv.player

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zuvion.tv.source.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [enhance] = nível do realce de imagem ("off", "soft", "strong", "max"). */
class ExoEngine(context: Context, private val enhance: String = "off") : VideoEngine {
    /** Lado menor da tela física (1080 numa TV Full HD, 2160 numa TV 4K): alvo da ampliação. */
    private val display = displayMode(context)
    private var lastRendered = 0
    private var lastStatsAt = 0L
    private var fps = 0.0
    @Volatile private var bandwidth = 0L
    override val name = "ExoPlayer"
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state
    private var view: PlayerView? = null
    private var aspect = AspectMode.FIT

    private val player: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    )
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(Http.streamClient).setUserAgent(Http.USER_AGENT))
            )
        )
        .setLoadControl(
            DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_500, 3_000).build()
        )
        .build().apply {
            // Antes de ligar a superfície de vídeo (feito no PlayerView).
            if (enhance != "off") {
                Enhance.lastOutput = null
                setVideoEffects(Enhance.effects(enhance, minOf(display.first, display.second).coerceAtMost(2160)))
            }
            android.util.Log.i("Zuvion", "exo engine enhance=$enhance display=${display.first}x${display.second}")
            addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onBandwidthEstimate(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
                    bandwidth = bitrateEstimate
                }
            })
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(s: Int) = sync()
                override fun onIsPlayingChanged(isPlaying: Boolean) = sync()
                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.w("Zuvion", "player error ${error.errorCodeName}", error)
                    _state.value = _state.value.copy(error = error.errorCodeName + ": " + (error.message ?: ""), buffering = false)
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    // Com o realce ligado, o tamanho de saída é o ampliado: usa a altura do vídeo de origem.
                    val h = this@apply.videoFormat?.height?.takeIf { it > 0 } ?: videoSize.height
                    if (h > 0) _state.value = _state.value.copy(videoSize = quality(h), videoHeight = h)
                    android.util.Log.i("Zuvion", "video source=${h}p output=${videoSize.width}x${videoSize.height}")
                }
                override fun onTracksChanged(tracks: Tracks) = sync()
                // Com o realce ligado o Android não avisa o tamanho do vídeo: lê do formato no 1º quadro.
                override fun onRenderedFirstFrame() {
                    val h = this@apply.videoFormat?.height ?: 0
                    if (h > 0 && _state.value.videoHeight != h) {
                        _state.value = _state.value.copy(videoSize = quality(h), videoHeight = h)
                        android.util.Log.i("Zuvion", "video source=${h}p (primeiro quadro)")
                    }
                }
            })
        }

    private var frLast = 0
    private var frAt = 0L
    override fun frameRate(): Pair<Double, Float> {
        val c = player.videoDecoderCounters ?: return 0.0 to 0f
        c.ensureUpdated()
        val now = System.currentTimeMillis()
        val r = c.renderedOutputBufferCount
        val f = if (frAt > 0 && now > frAt) (r - frLast) * 1000.0 / (now - frAt) else 0.0
        frLast = r; frAt = now
        return f to (player.videoFormat?.frameRate ?: 0f)
    }

    override fun stats(): List<Pair<String, String>> {
        val v = player.videoFormat
        val a = player.audioFormat
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val now = System.currentTimeMillis()
        val rendered = counters?.renderedOutputBufferCount ?: 0
        if (lastStatsAt > 0 && now > lastStatsAt) fps = (rendered - lastRendered) * 1000.0 / (now - lastStatsAt)
        lastRendered = rendered; lastStatsAt = now
        val out = Enhance.lastOutput
        return buildList {
            add("Motor" to "ExoPlayer (Media3)")
            if (v != null) add("Vídeo" to listOfNotNull(
                "${v.width}×${v.height}", codecName(v.codecs ?: v.sampleMimeType),
                v.frameRate.takeIf { it > 0 }?.let { "%.2f fps".format(it) },
                v.bitrate.takeIf { it > 0 }?.let { mbps(it.toLong()) }
            ).joinToString(" · "))
            add("Realce" to if (enhance == "off") "Desligado" else
                Enhance.label(enhance) + (out?.let { " · saída ${it.width}×${it.height}" } ?: ""))
            add("Quadros" to "%.1f/s exibidos · %d descartados".format(fps, counters?.droppedBufferCount ?: 0))
            if (a != null) add("Áudio" to listOfNotNull(
                codecName(a.codecs ?: a.sampleMimeType), a.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" },
                a.channelCount.takeIf { it > 0 }?.let { "${it}ch" }, a.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
            ).joinToString(" · "))
            add("Buffer" to "%.1f s".format(player.totalBufferedDuration / 1000.0))
            if (bandwidth > 0) add("Conexão" to mbps(bandwidth) + " (estimativa)")
            add("Tela" to "${display.first}×${display.second}")
        }
    }

    private fun quality(h: Int) = when {
        h >= 2000 -> "4K"; h >= 1000 -> "FHD"; h >= 700 -> "HD"; else -> "SD"
    }

    private fun sync() {
        _state.value = _state.value.copy(
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            ended = player.playbackState == Player.STATE_ENDED
        )
    }

    override val position: Long get() = player.currentPosition.coerceAtLeast(0)
    override val duration: Long get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0

    override fun play(url: String, startMs: Long) {
        _state.value = EngineState()
        val b = MediaItem.Builder().setUri(url)
        val l = url.lowercase()
        when {
            l.contains(".m3u8") || l.contains("/hls/") -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            l.contains(".mpd") -> b.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        player.setMediaItem(b.build(), startMs)
        player.prepare()
        player.playWhenReady = true
    }

    override fun togglePause() { if (player.isPlaying) player.pause() else player.play() }
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun setVolume(v: Float) { player.volume = v }

    private fun tracksOf(type: Int): List<Track> = player.currentTracks.groups
        .filter { it.type == type }
        .flatMapIndexed { gi, g ->
            (0 until g.length).filter { g.isTrackSupported(it) }.map { ti ->
                val f = g.getTrackFormat(ti)
                val label = listOfNotNull(f.label, f.language?.uppercase(), f.channelCount.takeIf { it > 0 }?.let { "${it}ch" })
                    .joinToString(" · ").ifBlank { "Faixa ${gi + 1}" }
                Track("$gi:$ti", label, g.isTrackSelected(ti))
            }
        }

    override fun audioTracks() = tracksOf(C.TRACK_TYPE_AUDIO)
    override fun subtitleTracks() = tracksOf(C.TRACK_TYPE_TEXT)

    private fun select(type: Int, id: String?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (id == null) {
            params.setTrackTypeDisabled(type, true)
        } else {
            val (gi, ti) = id.split(":").map { it.toInt() }
            val group = player.currentTracks.groups.filter { it.type == type }.getOrNull(gi) ?: return
            params.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
        }
        player.trackSelectionParameters = params.build()
    }

    override fun selectAudio(id: String) = select(C.TRACK_TYPE_AUDIO, id)
    override fun selectSubtitle(id: String?) = select(C.TRACK_TYPE_TEXT, id)

    override fun setAspect(mode: AspectMode) {
        aspect = mode
        view?.resizeMode = when (mode) {
            AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    override fun release() {
        view?.player = null
        player.release()
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    player = this@ExoEngine.player
                    view = this
                    setAspect(aspect)
                }
            },
            onRelease = { it.player = null }
        )
    }
}


internal fun mbps(bps: Long) = if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1e6) else "${bps / 1000} kbps"

internal fun codecName(c: String?): String? {
    val x = c?.lowercase() ?: return null
    return when {
        x.startsWith("avc") || x.contains("h264") -> "H.264 ($c)"
        x.startsWith("hev") || x.startsWith("hvc") || x.contains("hevc") -> "H.265 ($c)"
        x.startsWith("av01") || x.contains("av1") -> "AV1"
        x.startsWith("vp9") || x.startsWith("vp09") -> "VP9"
        x.startsWith("mp4a") || x.contains("aac") -> "AAC"
        x.contains("ac-3") || x.contains("ac3") -> "Dolby Digital"
        x.contains("ec-3") || x.contains("eac3") -> "Dolby Digital Plus"
        x.contains("mpeg") || x.contains("mp2") -> "MPEG"
        else -> c
    }
}

/** Resolução física do modo de tela atual (largura, altura). */
internal fun displayMode(context: Context): Pair<Int, Int> = runCatching {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    @Suppress("DEPRECATION") val d = wm.defaultDisplay
    if (android.os.Build.VERSION.SDK_INT >= 23) d.mode.physicalWidth to d.mode.physicalHeight
    else android.graphics.Point().also { @Suppress("DEPRECATION") d.getRealSize(it) }.let { it.x to it.y }
}.getOrDefault(1920 to 1080)
