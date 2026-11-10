package com.zuvion.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.Kind
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.Screen
import com.zuvion.tv.ui.Tab
import com.zuvion.tv.ui.components.Badge
import com.zuvion.tv.ui.components.ChannelLogo
import com.zuvion.tv.ui.components.EmptyView
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.IconGlassButton
import com.zuvion.tv.ui.components.LoadingView
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.PosterCard
import com.zuvion.tv.ui.components.SectionTitle
import com.zuvion.tv.ui.components.WideCard
import com.zuvion.tv.ui.components.Wordmark
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z

@Composable
fun HomeScreen(state: AppState) {
    val lib by state.repo.library.collectAsState()
    val data by state.store.data.collectAsState()
    val programs by state.epg.programs.collectAsState()
    val tv = LocalIsTv.current
    val pd = state.store.profileData()
    val pid = state.store.activePlaylist?.id

    if (lib.loading && lib.items.isEmpty()) { LoadingView("Carregando sua lista…"); return }
    if (lib.error != null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(lib.error ?: "", color = Z.Muted, modifier = Modifier.padding(24.dp))
            PillButton("Tentar de novo", { state.repo.reload() }, primary = true)
            Spacer(Modifier.height(12.dp))
            PillButton("Ajustes", { state.tab = Tab.SETTINGS })
        }
        return
    }

    val movies = lib.items[Kind.MOVIE].orEmpty()
    val series = lib.items[Kind.SERIES].orEmpty()
    val recentMovies = remember(movies) { movies.sortedByDescending { it.added }.take(30) }
    val recentSeries = remember(series) { series.sortedByDescending { it.added }.take(30) }
    val history = pd.history.filter { it.item.playlistId == pid }
    val cont = history.filter { it.item.kind != Kind.LIVE }
    val favChannels = pd.favorites.filter { it.playlistId == pid && it.kind == Kind.LIVE }
    val recentChannels = history.filter { it.item.kind == Kind.LIVE }.map { it.item }
    val favVod = pd.favorites.filter { it.playlistId == pid && it.kind != Kind.LIVE }
    var hero by remember(recentMovies) { mutableStateOf(recentMovies.firstOrNull() ?: recentSeries.firstOrNull()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (tv) 40.dp else 110.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        item { HomeHeader(state, data.profiles.size > 1) }
        hero?.let { h -> item { Hero(state, h) } }
        if (cont.isNotEmpty()) item {
            ShelfRow(cont, "Continuar assistindo") { e ->
                WideCard(e.item.name, e.item.poster, { state.play(e.item) }, progress = e.fraction,
                    subtitle = if (e.item.kind == Kind.EPISODE) "T${e.item.season} · E${e.item.episode}" else null)
            }
        }
        val channels = (favChannels + recentChannels).distinctBy { it.key }.take(20)
        if (channels.isNotEmpty()) item {
            ShelfRow(channels, if (favChannels.isNotEmpty()) "Canais favoritos" else "Vistos recentemente") { c ->
                val now = programs.let { state.epg.nowNext(c).first }
                Focusable({ state.play(c, channels) }, Modifier.width(240.dp), RoundedCornerShape(20.dp)) {
                    androidx.compose.foundation.layout.Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChannelLogo(c, 44.dp)
                        Column {
                            Text(c.name, style = MaterialTheme.typography.labelLarge, color = Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(now?.title?.let { "Agora: $it" } ?: "Ao vivo", style = MaterialTheme.typography.labelMedium, color = Z.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (favVod.isNotEmpty()) item {
            ShelfRow(favVod, "Minha lista") { m -> PosterCard(m, { state.open(m) }, onFocus = { if (tv) hero = m }) }
        }
        if (recentMovies.isNotEmpty()) item {
            ShelfRow(recentMovies, "Filmes adicionados recentemente") { m -> PosterCard(m, { state.open(m) }, onFocus = { if (tv) hero = m }) }
        }
        if (recentSeries.isNotEmpty()) item {
            ShelfRow(recentSeries, "Séries") { m -> PosterCard(m, { state.open(m) }, onFocus = { if (tv) hero = m }) }
        }
        if (movies.isEmpty() && series.isEmpty() && channels.isEmpty() && cont.isEmpty()) item {
            Box(Modifier.height(240.dp)) { EmptyView("Abra “Ao vivo” para começar a assistir.") }
        }
    }
}

@Composable
private fun <T> ShelfRow(items: List<T>, title: String, card: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = 28.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { card(it) }
        }
    }
}

@Composable
private fun HomeHeader(state: AppState, multiProfile: Boolean) {
    val tv = LocalIsTv.current
    val profile = state.store.activeProfile
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Wordmark(22)
            if (tv) Text("Sua TV, seu universo", style = MaterialTheme.typography.labelMedium, color = Z.Muted)
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!tv) {
                IconGlassButton(Icons.Outlined.Search, "Buscar", { state.tab = Tab.SEARCH }, size = 44.dp)
                IconGlassButton(Icons.Outlined.FiberManualRecord, "Gravações", { state.tab = Tab.RECORDINGS }, size = 44.dp)
                IconGlassButton(Icons.Outlined.Tune, "Ajustes", { state.tab = Tab.SETTINGS }, size = 44.dp)
            } else {
                Clock()
            }
            if (profile != null) {
                Focusable({ if (multiProfile) state.push(Screen.Profiles) else state.tab = Tab.SETTINGS }, shape = CircleShape, focusScale = 1.1f, glass = false) {
                    ProfileAvatar(profile, 40)
                }
            }
        }
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(20_000); now = System.currentTimeMillis() }
    }
    Badge(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now)))
}

@Composable
private fun Hero(state: AppState, item: Content) {
    val tv = LocalIsTv.current
    Box(
        Modifier
            .padding(horizontal = if (tv) 28.dp else 16.dp)
            .fillMaxWidth()
            .height(if (tv) 300.dp else 230.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(30.dp))
    ) {
        if (!item.poster.isNullOrBlank()) {
            AsyncImage(item.poster, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp).alpha(0.55f))
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Z.Space.copy(alpha = 0.92f), Color.Transparent))))
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (item.kind == Kind.SERIES) "EM DESTAQUE · SÉRIE" else "EM DESTAQUE · FILME", style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
                Text(item.name, style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = Z.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString("  ·  ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = Z.Muted)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Assistir", { if (item.kind == Kind.MOVIE) state.play(item) else state.open(item) }, icon = Icons.Outlined.PlayArrow, primary = true)
                    PillButton("Detalhes", { state.open(item) }, icon = Icons.Outlined.Info)
                }
            }
            if (tv && !item.poster.isNullOrBlank()) {
                AsyncImage(item.poster, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(start = 24.dp).width(170.dp).height(250.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black))
            }
        }
    }
}

