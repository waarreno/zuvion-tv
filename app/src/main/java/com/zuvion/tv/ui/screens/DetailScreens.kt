package com.zuvion.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Close
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
import com.zuvion.tv.data.Variants
import com.zuvion.tv.data.title
import androidx.compose.foundation.layout.navigationBarsPadding
import com.zuvion.tv.meta.EpisodeMeta
import com.zuvion.tv.meta.Meta
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.Chip
import com.zuvion.tv.ui.components.IconGlassButton
import com.zuvion.tv.ui.components.LoadingView
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.Poster
import com.zuvion.tv.ui.components.WideCard
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.fadeEdges

@Composable
private fun Backdrop(url: String?) {
    if (!url.isNullOrBlank()) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.45f))
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Z.Space.copy(alpha = 0.3f), Z.Space.copy(alpha = 0.95f)))))
}


/** Linha de informações: ano · nota TMDB · IMDb · Rotten · duração. */
private fun metaLine(item: Content, m: Meta?, extra: String? = null): String = listOfNotNull(
    m?.year ?: item.year,
    (m?.rating ?: item.rating)?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" },
    m?.imdbRating?.let { "IMDb $it" },
    m?.rotten?.let { "Rotten $it" },
    m?.runtime?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}min" else "$it min" },
    extra
).joinToString("  ·  ")

