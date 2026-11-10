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

class ExoEngine(context: Context) : VideoEngine {
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
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(s: Int) = sync()
                override fun onIsPlayingChanged(isPlaying: Boolean) = sync()
                override fun onPlayerError(error: PlaybackException) {
                    _state.value = _state.value.copy(error = error.errorCodeName + ": " + (error.message ?: ""), buffering = false)
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0) _state.value = _state.value.copy(videoSize = quality(videoSize.height))
                }
                override fun onTracksChanged(tracks: Tracks) = sync()
            })
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
