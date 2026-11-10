package com.zuvion.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/** Pix estático (BR Code) para doações. */
private const val PIX_KEY = "ac6ffef1-a34a-4877-be9d-83cc7ee355d4"
private const val PIX_CODE = "00020126580014br.gov.bcb.pix0136ac6ffef1-a34a-4877-be9d-83cc7ee355d45204000053039865802BR5916WARRENO HENDRICK6015APARECIDA DE GO62120508ZUVIONTV63043A12"

@Composable
private fun KeyRow(label: String, value: String, hint: String, builtIn: Boolean, onClick: () -> Unit) {
    Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), glass = false, focusScale = 1.01f) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = Z.Text, style = MaterialTheme.typography.bodyLarge)
                Text(when {
                    value.isNotBlank() -> "Sua chave ••••" + value.takeLast(4)
                    builtIn -> "Ativa (chave do app) · toque para usar a sua"
                    else -> "Não configurada · $hint"
                }, color = Z.Muted, style = MaterialTheme.typography.labelMedium)
            }
            val ok = value.isNotBlank() || builtIn
            Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Add, null, tint = if (ok) Z.Cyan else Z.Muted)
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
    var keyEdit by remember { mutableStateOf<String?>(null) } // tmdb | omdb | intro
    val lib by state.repo.library.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Configurações", style = MaterialTheme.typography.headlineMedium, color = Z.Text) }

        item {
            Section("Listas IPTV") {
                data.playlists.forEach { p ->
                    val active = p.id == (s.activePlaylistId ?: data.playlists.firstOrNull()?.id)
                    Focusable({
                        state.store.updateSettings { it.copy(activePlaylistId = p.id) }
                        state.repo.open(); state.showToast("Lista “${p.name}” ativada")
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
                                state.repo.deleteCache(p.id)
                                if (state.store.data.value.playlists.isEmpty()) state.replaceAll(Screen.Setup) else state.repo.open()
                            }, size = 44.dp)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Atualizar lista agora", { state.repo.refresh() }, icon = Icons.Outlined.Refresh, primary = true)
                    PillButton("Adicionar lista", { state.push(Screen.Setup) }, icon = Icons.Outlined.Add)
                }
                val updated = lib.updatedAt.takeIf { it > 0 }?.let {
                    SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(Date(it))
                }
                Text("Última atualização: ${updated ?: "-"}", color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                Text("Atualizar automaticamente", color = Z.Muted, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(12 to "12 h", 24 to "24 h", 72 to "3 dias", 0 to "Só manual").forEach { (h, l) ->
                        Chip(l, s.refreshHours == h, { state.store.updateSettings { it.copy(refreshHours = h) } })
                    }
                }
            }
        }

        item {
            Section("Aparência") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("system" to "Automático", "dark" to "Escuro", "light" to "Claro").forEach { (v, l) ->
                        Chip(l, s.theme == v, { state.store.updateSettings { it.copy(theme = v) } })
                    }
                }
                Text("Automático segue o tema do aparelho. O player fica sempre escuro.", color = Z.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }

        item {
            Section("Capas e sinopses") {
                Text("Capas, sinopses e elenco vêm do TMDB; o servidor IPTV só é usado se o TMDB não encontrar. " +
                    "O OMDb acrescenta notas do IMDb e Rotten Tomatoes. O TheIntroDB mostra o botão “Pular abertura”. " +
                    "O app já vem com chaves; se quiser, use as suas.",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                KeyRow("TMDB", s.tmdbKey, "themoviedb.org → Configurações → API", BuildConfig.TMDB_KEY.isNotEmpty()) { keyEdit = "tmdb" }
                KeyRow("OMDb", s.omdbKey, "omdbapi.com/apikey.aspx", BuildConfig.OMDB_KEY.isNotEmpty()) { keyEdit = "omdb" }
                KeyRow("TheIntroDB", s.introDbKey, "theintrodb.org", true) { keyEdit = "intro" }
                ToggleRow("Pular aberturas automaticamente", s.autoSkipIntro) { v -> state.store.updateSettings { it.copy(autoSkipIntro = v) } }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("ExoPlayer (leve)", s.engine == "exo", { state.store.updateSettings { it.copy(engine = "exo") } })
                    Chip("VLC (compatível)", s.engine == "vlc", { state.store.updateSettings { it.copy(engine = "vlc") } })
                }
                ToggleRow("Se o ExoPlayer falhar, tentar no VLC automaticamente", s.autoFallbackVlc) { v -> state.store.updateSettings { it.copy(autoFallbackVlc = v) } }
                Text("Formato dos canais ao vivo (Xtream)", color = Z.Muted, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("MPEG-TS", s.liveFormat == "ts", { if (s.liveFormat != "ts") { state.store.updateSettings { it.copy(liveFormat = "ts") }; state.repo.refresh() } })
                    Chip("HLS (m3u8)", s.liveFormat == "m3u8", { if (s.liveFormat != "m3u8") { state.store.updateSettings { it.copy(liveFormat = "m3u8") }; state.repo.refresh() } })
                }
            }
        }

        item {
            Section("Controle parental") {
                Text(if (s.parentalPin == null) "Nenhum PIN definido. Com PIN, categorias adultas pedem senha." else "PIN ativo: categorias adultas pedem senha.", color = Z.Muted)
                ToggleRow("Ocultar categorias adultas", s.hideAdult) { v ->
                    if (!v && s.parentalPin != null) pinDialog = "verify-unhide" else state.store.updateSettings { it.copy(hideAdult = v) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Section("Apoie o Zuvion TV") {
                Text("O Zuvion TV é gratuito e sem anúncios. Se ele faz parte da sua rotina, considere apoiar com uma doação via Pix, de qualquer valor.",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val qr = remember { qrBitmap(PIX_CODE, 360) }
                    Box(Modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(10.dp)) {
                        Image(qr, "QR Code Pix para doação", Modifier.size(if (tv) 170.dp else 150.dp), filterQuality = FilterQuality.None)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pix · chave aleatória", color = Z.Text, style = MaterialTheme.typography.titleMedium)
                        Text(PIX_KEY, color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                        Text("Wárreno Hendrick", color = Z.Faint, style = MaterialTheme.typography.labelMedium)
                        if (!tv) {
                            val clip = LocalClipboardManager.current
                            PillButton("Copiar Pix copia e cola", {
                                clip.setText(AnnotatedString(PIX_CODE)); state.showToast("Código Pix copiado")
                            }, icon = Icons.Outlined.ContentCopy, primary = true)
                        } else {
                            Text("Aponte a câmera do app do seu banco para o QR Code.", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
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
                Text("Versão ${BuildConfig.VERSION_NAME}", color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                Text("O Zuvion TV é um player: não fornece canais nem conteúdo. Use apenas listas às quais você tem direito.", color = Z.Faint, style = MaterialTheme.typography.labelMedium)
                Text("Capas e sinopses: TMDB, OMDb e TheIntroDB. Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.", color = Z.Faint, style = MaterialTheme.typography.labelMedium)
                Text("© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).coerceAtLeast(2026)} Wárreno Hendrick. Todos os direitos reservados.", color = Z.Text, style = MaterialTheme.typography.labelLarge)
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

    keyEdit?.let { k ->
        val (title, current) = when (k) {
            "tmdb" -> "Chave do TMDB" to s.tmdbKey
            "omdb" -> "Chave do OMDb" to s.omdbKey
            else -> "Chave do TheIntroDB" to s.introDbKey
        }
        var v by remember(k) { mutableStateOf(current) }
        GlassDialog({ keyEdit = null }) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Z.Text)
            Text(when (k) {
                "tmdb" -> "Aceita a “Chave da API” (v3) ou o “Token de leitura” (v4). Cadastro gratuito em themoviedb.org."
                "omdb" -> "Chave gratuita em omdbapi.com (até 1.000 consultas por dia)."
                else -> "Opcional: o TheIntroDB funciona sem chave."
            }, color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
            GlassField("Chave", v, { v = it.trim() }, last = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Salvar", {
                    val value = v.trim()
                    state.store.updateSettings {
                        when (k) { "tmdb" -> it.copy(tmdbKey = value); "omdb" -> it.copy(omdbKey = value); else -> it.copy(introDbKey = value) }
                    }
                    keyEdit = null; state.showToast(if (value.isEmpty()) "Chave removida" else "Chave salva")
                }, Modifier.weight(1f), primary = true)
                if (current.isNotEmpty()) PillButton("Remover", { v = "" }, Modifier.weight(1f))
            }
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
                epgEdit = null; state.repo.refresh()
            }, Modifier.fillMaxWidth(), primary = true)
        }
    }
}
