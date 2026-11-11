package com.zuvion.tv.ui.screens

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.border

import androidx.compose.foundation.background

import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.ui.graphics.SolidColor

import androidx.compose.foundation.text.BasicTextField

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.runtime.LaunchedEffect
import com.zuvion.tv.remote.RemoteEvent
import com.zuvion.tv.remote.RemoteServer
import com.zuvion.tv.ui.theme.LocalIsTv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zuvion.tv.data.Kind
import com.zuvion.tv.data.Playlist
import com.zuvion.tv.data.SourceType
import com.zuvion.tv.source.M3uSource
import com.zuvion.tv.source.StalkerSource
import com.zuvion.tv.source.XtreamSource
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.Screen
import com.zuvion.tv.ui.components.Chip
import com.zuvion.tv.ui.components.Logo
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.Wordmark
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.random.Random

@Composable
fun GlassField(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, password: Boolean = false, placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    last: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    // Campo de vidro próprio: rótulo acima (como no protótipo) e sem o recorte do Material,
    // que deixava uma faixa clara atrás do texto em algumas telas.
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, color = if (focused) Z.Violet else Z.Muted)
        BasicTextField(
            value = value, onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Z.Text),
            cursorBrush = SolidColor(Z.Violet),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password && keyboard == KeyboardType.Text) KeyboardType.Password else keyboard,
                imeAction = if (last) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Down) },
                onDone = { focus.clearFocus(); onDone?.invoke() }
            ),
            // No controle remoto, ▲/▼ saem do campo (alguns Google TV prendem o foco no texto).
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clip(shape)
                        .background(Z.Tint.copy(alpha = if (focused) 0.09f else 0.05f))
                        .border(if (focused) 2.dp else 1.dp, if (focused) Z.Violet else Z.Tint.copy(alpha = 0.16f), shape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = Z.Faint, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    inner()
                }
            }
        )
    }
}

private fun randomMac() = "00:1A:79:" + (1..3).joinToString(":") { "%02X".format(Random.nextInt(256)) }

