package com.zuvion.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

data class EngineState(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val error: String? = null,
    val ended: Boolean = false,
    val videoSize: String? = null,
    /** Altura real do vídeo de origem (antes do realce de imagem). */
    val videoHeight: Int = 0
)

data class Track(val id: String, val label: String, val selected: Boolean)

enum class AspectMode(val label: String) { FIT("Ajustar"), FILL("Preencher"), ZOOM("Zoom") }

interface VideoEngine {
    val name: String
    val state: StateFlow<EngineState>
    val position: Long
    val duration: Long
    fun play(url: String, startMs: Long = 0)
    fun togglePause()
    fun pause()
    fun seekTo(ms: Long)
    fun seekBy(ms: Long) = seekTo((position + ms).coerceAtLeast(0))
    fun setVolume(v: Float)
    fun audioTracks(): List<Track>
    fun subtitleTracks(): List<Track>
    fun selectAudio(id: String)
    fun selectSubtitle(id: String?)
    fun setAspect(mode: AspectMode)
    /** Estatísticas para nerds (rótulo, valor), atualizadas a cada chamada. */
    fun stats(): List<Pair<String, String>> = emptyList()
    /** (quadros exibidos por segundo, quadros/s do vídeo) medidos desde a última chamada; 0 = desconhecido. */
    fun frameRate(): Pair<Double, Float> = 0.0 to 0f
    fun release()

    @Composable
    fun Surface(modifier: Modifier)
}
