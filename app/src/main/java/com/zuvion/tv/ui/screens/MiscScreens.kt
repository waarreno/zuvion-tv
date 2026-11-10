package com.zuvion.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.Kind
import com.zuvion.tv.recording.RecordingService
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.ChannelLogo
import com.zuvion.tv.ui.components.EmptyView
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.IconGlassButton
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.PosterCard
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingsScreen(state: AppState) {
    val ctx = LocalContext.current
    val tv = LocalIsTv.current
    val active by RecordingService.active.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val files = remember(refresh, active) {
        RecordingService.recordingsDir(ctx).listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedByDescending { it.lastModified() }.orEmpty()
    }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (tv) 24.dp else 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Gravações", style = MaterialTheme.typography.headlineMedium, color = Z.Text) }
        if (active.isNotEmpty()) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.FiberManualRecord, null, tint = Z.Live)
                Text("Gravando agora: " + active.values.joinToString(), color = Z.Text, modifier = Modifier.weight(1f), maxLines = 2)
                PillButton("Parar", { RecordingService.stopAll(ctx) }, icon = Icons.Outlined.Stop)
            }
        }
        if (files.isEmpty() && active.isEmpty()) item {
            Text("Nenhuma gravação ainda. No player de um canal ao vivo, use o botão ● para gravar o programa atual.", color = Z.Muted, style = MaterialTheme.typography.bodyLarge)
        }
        items(files) { f ->
            Focusable({ playFile(state, f) }, Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), focusScale = 1.02f) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Z.Violet)
                    Column(Modifier.weight(1f)) {
                        Text(f.nameWithoutExtension.replace('_', ' '), color = Z.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmt.format(Date(f.lastModified()))} · ${f.length() / 1_048_576} MB", color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                    IconGlassButton(Icons.Outlined.Delete, "Apagar", { f.delete(); refresh++ }, size = 44.dp)
                }
            }
        }
    }
}

private fun playFile(state: AppState, f: File) {
    val c = Content(id = f.name, kind = Kind.RECORDING, name = f.nameWithoutExtension.replace('_', ' '), streamUrl = Uri.fromFile(f).toString())
    state.play(c, startMs = 0)
}

@Composable
fun SearchScreen(state: AppState) {
    val tv = LocalIsTv.current
    var q by remember { mutableStateOf("") }
    val results = remember(q) { state.repo.search(q) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!tv) runCatching { fr.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Buscar", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
        GlassField("Canal, filme ou série", q, { q = it }, Modifier.focusRequester(fr))
        if (q.length >= 2 && results.isEmpty()) EmptyView("Nada encontrado para “$q”.")
        val channels = results.filter { it.kind == Kind.LIVE }
        val vod = results.filter { it.kind != Kind.LIVE }
        LazyVerticalGrid(
            GridCells.Adaptive(if (tv) 150.dp else 110.dp),
            contentPadding = PaddingValues(bottom = if (tv) 24.dp else 110.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(channels) { c ->
                Focusable({ state.play(c, channels) }, Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChannelLogo(c, 56.dp)
                        Text(c.name, color = Z.Text, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            items(vod) { m -> PosterCard(m, { state.open(m) }, width = if (tv) 150.dp else 110.dp) }
        }
    }
}
