package com.zuvion.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuvion.tv.data.Category
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.Kind
import com.zuvion.tv.data.title
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.Chip
import com.zuvion.tv.ui.components.ChannelLogo
import com.zuvion.tv.ui.components.EmptyView
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.LoadingView
import com.zuvion.tv.ui.components.ProgressLine
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass
import com.zuvion.tv.ui.theme.fadeEdges

const val CAT_ALL = "__all"
const val CAT_FAV = "__fav"

/** Lista de categorias com "Favoritos" e "Todos", respeitando o PIN. */
@Composable
fun rememberCategoryPicker(state: AppState, kind: Kind): Triple<List<Category>, String, (Category) -> Unit> {
    val lib by state.repo.library.collectAsState()
    val data by state.store.data.collectAsState()
    val genreMode = data.settings.genreMode
    val base = remember(kind) { listOf(Category(CAT_FAV, "Favoritos", kind), Category(CAT_ALL, "Todos", kind)) }
    // Gêneros (ou categorias do provedor) montados fora da thread da tela.
    val extra by produceState(emptyList<Category>(), lib, genreMode, data.settings.hideAdult, data.settings.activeProfileId) {
        value = state.repo.categoryList(kind)
    }
    val cats = remember(extra) { base + extra }
    var selected by remember(kind) { mutableStateOf(if (state.store.profileData().favorites.any { it.kind == kind }) CAT_FAV else CAT_ALL) }
    var pinFor by remember { mutableStateOf<Category?>(null) }
    pinFor?.let { c ->
        PinDialog("Conteúdo protegido", { pinFor = null }) { pin ->
            (pin == state.store.settings.parentalPin).also { if (it) { state.unlocked.add(c.id); selected = c.id; pinFor = null } }
        }
    }
    val onSelect: (Category) -> Unit = { c ->
        if (state.needsPin(c.id, c.adult)) { pinFor = c } else { selected = c.id }
        Unit
    }
    return Triple(cats, selected, onSelect)
}

@Composable
fun categoryItems(state: AppState, kind: Kind, catId: String): List<Content>? {
    val lib by state.repo.library.collectAsState()
    val data by state.store.data.collectAsState()
    val pid = state.store.activePlaylist?.id
    val favKind = if (kind == Kind.SERIES) Kind.SERIES else kind
    val v by produceState<List<Content>?>(null, catId, lib, if (catId == CAT_FAV) data else null) {
        value = null
        value = when (catId) {
            CAT_FAV -> state.store.profileData().favorites.filter { it.kind == favKind && it.playlistId == pid }
            CAT_ALL -> state.repo.itemsIn(kind, null)
            else -> state.repo.itemsIn(kind, catId)
        }
    }
    return v
}

@Composable
fun LiveScreen(state: AppState) {
    val tv = LocalIsTv.current
    val (cats, selected, select) = rememberCategoryPicker(state, Kind.LIVE)
    val list = categoryItems(state, Kind.LIVE, selected)
    val programs by state.epg.programs.collectAsState()
    var preview by remember { mutableStateOf<Content?>(null) }

    if (tv) {
        Row(Modifier.fillMaxSize().padding(top = 20.dp, end = 20.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(
                Modifier.width(230.dp).fillMaxHeight().glass(RoundedCornerShape(28.dp)),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(cats) { c -> CategoryRow(c, c.id == selected, state.needsPin(c.id, c.adult)) { select(c) } }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    list == null -> LoadingView()
                    list.isEmpty() -> EmptyView(if (selected == CAT_FAV) "Segure OK sobre um canal para favoritar." else "Nenhum canal nesta categoria.")
                    else -> LazyColumn(Modifier.fadeEdges(), contentPadding = PaddingValues(vertical = 18.dp, horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list) { c ->
                            ChannelRowItem(state, c, programs.isNotEmpty(), onFocus = { preview = c }) { state.play(c, list) }
                        }
                    }
                }
            }
            preview?.let { p -> ChannelPreview(state, p, Modifier.width(320.dp).fillMaxHeight()) }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Text("Ao vivo", style = MaterialTheme.typography.headlineMedium, color = Z.Text, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats) { c -> Chip(c.name, c.id == selected, { select(c) }) }
            }
            when {
                list == null -> LoadingView()
                list.isEmpty() -> EmptyView(if (selected == CAT_FAV) "Toque longo em um canal para favoritar." else "Nenhum canal nesta categoria.")
                else -> LazyColumn(Modifier.fadeEdges(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list) { c -> ChannelRowItem(state, c, programs.isNotEmpty()) { state.play(c, list) } }
                }
            }
        }
    }
}

@Composable
fun CategoryRow(c: Category, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), glass = selected, selected = selected, focusScale = 1.03f) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, style = MaterialTheme.typography.labelLarge, color = if (selected) Z.Text else Z.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (locked) Icon(Icons.Outlined.Lock, "Protegido", tint = Z.Faint, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
fun ChannelRowItem(state: AppState, c: Content, hasEpg: Boolean, onFocus: (() -> Unit)? = null, onClick: () -> Unit) {
    val data by state.store.data.collectAsState()
    val fav = remember(data, c) { state.store.isFavorite(c) }
    val (now, _) = if (hasEpg) state.epg.nowNext(c) else (null to null)
    Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), onLongClick = {
        state.store.toggleFavorite(c)
        state.showToast(if (fav) "Removido dos favoritos" else "Adicionado aos favoritos")
    }, focusScale = 1.02f, onFocus = onFocus) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(c, 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, color = Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now != null) {
                    Text(now.title, style = MaterialTheme.typography.labelMedium, color = Z.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressLine(now.progress(), Modifier.width(160.dp), height = 3.dp)
                }
            }
            if (c.catchupDays > 0) Text("REPLAY", style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
            if (fav) Icon(Icons.Filled.Favorite, "Favorito", tint = Z.Live)
        }
    }
}

@Composable
private fun ChannelPreview(state: AppState, c: Content, modifier: Modifier) {
    val list = state.epg.forChannel(c)
    val now = System.currentTimeMillis()
    val upcoming = list.filter { it.end > now }.take(6)
    val short by produceState(emptyList<com.zuvion.tv.data.EpgProgram>(), c) {
        value = if (list.isEmpty()) runCatching { state.repo.source?.shortEpg(c) }.getOrNull().orEmpty() else emptyList()
    }
    val progs = upcoming.ifEmpty { short }
    Column(modifier.glass(RoundedCornerShape(28.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(c, 52.dp)
            Text(c.title, style = MaterialTheme.typography.titleLarge, color = Z.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (progs.isEmpty()) Text("Sem guia de programação para este canal.", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
        val fmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
        progs.forEachIndexed { i, p ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${fmt.format(java.util.Date(p.start))} – ${fmt.format(java.util.Date(p.end))}", style = MaterialTheme.typography.labelMedium, color = if (i == 0) Z.Cyan else Z.Faint)
                Text(p.title, style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = Z.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (i == 0 && p.desc.isNotBlank()) Text(p.desc, style = MaterialTheme.typography.bodyMedium, color = Z.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