@Composable
fun MovieDetailScreen(state: AppState, base: Content) {
    val tv = LocalIsTv.current
    val data by state.store.data.collectAsState()
    // TMDB primeiro; os dados do servidor só são buscados se o TMDB não encontrar.
    val detail by produceState(base to state.meta.cached(base), base.key) {
        val m = state.meta.forContent(base, full = true)
        val server = runCatching { state.repo.source?.movieInfo(base) }.getOrNull() ?: base
        value = server to m
    }
    val (item, m) = detail
    val fav = remember(data) { state.store.isFavorite(base) }
    // Progresso em qualquer formato do mesmo filme.
    val progress = remember(data) { state.repo.variantsOf(base).firstNotNullOfOrNull { state.store.progressOf(it) } }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    // Toca o formato preferido (o usuário pode trocar no player).
    val playable = remember(item, m, progress) {
        val v = progress?.item ?: state.repo.preferredVariant(base)
        (if (v.key == base.key) item else v).copy(tmdbId = m?.tmdbId ?: item.tmdbId)
    }
    val poster = m?.poster ?: item.poster
    val genres = m?.genres.orEmpty()

    Box(Modifier.fillMaxSize()) {
        Backdrop(m?.backdrop ?: poster)
        Row(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(if (tv) 48.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = if (tv) Alignment.CenterVertically else Alignment.Top
        ) {
            if (tv) Poster(poster, item.name, Modifier.width(260.dp).height(385.dp).clip(RoundedCornerShape(24.dp)), label = item.name)
            Column(
                // Rolável nos dois formatos: sinopses longas não cortam os botões (o foco rola até eles na TV).
                Modifier.weight(1f).widthIn(max = 760.dp).fadeEdges().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!tv) IconGlassButton(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", { state.pop() }, size = 44.dp)
                if (!tv) Poster(poster, item.name, Modifier.width(150.dp).height(222.dp).clip(RoundedCornerShape(20.dp)), label = item.name)
                Text(if (genres.isEmpty()) "FILME" else "FILME · " + genres.take(3).joinToString(" · ").uppercase(), style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
                Text(m?.title?.ifBlank { null } ?: item.title, style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = Z.Text)
                // Qualidade (Full HD, 4K…) em vez do tipo de arquivo; com vários formatos, mostra todos.
                val qualities = remember(base.key, item.videoHeight) {
                    state.repo.variantsOf(base).map { v -> state.repo.qualityOf(if (v.key == item.key) item else v) }
                        .filter { it.isNotEmpty() }.distinct()
                        .sortedBy { listOf("4K", "Full HD", "HD", "SD").indexOf(it) }
                }
                val line = metaLine(item, m, qualities.joinToString(" / ").ifEmpty { null })
                if (line.isNotBlank()) Text(line, color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                val plot = m?.overview?.ifBlank { null } ?: item.plot
                plot?.let { Text(it, color = Z.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = if (tv) 6 else 12, overflow = TextOverflow.Ellipsis) }
                val people = listOfNotNull(m?.director?.let { "Direção: $it" }, m?.cast?.takeIf { it.isNotEmpty() }?.let { "Elenco: " + it.take(5).joinToString() })
                people.forEach { Text(it, color = Z.Faint, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val resume = progress?.takeIf { it.fraction in 0.01f..(data.settings.continueMaxPct / 100f) }
                    PillButton(if (resume != null) "Continuar" else "Assistir", { state.play(playable) }, Modifier.focusRequester(fr), icon = Icons.Outlined.PlayArrow, primary = true)
                    if (resume != null) PillButton("Do início", { state.play(playable, startMs = 0) }, icon = Icons.Outlined.Replay)
                    if (resume != null) PillButton("Remover de Continuar", {
                        state.store.removeProgress(resume.item); state.showToast("Removido de Continuar assistindo")
                    }, icon = Icons.Outlined.Close)
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
    val meta by produceState(state.meta.cached(item), item.key) { value = state.meta.forContent(item, full = true) }
    val info by produceState<SeriesInfo?>(null, item.key) {
        value = runCatching { state.repo.source?.seriesInfo(item) }.getOrNull() ?: SeriesInfo(item.plot, item.poster, emptyMap())
    }
    val fav = remember(data) { state.store.isFavorite(item) }
    var season by remember(info) { mutableStateOf(info?.seasons?.keys?.minOrNull() ?: 1) }
    val tmdbEps by produceState(emptyList<EpisodeMeta>(), meta?.tmdbId, season) {
        value = meta?.let { state.meta.season(it.tmdbId, season) }.orEmpty()
    }
    val fr = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        Backdrop(meta?.backdrop ?: meta?.poster ?: info?.poster ?: item.poster)
        val i = info
        if (i == null) LoadingView("Carregando episódios…") else SeriesBody(state, item, meta, i, tmdbEps, season, { season = it }, fav, data, tv, fr)
    }
}

@Composable
private fun SeriesBody(
    state: AppState, series: Content, m: Meta?, i: SeriesInfo, tmdbEps: List<EpisodeMeta>, season: Int, setSeason: (Int) -> Unit,
    fav: Boolean, data: com.zuvion.tv.data.AppData, tv: Boolean, fr: FocusRequester
) {
    val tmdbId = m?.tmdbId ?: 0
    // Episódios do servidor, enriquecidos com nome/sinopse/imagem do TMDB.
    val byNumber = tmdbEps.associateBy { it.episode }
    val episodes = i.seasons[season].orEmpty().map { e ->
        val t = byNumber[e.episode]
        e.copy(
            name = t?.name?.ifBlank { null } ?: e.name,
            plot = t?.overview?.ifBlank { null } ?: e.plot,
            poster = t?.still ?: e.poster,
            tmdbId = tmdbId
        )
    }
    val allEpisodes = i.seasons.toSortedMap().flatMap { (sn, list) -> if (sn == season) episodes else list.map { it.copy(tmdbId = tmdbId) } }
    val genres = m?.genres.orEmpty()
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().fadeEdges(),
        contentPadding = PaddingValues(if (tv) 48.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Poster(m?.poster ?: i.poster ?: series.poster, series.name, Modifier.width(if (tv) 200.dp else 120.dp).height(if (tv) 296.dp else 178.dp).clip(RoundedCornerShape(22.dp)), label = series.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!tv) IconGlassButton(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", { state.pop() }, size = 44.dp)
                    Text(if (genres.isEmpty()) "SÉRIE" else "SÉRIE · " + genres.take(3).joinToString(" · ").uppercase(), style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
                    Text(m?.title?.ifBlank { null } ?: series.title, style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = Z.Text)
                    Text(metaLine(series, m?.copy(runtime = 0), "${i.seasons.size} temporada(s)"), color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                    (m?.overview?.ifBlank { null } ?: i.plot)?.let { Text(it, color = Z.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis) }
                    m?.cast?.takeIf { it.isNotEmpty() }?.let { Text("Elenco: " + it.take(5).joinToString(), color = Z.Faint, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val last = data.profileData[state.store.activeProfile?.id]?.history
                            ?.firstOrNull { it.item.seriesId == series.id && it.item.playlistId == series.playlistId }
                        val target = last?.item?.copy(tmdbId = tmdbId) ?: allEpisodes.firstOrNull()
                        if (target != null) PillButton(
                            if (last != null) "Continuar T${target.season}·E${target.episode}" else "Assistir T${target.season}·E${target.episode}",
                            { state.play(target, allEpisodes) }, Modifier.focusRequester(fr), icon = Icons.Outlined.PlayArrow, primary = true
                        )
                        if (last != null) PillButton("Remover de Continuar", {
                            state.store.removeProgress(last.item); state.showToast("Removido de Continuar assistindo")
                        }, icon = Icons.Outlined.Close)
                        PillButton(if (fav) "Na sua lista" else "Minha lista", { state.store.toggleFavorite(series) },
                            icon = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder)
                    }
                }
            }
        }
        if (i.seasons.size > 1) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(i.seasons.keys.sorted()) { s -> Chip("Temporada $s", s == season, { setSeason(s) }) }
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
