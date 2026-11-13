package com.zuvion.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.Kind
import com.zuvion.tv.data.Stremio
import com.zuvion.tv.data.StremioStream
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.theme.Z

private sealed interface SourcesState {
    data object Loading : SourcesState
    data class Failed(val message: String) : SourcesState
    data class Ready(val streams: List<StremioStream>) : SourcesState
}

/**
 * Links das fontes extras (addons do Stremio) para um filme ou episódio.
 * [item] = o filme ou o episódio; [title] = o filme ou a série (para achar o id do IMDb).
 */
@Composable
fun SourcesDialog(state: AppState, item: Content, title: Content, list: List<Content>, onDismiss: () -> Unit) {
    val addons = state.store.settings.stremioAddons
    val st by produceState<SourcesState>(SourcesState.Loading, item.key) {
        val imdb = runCatching { state.meta.imdbId(title) }.getOrNull()
        if (imdb == null) { value = SourcesState.Failed("Não foi possível identificar este título no TMDB para buscar nas outras fontes."); return@produceState }
        val (type, id) = if (item.kind == Kind.EPISODE) "series" to "$imdb:${item.season}:${item.episode}" else "movie" to imdb
        val streams = Stremio.streams(addons, type, id)
        value = if (streams.isEmpty()) SourcesState.Failed("Nenhum link encontrado nas outras fontes.") else SourcesState.Ready(streams)
    }
    val fr = remember { FocusRequester() }
    GlassDialog(onDismiss) {
        Text("Outras fontes", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
        Text(if (item.kind == Kind.EPISODE) "${title.name} · T${item.season}·E${item.episode}" else item.name,
            color = Z.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when (val s = st) {
            SourcesState.Loading -> Text("Procurando nos addons…", color = Z.Muted)
            is SourcesState.Failed -> Text(s.message, color = Z.Muted)
            is SourcesState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.streams.forEachIndexed { i, x ->
                    Focusable({
                        onDismiss()
                        state.play(item, list, overrideUrl = x.url, subtitle = x.addon, external = true)
                    }, Modifier.fillMaxWidth().let { if (i == 0) it.focusRequester(fr) else it }, RoundedCornerShape(18.dp), focusScale = 1.01f) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null, tint = Z.Cyan)
                            Column(Modifier.weight(1f)) {
                                Text(x.title, color = Z.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOf(x.addon, x.detail.replace('\n', ' ')).filter { it.isNotBlank() && it != x.title }.joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, color = Z.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
            }
        }
        PillButton("Fechar", onDismiss, Modifier.fillMaxWidth().let { if (st !is SourcesState.Ready) it.focusRequester(fr) else it })
        if (st !is SourcesState.Ready) LaunchedEffect(st) { runCatching { fr.requestFocus() } }
    }
}
