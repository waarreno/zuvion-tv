package com.zuvion.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.zuvion.tv.BuildConfig
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.IconGlassButton
import com.zuvion.tv.ui.components.Logo
import com.zuvion.tv.ui.components.PillButton
import com.zuvion.tv.ui.components.Wordmark
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass

/** Pix estático (BR Code) para doações. */
const val PIX_KEY = "ac6ffef1-a34a-4877-be9d-83cc7ee355d4"
const val PIX_CODE = "00020126580014br.gov.bcb.pix0136ac6ffef1-a34a-4877-be9d-83cc7ee355d45204000053039865802BR5916WARRENO HENDRICK6015APARECIDA DE GO62120508ZUVIONTV63043A12"

/** Sobre e apoio: versão, créditos, copyright e doação via Pix. */
@Composable
fun AboutScreen(state: AppState) {
    val tv = LocalIsTv.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).coerceAtLeast(2026)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = if (tv) 64.dp else 20.dp, vertical = if (tv) 40.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconGlassButton(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", { state.pop() }, Modifier.focusRequester(fr), size = 48.dp)
            Text("Sobre e apoio", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Sobre
            Column(
                Modifier.widthIn(max = 560.dp).glass(RoundedCornerShape(28.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Logo(64.dp)
                    Column {
                        Wordmark(26)
                        Text("Sua TV, seu universo", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Versão ${BuildConfig.VERSION_NAME}", color = Z.Text, style = MaterialTheme.typography.titleMedium)
                Text("© $year Wárreno Hendrick. Todos os direitos reservados.", color = Z.Text, style = MaterialTheme.typography.bodyMedium)
                Text("O Zuvion TV é um player: não fornece canais nem conteúdo. Use apenas listas às quais você tem direito.",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                Text("Créditos", color = Z.Cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                Text("Capas e sinopses: TMDB. Notas: OMDb. Pular abertura: TheIntroDB. Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.",
                    color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                Text("Player: Media3 ExoPlayer e libVLC. Fontes: Inter e Sora (SIL Open Font License).",
                    color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                Text("Código aberto: github.com/waarreno/zuvion-tv", color = Z.Violet, style = MaterialTheme.typography.labelLarge)
            }

            // Apoio
            Column(
                Modifier.widthIn(max = 560.dp).glass(RoundedCornerShape(28.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Apoie o Zuvion TV", style = MaterialTheme.typography.titleLarge, color = Z.Text)
                Text("O Zuvion TV é gratuito e sem anúncios. Se ele faz parte da sua rotina, considere apoiar com uma doação via Pix, de qualquer valor.",
                    color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    val qr = remember { qrBitmap(PIX_CODE, 360) }
                    Box(Modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(10.dp)) {
                        Image(qr, "QR Code Pix para doação", Modifier.size(if (tv) 170.dp else 140.dp), filterQuality = FilterQuality.None)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pix · chave aleatória", color = Z.Text, style = MaterialTheme.typography.titleMedium)
                        Text(PIX_KEY, color = Z.Muted, style = MaterialTheme.typography.labelMedium)
                        Text("Wárreno Hendrick", color = Z.Faint, style = MaterialTheme.typography.labelMedium)
                        if (tv) Text("Aponte a câmera do app do seu banco para o QR Code.", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!tv) {
                    val clip = LocalClipboardManager.current
                    PillButton("Copiar Pix copia e cola", {
                        clip.setText(AnnotatedString(PIX_CODE)); state.showToast("Código Pix copiado")
                    }, Modifier.fillMaxWidth(), icon = Icons.Outlined.ContentCopy, primary = true)
                }
            }
        }
    }
}
