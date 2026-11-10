package com.zuvion.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuvion.tv.BuildConfig
import com.zuvion.tv.data.Playlist
import com.zuvion.tv.data.SourceType
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.Screen
import com.zuvion.tv.ui.components.Chip
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.IconGlassButton
import com.zuvion.tv.ui.components.Logo
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.Wordmark
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Z.Text)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Focusable({ onChange(!value) }, Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), glass = false, focusScale = 1.01f) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Z.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(value, null)
        }
    }
}

private fun typeLabel(p: Playlist) = when (p.type) {
    SourceType.XTREAM -> "Xtream"; SourceType.M3U_URL -> "M3U"; SourceType.M3U_FILE -> "Arquivo"; SourceType.STALKER -> "MAC"
}

@Composable
fun SettingsScreen(state: AppState) {
    val tv = LocalIsTv.current
    val data by state.store.data.collectAsState()
    val s = data.settings
    val scope = rememberCoroutineScope()
    var pinDialog by remember { mutableStateOf<String?>(null) } // "set" | "verify-remove" | "verify-change"
    var newPin by remember { mutableStateOf("") }
    var epgEdit by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (tv) 24.dp else 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Ajustes", style = MaterialTheme.typography.headlineMedium, color = Z.Text) }

        item {
            Section("Listas IPTV") {
                data.playlists.forEach { p ->
                    val active = p.id == (s.activePlaylistId ?: data.playlists.firstOrNull()?.id)
                    Focusable({
                        state.store.updateSettings { it.copy(activePlaylistId = p.id) }
                        state.repo.reload(); state.showToast("Lista “${p.name}” ativada")
                    }, Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), glass = active, selected = active, focusScale = 1.01f) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (active) Icon(Icons.Outlined.CheckCircle, "Ativa", tint = Z.Cyan)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = Z.Text, style = MaterialTheme.typography.titleMedium)
                                Text("${typeLabel(p)} · ${p.url.substringAfterLast('/').ifBlank { p.url }.take(40)}", color = Z.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            PillButton("EPG", { epgEdit = p })
                            IconGlassButton(Icons.Outlined.Delete, "Remover lista", {
                                state.store.removePlaylist(p.id)
                                if (state.store.data.value.playlists.isEmpty()) state.replaceAll(Screen.Setup) else state.repo.reload()
                            }, size = 44.dp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Adicionar lista", { state.push(Screen.Setup) }, icon = Icons.Outlined.Add, primary = true)
                    PillButton("Recarregar", { state.repo.reload(); state.showToast("Recarregando…") }, icon = Icons.Outlined.Refresh)
                }
            }
        }

        item {
            Section("Perfis") {
                Text("Perfil atual: ${state.store.activeProfile?.name ?: "-"}", color = Z.Muted)
                PillButton("Gerenciar perfis", { state.push(Screen.Profiles) }, icon = Icons.Outlined.People)
            }
        }

        item {
            Section("Player") {
                Text("Motor de vídeo padrão", color = Z.Muted, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("ExoPlayer (leve)", s.engine == "exo", { state.store.updateSettings { it.copy(engine = "exo") } })
                    Chip("VLC (compatível)", s.engine == "vlc", { state.store.updateSettings { it.copy(engine = "vlc") } })
                }
                ToggleRow("Se o ExoPlayer falhar, tentar no VLC automaticamente", s.autoFallbackVlc) { v -> state.store.updateSettings { it.copy(autoFallbackVlc = v) } }
                Text("Formato dos canais ao vivo (Xtream)", color = Z.Muted, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("MPEG-TS", s.liveFormat == "ts", { state.store.updateSettings { it.copy(liveFormat = "ts") }; state.repo.reload() })
                    Chip("HLS (m3u8)", s.liveFormat == "m3u8", { state.store.updateSettings { it.copy(liveFormat = "m3u8") }; state.repo.reload() })
                }
            }
        }

        item {
            Section("Controle parental") {
                Text(if (s.parentalPin == null) "Nenhum PIN definido. Com PIN, categorias adultas pedem senha." else "PIN ativo: categorias adultas pedem senha.", color = Z.Muted)
                ToggleRow("Ocultar categorias adultas", s.hideAdult) { v ->
                    if (!v && s.parentalPin != null) pinDialog = "verify-unhide" else state.store.updateSettings { it.copy(hideAdult = v) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.parentalPin == null) PillButton("Definir PIN", { pinDialog = "set" }, primary = true)
                    else {
                        PillButton("Alterar PIN", { pinDialog = "verify-change" })
                        PillButton("Remover PIN", { pinDialog = "verify-remove" })
                    }
                }
            }
        }

        item {
            Section("Atualizações") {
                Text("Versão instalada: ${BuildConfig.VERSION_NAME}", color = Z.Muted)
                PillButton("Verificar atualizações", { scope.launch { state.updater.check(manual = true) } }, icon = Icons.Outlined.SystemUpdate, primary = true)
            }
        }

        item {
            Section("Sobre") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Logo(48.dp)
                    Column {
                        Wordmark(20)
                        Text("Sua TV, seu universo", color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text("O Zuvion TV é um player: não fornece canais nem conteúdo. Use apenas listas às quais você tem direito.", color = Z.Faint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    when (pinDialog) {
        "set" -> GlassDialog({ pinDialog = null }) {
            Text("Novo PIN", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
            GlassField("4 dígitos", newPin, { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it }, password = true,
                keyboard = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
            PillButton("Salvar", {
                if (newPin.length == 4) {
                    val pin = newPin
                    state.store.updateSettings { it.copy(parentalPin = pin) }
                    newPin = ""; pinDialog = null; state.showToast("PIN definido")
                }
            }, Modifier.fillMaxWidth(), primary = true)
        }
        "verify-change", "verify-remove", "verify-unhide" -> PinDialog("Digite o PIN atual", { pinDialog = null }) { pin ->
            val ok = pin == s.parentalPin
            if (ok) {
                when (pinDialog) {
                    "verify-change" -> pinDialog = "set"
                    "verify-remove" -> { state.store.updateSettings { it.copy(parentalPin = null) }; pinDialog = null }
                    else -> { state.store.updateSettings { it.copy(hideAdult = false) }; pinDialog = null }
                }
            }
            ok
        }
    }

    epgEdit?.let { p ->
        var url by remember(p.id) { mutableStateOf(p.epgUrl) }
        GlassDialog({ epgEdit = null }) {
            Text("EPG da lista “${p.name}”", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
            Text("Deixe em branco para usar o EPG do próprio servidor.", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
            GlassField("URL XMLTV (.xml ou .xml.gz)", url, { url = it })
            PillButton("Salvar", {
                state.store.update { d -> d.copy(playlists = d.playlists.map { if (it.id == p.id) it.copy(epgUrl = url.trim()) else it }) }
                epgEdit = null; state.repo.reload()
            }, Modifier.fillMaxWidth(), primary = true)
        }
    }
}
