package com.zuvion.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.SeriesInfo
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.Chip
import com.zuvion.tv.ui.components.IconGlassButton
import com.zuvion.tv.ui.components.LoadingView
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.Poster
import com.zuvion.tv.ui.components.WideCard
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z

@Composable
private fun Backdrop(url: String?) {
    if (!url.isNullOrBlank()) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.45f))
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Z.Space.copy(alpha = 0.3f), Z.Space.copy(alpha = 0.95f)))))
}

@Composable
fun MovieDetailScreen(state: AppState, base: Content) {
    val tv = LocalIsTv.current
    val data by state.store.data.collectAsState()
    val item by produceState(base, base.key) { value = runCatching { state.repo.source?.movieInfo(base) }.getOrNull() ?: base }
    val fav = remember(data) { state.store.isFavorite(base) }
    val progress = remember(data) { state.store.progressOf(base) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        Backdrop(item.poster)
        Row(
            Modifier.fillMaxSize().statusBarsPadding().padding(if (tv) 48.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = if (tv) Alignment.CenterVertically else Alignment.Top
        ) {
            if (tv) Poster(item.poster, item.name, Modifier.width(260.dp).height(385.dp).clip(RoundedCornerShape(24.dp)), label = item.name)
            Column(Modifier.weight(1f).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!tv) IconGlassButton(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", { state.pop() }, size = 44.dp)
                if (!tv) Poster(item.poster, item.name, Modifier.width(150.dp).height(222.dp).clip(RoundedCornerShape(20.dp)), label = item.name)
                Text("FILME", style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
                Text(item.name, style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = Z.Text)
                val meta = listOfNotNull(item.year, item.rating?.let { "★ $it" }, item.extension?.uppercase()).joinToString("  ·  ")
                if (meta.isNotBlank()) Text(meta, color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                item.plot?.let { Text(it, color = Z.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 7, overflow = TextOverflow.Ellipsis) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val resume = progress?.takeIf { it.fraction in 0.01f..0.95f }
                    PillButton(if (resume != null) "Continuar" else "Assistir", { state.play(item) }, Modifier.focusRequester(fr), icon = Icons.Outlined.PlayArrow, primary = true)
                    if (resume != null) PillButton("Do início", { state.play(item, startMs = 0) }, icon = Icons.Outlined.Replay)
                    PillButton(if (fav) "Na sua lista" else "Minha lista", { state.store.toggleFavorite(base) },
                        icon = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder)
                }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(state: AppState, item: Content) {
    val tv = LocalIsTv.current
    val data by state.store.data.collectAsState()
    val info by produceState<SeriesInfo?>(null, item.key) {
        value = runCatching { state.repo.source?.seriesInfo(item) }.getOrNull() ?: SeriesInfo(item.plot, item.poster, emptyMap())
    }
    val fav = remember(data) { state.store.isFavorite(item) }
    var season by remember(info) { mutableStateOf(info?.seasons?.keys?.firstOrNull() ?: 1) }
    val fr = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        Backdrop(info?.poster ?: item.poster)
        val i = info
        if (i == null) LoadingView("Carregando episódios…") else SeriesBody(state, item, i, season, { season = it }, fav, data, tv, fr)
    }
}

@Composable
private fun SeriesBody(
    state: AppState, series: Content, i: SeriesInfo, season: Int, setSeason: (Int) -> Unit,
    fav: Boolean, data: com.zuvion.tv.data.AppData, tv: Boolean, fr: FocusRequester
) {
    run {
        val episodes = i.seasons[season].orEmpty()
        val allEpisodes = i.seasons.values.flatten()
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(if (tv) 48.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Poster(i.poster ?: series.poster, series.name, Modifier.width(if (tv) 200.dp else 120.dp).height(if (tv) 296.dp else 178.dp).clip(RoundedCornerShape(22.dp)), label = series.name)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!tv) IconGlassButton(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", { state.pop() }, size = 44.dp)
                        Text("SÉRIE", style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
                        Text(series.name, style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = Z.Text)
                        val meta = listOfNotNull(series.year, series.rating?.let { "★ $it" }, "${i.seasons.size} temporada(s)").joinToString("  ·  ")
                        Text(meta, color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                        i.plot?.let { Text(it, color = Z.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val last = data.profileData[state.store.activeProfile?.id]?.history
                                ?.firstOrNull { it.item.seriesId == series.id && it.item.playlistId == series.playlistId }
                            val target = last?.item ?: allEpisodes.firstOrNull()
                            if (target != null) PillButton(
                                if (last != null) "Continuar T${target.season}·E${target.episode}" else "Assistir T${target.season}·E${target.episode}",
                                { state.play(target, allEpisodes) }, Modifier.focusRequester(fr), icon = Icons.Outlined.PlayArrow, primary = true
                            )
                            PillButton(if (fav) "Na sua lista" else "Minha lista", { state.store.toggleFavorite(series) },
                                icon = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder)
                        }
                    }
                }
            }
            if (i.seasons.size > 1) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(i.seasons.keys.toList()) { s -> Chip("Temporada $s", s == season, { setSeason(s) }) }
                }
            }
            if (episodes.isEmpty()) item { Text("Nenhum episódio encontrado.", color = Z.Muted) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(episodes) { e ->
                        val pr = state.store.progressOf(e)
                        WideCard(
                            "E${e.episode} · ${e.name}", e.poster, { state.play(e, allEpisodes) },
                            width = if (tv) 260.dp else 200.dp, progress = pr?.fraction,
                            subtitle = e.plot
                        )
                    }
                }
            }
        }
    }
}
