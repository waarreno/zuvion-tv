package com.zuvion.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.ProgressLine
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass
import com.zuvion.tv.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x99040610)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth()
                    .background(Z.Panel, RoundedCornerShape(32.dp))
                    .glass(RoundedCornerShape(32.dp), strong = true)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) { content() }
        }
    }
}

/** Pede o PIN do controle parental. */
@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    GlassDialog(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Z.Text)
        GlassField("PIN (4 dígitos)", pin, { if (it.length <= 4 && it.all(Char::isDigit)) { pin = it; wrong = false } },
            Modifier.focusRequester(fr), password = true, keyboard = KeyboardType.NumberPassword)
        if (wrong) Text("PIN incorreto", color = Z.Live)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Confirmar", { if (!onPin(pin)) { wrong = true; pin = "" } }, Modifier.weight(1f), primary = true)
            PillButton("Cancelar", onDismiss, Modifier.weight(1f))
        }
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

@Composable
fun UpdateDialog(state: AppState, upd: UpdateState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = state.updater
    GlassDialog({ if (upd !is UpdateState.Downloading) updater.dismiss() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(56.dp).background(Z.Accent, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Download, null, tint = Z.Ink, modifier = Modifier.size(28.dp))
            }
            Column {
                val title = when (upd) {
                    is UpdateState.UpToDate -> "Você está na versão mais recente"
                    is UpdateState.Failed -> "Atualização"
                    is UpdateState.ReadyToInstall -> "Pronto para instalar"
                    is UpdateState.Downloading -> "Baixando atualização…"
                    else -> "Nova versão disponível"
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Z.Text)
                val info = when (upd) {
                    is UpdateState.Available -> upd.info
                    is UpdateState.Downloading -> upd.info
                    is UpdateState.ReadyToInstall -> upd.info
                    is UpdateState.Failed -> upd.info
                    else -> null
                }
                val sub = if (info != null) "Zuvion TV ${info.version} · você tem a ${updater.currentVersion}" +
                    (if (info.sizeBytes > 0) " · ${info.sizeBytes / 1_048_576} MB" else "")
                else "Versão ${updater.currentVersion}"
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = Z.Muted)
            }
        }
        when (upd) {
            is UpdateState.Available -> {
                if (upd.info.notes.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 220.dp).background(Z.Tint.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("NOVIDADES", style = MaterialTheme.typography.labelSmall, color = Z.Cyan)
                        Text(upd.info.notes.replace("**", "").replace("## ", "").replace("- ", "• "), style = MaterialTheme.typography.bodyMedium, color = Z.Text)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Atualizar agora", { scope.launch { updater.download(upd.info) } }, Modifier.weight(1f), primary = true)
                    PillButton("Mais tarde", {
                        state.store.updateSettings { it.copy(skippedVersion = upd.info.version) }
                        updater.dismiss()
                    }, Modifier.weight(1f))
                }
                Text("O Android vai pedir para confirmar a instalação. Seus logins e favoritos são mantidos.",
                    style = MaterialTheme.typography.labelMedium, color = Z.Muted)
            }
            is UpdateState.Downloading -> {
                ProgressLine(upd.progress, height = 8.dp)
                Text("${(upd.progress * 100).toInt()}%", color = Z.Muted)
            }
            is UpdateState.ReadyToInstall -> {
                val canInstall = updater.canInstall()
                if (!canInstall) Text("Permita que o Zuvion TV instale apps (uma única vez) e volte aqui.", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(if (canInstall) "Instalar" else "Permitir instalação", {
                        if (updater.canInstall()) ctx.startActivity(updater.installIntent(upd.file))
                        else ctx.startActivity(updater.openInstallPermission())
                    }, Modifier.weight(1f), primary = true)
                    PillButton("Fechar", { updater.dismiss() }, Modifier.weight(1f))
                }
            }
            is UpdateState.Failed -> {
                Text(upd.message, color = Z.Live, style = MaterialTheme.typography.bodyMedium)
                PillButton("Fechar", { updater.dismiss() }, Modifier.fillMaxWidth())
            }
            else -> PillButton("OK", { updater.dismiss() }, Modifier.fillMaxWidth(), primary = true)
        }
    }
}
