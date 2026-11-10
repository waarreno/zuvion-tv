package com.zuvion.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuvion.tv.data.Profile
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.Screen
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.Wordmark
import com.zuvion.tv.ui.theme.Z
import java.util.UUID

private val profileColors = listOf(0xFFFF8FA8, 0xFF8FE6F2, 0xFFB9AEFF, 0xFFFFC46B, 0xFF7EE0A8, 0xFF6E8BFF)

@Composable
fun ProfileAvatar(p: Profile, size: Int) {
    Box(Modifier.size(size.dp).background(Color(p.color), CircleShape), contentAlignment = Alignment.Center) {
        Text(p.name.take(1).uppercase(), color = Z.Ink, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
    }
}

@Composable
fun ProfilesScreen(state: AppState) {
    val data by state.store.data.collectAsState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }

    fun enter(p: Profile) {
        state.store.updateSettings { it.copy(activeProfileId = p.id) }
        state.unlocked.clear()
        state.replaceAll(Screen.Main)
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Wordmark(28)
        Text("Quem está assistindo?", style = MaterialTheme.typography.displaySmall, color = Z.Text, modifier = Modifier.padding(top = 18.dp, bottom = 32.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            items(data.profiles, key = { it.id }) { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Focusable(
                        onClick = {
                            // Sair do perfil infantil exige PIN, se houver
                            val cur = state.store.activeProfile
                            if (cur?.kids == true && !p.kids && state.store.settings.parentalPin != null && cur.id != p.id) pinFor = p else enter(p)
                        },
                        onLongClick = { editing = p },
                        shape = CircleShape, focusScale = 1.1f
                    ) { ProfileAvatar(p, 110) }
                    Text(p.name + if (p.kids) " · Kids" else "", color = Z.Text, style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Focusable({ editing = Profile(UUID.randomUUID().toString(), "", profileColors[data.profiles.size % profileColors.size]) }, Modifier.size(110.dp), CircleShape, focusScale = 1.1f) {
                        Icon(Icons.Outlined.Add, "Adicionar perfil", tint = Z.Text, modifier = Modifier.align(Alignment.Center).size(40.dp))
                    }
                    Text("Adicionar", color = Z.Muted, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text("Toque longo (ou segure OK) para editar um perfil.", color = Z.Faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 28.dp))
    }

    editing?.let { p -> ProfileEditor(state, p, canDelete = data.profiles.size > 1 && data.profiles.any { it.id == p.id }) { editing = null } }
    pinFor?.let { p ->
        PinDialog("PIN para sair do perfil infantil", { pinFor = null }) { pin ->
            (pin == state.store.settings.parentalPin).also { if (it) { pinFor = null; enter(p) } }
        }
    }
}

@Composable
fun ProfileEditor(state: AppState, initial: Profile, canDelete: Boolean, onDone: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var kids by remember { mutableStateOf(initial.kids) }
    var color by remember { mutableStateOf(initial.color) }
    GlassDialog(onDone) {
        Text(if (initial.name.isBlank()) "Novo perfil" else "Editar perfil", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
        GlassField("Nome", name, { name = it.take(20) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            profileColors.forEach { c ->
                Focusable({ color = c }, Modifier.size(40.dp), CircleShape, glass = false, focusScale = 1.15f) {
                    Box(Modifier.fillMaxSize().background(Color(c), CircleShape).then(if (c == color) Modifier.padding(4.dp) else Modifier))
                }
            }
        }
        Focusable({ kids = !kids }, shape = CircleShape) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Perfil infantil (oculta conteúdo adulto)", color = Z.Text, modifier = Modifier.weight(1f))
                Switch(kids, null)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Salvar", {
                if (name.isNotBlank()) { state.store.saveProfile(initial.copy(name = name.trim(), kids = kids, color = color)); onDone() }
            }, Modifier.weight(1f), primary = true)
            if (canDelete) PillButton("Excluir", { state.store.removeProfile(initial.id); onDone() }, Modifier.weight(1f))
            PillButton("Cancelar", onDone, Modifier.weight(1f))
        }
    }
}
