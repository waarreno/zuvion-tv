package com.zuvion.tv.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zuvion.tv.source.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Motor alternativo (libVLC): toca formatos que o ExoPlayer não suporta. */
class VlcEngine(context: Context) : VideoEngine {
    override val name = "VLC"
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state
    private val libVlc = LibVLC(context, arrayListOf("--network-caching=1500", "--no-drop-late-frames", "--no-skip-frames", "--http-reconnect"))
    private val mp = MediaPlayer(libVlc)
    private var layout: VLCVideoLayout? = null
    private var pendingSeek = 0L

    init {
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> {
                    _state.value = _state.value.copy(playing = true, buffering = false, error = null)
                    if (pendingSeek > 0) { mp.setTime(pendingSeek); pendingSeek = 0 }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> _state.value = _state.value.copy(playing = false)
                MediaPlayer.Event.Buffering -> _state.value = _state.value.copy(buffering = e.buffering < 100f)
                MediaPlayer.Event.EncounteredError -> _state.value = _state.value.copy(error = "VLC não conseguiu abrir o stream", buffering = false)
                MediaPlayer.Event.EndReached -> _state.value = _state.value.copy(ended = true, playing = false)
                MediaPlayer.Event.Vout -> {
                    val h = mp.currentVideoTrack?.height ?: 0
                    if (h > 0) _state.value = _state.value.copy(videoSize = when {
                        h >= 2000 -> "4K"; h >= 1000 -> "FHD"; h >= 700 -> "HD"; else -> "SD"
                    })
                }
            }
        }
    }

    override val position: Long get() = mp.time.coerceAtLeast(0)
    override val duration: Long get() = mp.length.coerceAtLeast(0)

    override fun play(url: String, startMs: Long) {
        _state.value = EngineState()
        val media = Media(libVlc, Uri.parse(url))
        media.addOption(":network-caching=1500")
        media.addOption(":http-user-agent=${Http.USER_AGENT}")
        mp.setMedia(media)
        media.release()
        pendingSeek = startMs
        mp.play()
    }

    override fun togglePause() { if (mp.isPlaying) mp.pause() else mp.play() }
    override fun pause() = mp.pause()
    override fun seekTo(ms: Long) { mp.setTime(ms) }
    override fun setVolume(v: Float) { mp.setVolume((v * 100).toInt()) }

    override fun audioTracks(): List<Track> {
        val cur = mp.audioTrack
        return mp.audioTracks.orEmpty().filter { it.id >= 0 }.map { Track(it.id.toString(), it.name ?: "Faixa ${it.id}", it.id == cur) }
    }

    override fun subtitleTracks(): List<Track> {
        val cur = mp.spuTrack
        return mp.spuTracks.orEmpty().filter { it.id >= 0 }.map { Track(it.id.toString(), it.name ?: "Legenda ${it.id}", it.id == cur) }
    }

    override fun selectAudio(id: String) { mp.setAudioTrack(id.toInt()) }
    override fun selectSubtitle(id: String?) { mp.setSpuTrack(id?.toInt() ?: -1) }

    override fun setAspect(mode: AspectMode) {
        when (mode) {
            AspectMode.FIT -> { mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT) }
            AspectMode.FILL -> { mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL) }
            AspectMode.ZOOM -> { mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN) }
        }
    }

    override fun release() {
        mp.stop()
        mp.detachViews()
        mp.release()
        libVlc.release()
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                VLCVideoLayout(ctx).also {
                    layout = it
                    mp.attachViews(it, null, false, false)
                }
            },
            onRelease = { mp.detachViews() }
        )
    }
}
