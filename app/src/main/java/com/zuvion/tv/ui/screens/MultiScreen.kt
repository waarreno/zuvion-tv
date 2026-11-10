package com.zuvion.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuvion.tv.data.Content
import com.zuvion.tv.player.ExoEngine
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.Screen
import com.zuvion.tv.ui.components.ChannelLogo
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.theme.Z

/** Multi-tela: até 4 canais ao mesmo tempo. O quadro em foco tem o áudio. */
@Composable
fun MultiScreen(state: AppState, s: Screen.Multi) {
    val slots = remember { mutableStateListOf<Content?>(s.first, null, null, null) }
    var audio by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<Int?>(null) }
    ImmersiveMode()

    Column(Modifier.fillMaxSize().background(Color.Black).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        MultiCell(
                            state, slots[i], i == audio,
                            onFocus = { audio = i },
                            onClick = { if (slots[i] == null || audio == i) picking = i else audio = i },
                            onLongClick = { picking = i }
                        )
                    }
                }
            }
        }
    }

    picking?.let { slot ->
        GlassDialog({ picking = null }) {
            Text("Escolha o canal do quadro ${slot + 1}", style = MaterialTheme.typography.titleLarge, color = Z.Text)
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                items(s.list) { c ->
                    Focusable({ slots[slot] = c; audio = slot; picking = null }, Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), focusScale = 1.02f) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChannelLogo(c, 36.dp)
                            Text(c.name, color = Z.Text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (slots[slot] != null) PillButton("Esvaziar quadro", { slots[slot] = null; picking = null }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MultiCell(state: AppState, c: Content?, hasAudio: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ctx = LocalContext.current
    Focusable(onClick, Modifier.fillMaxSize(), RoundedCornerShape(16.dp), onLongClick = onLongClick, focusScale = 1.0f, glass = c == null, onFocus = onFocus) {
        if (c == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Add, null, tint = Z.Muted, modifier = Modifier.padding(8.dp))
                Text("Escolher canal", color = Z.Muted, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            val engine = remember(c.key) { ExoEngine(ctx) }
            DisposableEffect(engine) { onDispose { engine.release() } }
            LaunchedEffect(engine) {
                val url = runCatching { state.repo.source?.resolveUrl(c) }.getOrNull() ?: c.streamUrl
                if (url != null) engine.play(url)
            }
            LaunchedEffect(hasAudio) { engine.setVolume(if (hasAudio) 1f else 0f) }
            engine.Surface(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .then(if (hasAudio) Modifier.border(1.dp, Z.Violet, RoundedCornerShape(12.dp)) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasAudio) Icon(Icons.Outlined.VolumeUp, "Com áudio", tint = Z.Violet)
                Text(c.name, color = Z.Text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
