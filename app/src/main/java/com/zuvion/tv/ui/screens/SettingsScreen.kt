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
import com.zuvion.tv.ui.theme.fadeEdges
import com.zuvion.tv.ui.theme.glass
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import com.zuvion.tv.data.OPEN_EPG_BR
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
    var addingAddon by remember { mutableStateOf(false) }
    var removingAddon by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    val lib by state.repo.library.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().fadeEdges(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Configurações", style = MaterialTheme.typography.headlineMedium, color = Z.Text) }

        item {
            Section("Listas IPTV") {
                data.playlists.forEach { p ->
                    val active = p.id == (s.activePlaylistId ?: data.playlists.firstOrNull()?.id)
                    // Ativar, EPG e excluir são itens irmãos: o controle remoto chega em cada um com ◀ ▶.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Focusable({
                            state.store.updateSettings { it.copy(activePlaylistId = p.id) }
                            state.repo.open(); state.showToast("Lista “${p.name}” ativada")
                        }, Modifier.weight(1f), RoundedCornerShape(18.dp), glass = active, selected = active, focusScale = 1.01f) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (active) Icon(Icons.Outlined.CheckCircle, "Ativa", tint = Z.Cyan)
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = Z.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${typeLabel(p)} · ${p.url.removePrefix("http://").removePrefix("https://").trimEnd('/').substringBefore('/').take(40)}", color = Z.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        PillButton("EPG", { epgEdit = p })
                        IconGlassButton(Icons.Outlined.Delete, "Remover lista", { deleting = p }, size = 46.dp)
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
            Section("Continuar assistindo") {
                Text("Aparece em Continuar assistindo a partir de", color = Z.Muted, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10).forEach { v -> Chip("$v%", s.continueMinPct == v, { state.store.updateSettings { it.copy(continueMinPct = v) } }) }
                }
                Text("Considera assistido (e retira) a partir de", color = Z.Muted, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(85, 90, 95).forEach { v -> Chip("$v%", s.continueMaxPct == v, { state.store.updateSettings { it.copy(continueMaxPct = v) } }) }
                }
                Text("Para tirar um título, segure OK sobre ele na tela inicial, ou use “Remover de Continuar” nos detalhes.",
                    color = Z.Faint, style = MaterialTheme.typography.labelMedium)
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
                    "TMDB e TheIntroDB já vêm configurados. O OMDb tem limite diário de consultas, então precisa da sua própria chave (gratuita).",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                KeyRow("TMDB", s.tmdbKey, "themoviedb.org → Configurações → API", BuildConfig.TMDB_KEY.isNotEmpty()) { keyEdit = "tmdb" }
                KeyRow("OMDb", s.omdbKey, "omdbapi.com/apikey.aspx", false) { keyEdit = "omdb" }
                KeyRow("TheIntroDB", s.introDbKey, "theintrodb.org", true) { keyEdit = "intro" }
                ToggleRow("Pular aberturas automaticamente", s.autoSkipIntro) { v -> state.store.updateSettings { it.copy(autoSkipIntro = v) } }
            }
        }

        if (!tv) item {
            Focusable({ state.tab = com.zuvion.tv.ui.Tab.RECORDINGS }, Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), focusScale = 1.01f) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.RadioButtonChecked, null, tint = Z.Live)
                    Text("Gravações", style = MaterialTheme.typography.titleLarge, color = Z.Text, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Z.Muted)
                }
            }
        }

        item {
            Section("Organização") {
                Text("Com os gêneros do Zuvion, filmes e séries ficam agrupados pelos gêneros do TMDB (Ação, Comédia, Drama…) " +
                    "e os canais por tipo (Abertos, Esportes, Notícias, Infantil…). Desligado, aparecem as categorias do seu provedor.",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                ToggleRow("Organizar por gêneros", s.genreMode) { v ->
                    state.store.updateSettings { it.copy(genreMode = v) }
                    if (v) state.repo.enrichGenres()
                }
            }
        }

        item {
            Section("Outras fontes (addons do Stremio)") {
                Text("Addons no formato do Stremio que oferecem links diretos podem servir de fonte extra para filmes e episódios: " +
                    "use “Outras fontes” na tela do título. Links de torrent não são usados. O conteúdo vem do addon, não do Zuvion.",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                s.stremioAddons.forEach { u ->
                    val name by produceState(u.removePrefix("https://").substringBefore('/'), u) {
                        runCatching { com.zuvion.tv.data.Stremio.manifest(u).name }.getOrNull()?.let { value = it }
                    }
                    Focusable({ removingAddon = u }, Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), glass = false, focusScale = 1.01f) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name, color = Z.Text, style = MaterialTheme.typography.bodyLarge)
                                Text(u.removePrefix("https://").removePrefix("http://"), color = Z.Muted, style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Outlined.Close, "Remover", tint = Z.Muted)
                        }
                    }
                }
                PillButton("Adicionar addon", { addingAddon = true }, icon = Icons.Outlined.Add)
            }
        }

        item {
            Section("Perfis") {
                Text("Perfil atual: ${state.store.activeProfile?.name ?: "-"}", color = Z.Muted)
                PillButton("Gerenciar perfis", { state.push(Screen.Profiles) }, icon = Icons.Outlined.People)
            }
        }

        item {
            Section("Realce de imagem") {
                Text("Melhora vídeos em baixa qualidade: amplia para Full HD e aumenta a nitidez das bordas, sem estourar o contraste. " +
                    "Usa a placa de vídeo do aparelho; em aparelhos mais simples, prefira o modo suave. Também dá para trocar no player.",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("off" to "Desligado", "soft" to "Suave", "strong" to "Forte").forEach { (v, l) ->
                        Chip(l, s.enhance == v, { state.store.updateSettings { it.copy(enhance = v) } })
                    }
                }
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
            Section("Guia de programação") {
                Text("Complete o guia do seu provedor com um guia aberto e gratuito, que tem a grade completa de cerca de 260 canais brasileiros.",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                ToggleRow("Usar o guia aberto do Brasil", s.extraEpgUrl == OPEN_EPG_BR) { v ->
                    state.store.updateSettings { it.copy(extraEpgUrl = if (v) OPEN_EPG_BR else "") }
                    state.repo.reloadEpg()
                    state.showToast(if (v) "Carregando o guia aberto…" else "Guia aberto desligado")
                }
                Text("Para usar outro guia XMLTV na sua lista, toque em EPG em Listas IPTV.", color = Z.Faint, style = MaterialTheme.typography.labelMedium)
            }
        }

        item {
            Focusable({ state.push(Screen.About) }, Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), focusScale = 1.01f) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Logo(44.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Sobre e apoio", style = MaterialTheme.typography.titleLarge, color = Z.Text)
                        Text("Versão ${BuildConfig.VERSION_NAME}, créditos e doação via Pix", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Z.Muted)
                }
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

    deleting?.let { p ->
        GlassDialog({ deleting = null }) {
            Text("Remover “${p.name}”?", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
            Text("A lista sai do app neste aparelho. Favoritos e histórico dela também deixam de aparecer.", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val cancelFr = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { cancelFr.requestFocus() } }
                PillButton("Remover", {
                    state.store.removePlaylist(p.id)
                    state.repo.deleteCache(p.id)
                    deleting = null
                    if (state.store.data.value.playlists.isEmpty()) state.replaceAll(Screen.Setup) else state.repo.open()
                }, Modifier.weight(1f), primary = true)
                PillButton("Cancelar", { deleting = null }, Modifier.weight(1f).focusRequester(cancelFr))
            }
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

    if (addingAddon) AddAddonDialog(state, tv) { addingAddon = false }

    removingAddon?.let { u ->
        GlassDialog({ removingAddon = null }) {
            Text("Remover este addon?", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
            Text(u, color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Remover", {
                    state.store.updateSettings { it.copy(stremioAddons = it.stremioAddons - u) }
                    removingAddon = null; state.showToast("Addon removido")
                }, Modifier.weight(1f), primary = true)
                PillButton("Cancelar", { removingAddon = null }, Modifier.weight(1f))
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


/** Adiciona um addon pelo link do manifest (digitado ou enviado pelo celular). */
@Composable
private fun AddAddonDialog(state: AppState, tv: Boolean, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        com.zuvion.tv.remote.RemoteServer.events.collect { ev ->
            if (ev is com.zuvion.tv.remote.RemoteEvent.Search && ev.final) { url = ev.text.trim(); phone = false }
        }
    }
    if (phone) PhoneRemoteDialog("addon") { phone = false }
    fun save() {
        val u = com.zuvion.tv.data.Stremio.normalize(url)
        busy = true; error = null
        scope.launch {
            val r = runCatching { com.zuvion.tv.data.Stremio.manifest(u) }
            busy = false
            r.onSuccess { a ->
                when {
                    !a.hasStreams -> error = "“${a.name}” não oferece links de vídeo."
                    u in state.store.settings.stremioAddons -> error = "Este addon já foi adicionado."
                    else -> {
                        state.store.updateSettings { it.copy(stremioAddons = it.stremioAddons + u) }
                        state.showToast("Addon “${a.name}” adicionado"); onDismiss()
                    }
                }
            }.onFailure { error = "Não foi possível ler o addon. Confira o link (termina em manifest.json)." }
        }
    }
    GlassDialog(onDismiss) {
        Text("Adicionar addon", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
        Text("Cole o link de instalação do addon (manifest.json). O link do botão “Instalar” do Stremio (stremio://…) também serve.",
            color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
        GlassField("https://…/manifest.json", url, { url = it; error = null }, last = true)
        error?.let { Text(it, color = Z.Live) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(if (busy) "Verificando…" else "Adicionar", { if (!busy && url.isNotBlank()) save() }, Modifier.weight(1f), primary = true)
            if (tv) PillButton("Pelo celular", { phone = true }, Modifier.weight(1f), icon = Icons.Outlined.QrCode2)
            else PillButton("Cancelar", onDismiss, Modifier.weight(1f))
        }
    }
}
