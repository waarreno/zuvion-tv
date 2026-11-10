package com.zuvion.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zuvion.tv.data.Kind
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.Chip
import com.zuvion.tv.ui.components.EmptyView
import com.zuvion.tv.ui.components.LoadingView
import com.zuvion.tv.ui.components.PosterCard
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass

/** Filmes ou Séries: categorias + grade de cartazes. */
@Composable
fun VodScreen(state: AppState, kind: Kind) {
    val tv = LocalIsTv.current
    val (cats, selected, select) = rememberCategoryPicker(state, kind)
    val list = categoryItems(state, kind, selected)
    val title = if (kind == Kind.MOVIE) "Filmes" else "Séries"

    val grid: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            when {
                list == null -> LoadingView()
                list.isEmpty() -> EmptyView(if (selected == CAT_FAV) "Nada na sua lista ainda. Segure OK (ou toque longo) para adicionar." else "Nada nesta categoria.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (tv) 150.dp else 110.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(list) { m ->
                        val pr = state.store.progressOf(m)?.fraction
                        PosterCard(m, { state.open(m) }, width = if (tv) 150.dp else 110.dp, progress = pr, onLongClick = {
                            val was = state.store.isFavorite(m)
                            state.store.toggleFavorite(m)
                            state.showToast(if (was) "Removido da sua lista" else "Adicionado à sua lista")
                        })
                    }
                }
            }
        }
    }

    if (tv) {
        Row(Modifier.fillMaxSize().padding(top = 20.dp, end = 20.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(
                Modifier.width(230.dp).fillMaxHeight().glass(RoundedCornerShape(28.dp)),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { Text(title, style = MaterialTheme.typography.headlineMedium, color = Z.Text, modifier = Modifier.padding(10.dp)) }
                items(cats) { c -> CategoryRow(c, c.id == selected, state.needsPin(c.id, c.adult)) { select(c) } }
            }
            grid(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Z.Text, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats) { c -> Chip(c.name, c.id == selected, { select(c) }) }
            }
            grid(Modifier.weight(1f))
        }
    }
}
