package com.zuvion.tv.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.zuvion.tv.remote.RemoteServer
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass

fun qrBitmap(text: String, size: Int = 360): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) 0xFF0B0D1F.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** QR Code fixo na tela (login da TV): o servidor fica ligado enquanto o cartão aparece. */
@Composable
fun PhoneQrCard(mode: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        url = RemoteServer.start(ctx.applicationContext, mode)
        onDispose { if (url != null) RemoteServer.stop() }
    }
    val u = url ?: return
    Row(
        modifier.glass(RoundedCornerShape(26.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = remember(u) { qrBitmap(u, 300) }
        Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(8.dp)) {
            Image(bmp, "QR Code para digitar pelo celular", Modifier.size(132.dp), filterQuality = FilterQuality.None)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mais fácil pelo celular", style = MaterialTheme.typography.titleMedium, color = Z.Text)
            Text("Escaneie com a câmera, preencha os dados no celular e a TV entra sozinha.", style = MaterialTheme.typography.bodyMedium, color = Z.Muted)
            Text("Celular e TV na mesma rede Wi-Fi.", style = MaterialTheme.typography.labelMedium, color = Z.Faint)
        }
    }
}

/**
 * Mostra um QR Code para abrir, no celular, uma página que preenche os campos da TV.
 * [mode] = "login" ou "search". O servidor só fica ligado enquanto este diálogo está aberto.
 */
@Composable
fun PhoneRemoteDialog(mode: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        url = RemoteServer.start(ctx.applicationContext, mode)
        failed = url == null
        onDispose { if (url != null) RemoteServer.stop() }
    }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    GlassDialog(onDismiss) {
        Text("Use o celular", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
        val u = url
        if (failed || u == null) {
            Text("A TV não está conectada a uma rede Wi-Fi ou cabo. Conecte e tente de novo.", color = Z.Muted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                val bmp = remember(u) { qrBitmap(u) }
                Box(Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(10.dp)) {
                    Image(bmp, "QR Code", Modifier.size(200.dp), filterQuality = FilterQuality.None)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Conecte o celular na mesma rede Wi-Fi da TV.", color = Z.Text, style = MaterialTheme.typography.bodyMedium)
                    Text("2. Aponte a câmera para o código.", color = Z.Text, style = MaterialTheme.typography.bodyMedium)
                    Text(if (mode == "search") "3. Digite a busca: o texto aparece aqui na hora."
                        else "3. Preencha os dados da lista e toque em Enviar.", color = Z.Text, style = MaterialTheme.typography.bodyMedium)
                    Text("Ou abra no navegador:", color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                    Text(u.substringBefore("&m="), color = Z.Violet, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        PillButton("Fechar", onDismiss, Modifier.fillMaxWidth().focusRequester(fr))
    }
}