@Composable
fun SetupScreen(state: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(SourceType.XTREAM) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf(randomMac()) }
    var epg by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    val id = remember { UUID.randomUUID().toString() }
    val tv = LocalIsTv.current
    var phone by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(ctx.filesDir, "playlists").apply { mkdirs() }
                    val out = File(dir, "$id.m3u")
                    ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out.path
                }.getOrNull()
            }
            if (file == null) errMsg = "Não foi possível ler o arquivo"
        }
    }

    fun submit() {
        errMsg = null
        val p = Playlist(
            id = id, name = name.ifBlank { if (type == SourceType.M3U_FILE) "Lista local" else "Minha TV" }, type = type,
            url = if (type == SourceType.M3U_FILE) file.orEmpty() else url.trim(),
            username = user.trim(), password = pass.trim(), mac = mac.trim(), epgUrl = epg.trim()
        )
        when {
            type == SourceType.M3U_FILE && file == null -> { errMsg = "Escolha um arquivo .m3u"; return }
            type != SourceType.M3U_FILE && p.url.isBlank() -> { errMsg = "Informe o endereço"; return }
            type == SourceType.XTREAM && (p.username.isBlank() || p.password.isBlank()) -> { errMsg = "Informe usuário e senha"; return }
            type == SourceType.STALKER && p.mac.length < 17 -> { errMsg = "MAC inválido"; return }
        }
        loading = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (type) {
                        SourceType.XTREAM -> XtreamSource(p, "ts").authenticate()
                        SourceType.STALKER -> StalkerSource(p).authenticate()
                        else -> {
                            val s = M3uSource(ctx, p)
                            if (s.items(Kind.LIVE).isEmpty() && s.items(Kind.MOVIE).isEmpty() && s.items(Kind.SERIES).isEmpty())
                                throw IllegalStateException("A lista está vazia")
                        }
                    }
                }
                state.store.addPlaylist(p)
                state.repo.reload()
                state.replaceAll(Screen.Main)
            } catch (e: Throwable) {
                errMsg = e.message ?: "Falha ao conectar"
            } finally {
                loading = false
            }
        }
    }

    // Dados digitados no celular (QR Code): preenche e já tenta entrar.
    LaunchedEffect(Unit) {
        RemoteServer.events.collect { ev ->
            if (ev !is RemoteEvent.Login || loading) return@collect
            val f = ev.fields
            type = runCatching { SourceType.valueOf(f["type"].orEmpty()) }.getOrDefault(SourceType.XTREAM)
            f["name"]?.takeIf { it.isNotBlank() }?.let { name = it }
            url = f["url"].orEmpty().trim()
            user = f["user"].orEmpty().trim()
            pass = f["pass"].orEmpty().trim()
            f["mac"]?.trim()?.uppercase()?.takeIf { it.length >= 17 }?.let { mac = it }
            epg = f["epg"].orEmpty().trim()
            phone = false
            submit()
        }
    }
    if (phone) PhoneRemoteDialog("login") { phone = false }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        val wide = maxWidth > 760.dp
        val form: @Composable () -> Unit = {
            Column(
                Modifier.glass(RoundedCornerShape(28.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(SourceType.XTREAM to "Xtream", SourceType.M3U_URL to "M3U", SourceType.STALKER to "MAC", SourceType.M3U_FILE to "Arquivo")
                        .forEach { (t, l) -> Chip(l, type == t, { type = t; errMsg = null }) }
                }
                GlassField("Nome da lista", name, { name = it }, placeholder = "Ex.: Minha TV")
                when (type) {
                    SourceType.XTREAM -> {
                        GlassField("Servidor (URL)", url, { url = it }, placeholder = "http://servidor:porta", keyboard = KeyboardType.Uri)
                        GlassField("Usuário", user, { user = it })
                        GlassField("Senha", pass, { pass = it }, password = true, last = true, onDone = { submit() })
                    }
                    SourceType.M3U_URL -> {
                        GlassField("URL da lista M3U", url, { url = it }, placeholder = "http://…/lista.m3u", keyboard = KeyboardType.Uri)
                        GlassField("URL do EPG (XMLTV, opcional)", epg, { epg = it }, keyboard = KeyboardType.Uri)
                    }
                    SourceType.STALKER -> {
                        GlassField("URL do portal", url, { url = it }, placeholder = "http://portal:porta/c/", keyboard = KeyboardType.Uri)
                        GlassField("Endereço MAC", mac, { mac = it.uppercase() })
                        Text("Cadastre este MAC no seu provedor.", color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                    SourceType.M3U_FILE -> {
                        PillButton(if (file == null) "Escolher arquivo .m3u" else "Arquivo selecionado ✓", { picker.launch(arrayOf("*/*")) }, icon = Icons.Outlined.FolderOpen)
                        GlassField("URL do EPG (XMLTV, opcional)", epg, { epg = it }, keyboard = KeyboardType.Uri)
                    }
                }
                if (errMsg != null) Text(errMsg!!, color = Z.Live, style = MaterialTheme.typography.bodyMedium)
                PillButton(if (loading) "Conectando…" else "Entrar", { if (!loading) submit() }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Login, primary = true)
                if (tv && !wide && type != SourceType.M3U_FILE) PillButton("Digitar pelo celular (QR Code)", { phone = true }, Modifier.fillMaxWidth(), icon = Icons.Outlined.QrCode2)
                if (state.stack.size > 1) PillButton("Cancelar", { state.pop() }, Modifier.fillMaxWidth())
            }
        }
        val brand: @Composable () -> Unit = {
            Column(horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Logo(if (wide) 96.dp else 72.dp)
                Wordmark(if (wide) 40 else 30)
                Text("Sua TV, seu universo", color = Z.Muted, style = MaterialTheme.typography.bodyLarge)
                if (wide) {
                    Spacer(Modifier.height(12.dp))
                    Text("Adicione sua lista IPTV: Xtream Codes, M3U, portal MAC ou um arquivo do aparelho.",
                        color = Z.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(380.dp))
                    if (tv) { Spacer(Modifier.height(14.dp)); PhoneQrCard("login", Modifier.width(420.dp)) }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Column(Modifier.weight(0.9f)) { brand() }
                Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState())) { form() }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                brand()
                form()
            }
        }
    }
}
