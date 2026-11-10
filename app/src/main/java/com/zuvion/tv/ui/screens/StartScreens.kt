package com.zuvion.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.zuvion.tv.data.LoadStep
import com.zuvion.tv.data.StepState
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.Wordmark
import com.zuvion.tv.ui.theme.SpaceBackground
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass
import kotlin.math.cos
import kotlin.math.sin

/** Abertura animada: o planeta surge, o anel se desenha e a lua entra em órbita. */
@Composable
fun SplashScreen() {
    val planet = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val slogan = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        planet.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(700); text.animateTo(1f, tween(600)) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1050); slogan.animateTo(1f, tween(600)) }
    val orbit by rememberInfiniteTransition(label = "orbit").animateFloat(
        0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "moon"
    )
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        0.35f, 0.7f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glow"
    )

    SpaceBackground {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(170.dp)) {
                val s = size.width
                val c = Offset(s / 2, s / 2)
                val r = s * 0.24f * planet.value
                // brilho
                drawCircle(Brush.radialGradient(listOf(Z.VioletDeep.copy(alpha = glow * planet.value), Color.Transparent), c, s * 0.5f), s * 0.5f, c)
                // planeta
                drawCircle(Brush.linearGradient(listOf(Color(0xFFB9AEFF), Color(0xFF5BD8EE)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
                // anel (desenhado progressivamente) e lua em órbita
                val ringColor = if (Z.dark) Color.White else Z.Violet
                rotate(-24f, c) {
                    val w = s * 0.94f; val h = s * 0.32f
                    drawArc(
                        ringColor.copy(alpha = 0.9f), startAngle = 180f, sweepAngle = 360f * ring.value, useCenter = false,
                        topLeft = Offset(c.x - w / 2, c.y - h / 2), size = Size(w, h),
                        style = Stroke(width = s * 0.022f, cap = StrokeCap.Round)
                    )
                    if (ring.value > 0.98f) {
                        val a = Math.toRadians(orbit.toDouble())
                        val mx = c.x + (w / 2) * cos(a).toFloat()
                        val my = c.y + (h / 2) * sin(a).toFloat()
                        // a lua passa "atrás" do planeta na metade de cima da órbita
                        val behind = sin(a) < 0 && kotlin.math.abs(mx - c.x) < r
                        if (!behind) drawCircle(ringColor, s * 0.032f, Offset(mx, my))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.alpha(text.value).graphicsLayer { translationY = (1f - text.value) * 30f }) { Wordmark(38) }
            Spacer(Modifier.height(8.dp))
            Text(
                "Sua TV, seu universo", color = Z.Muted, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(slogan.value).graphicsLayer { translationY = (1f - slogan.value) * 20f }
            )
        }
    }
}

/** Tela de carregamento da lista, item por item, com "Sucesso" ao concluir cada grupo. */
@Composable
fun LoadProgressScreen(state: AppState, steps: List<LoadStep>) {
    SpaceBackground {
        Column(
            Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 520.dp).fillMaxWidth()
                .glass(RoundedCornerShape(32.dp), strong = true).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Wordmark(26)
            Text(
                "Atualizando “${state.store.activePlaylist?.name ?: "lista"}”",
                color = Z.Muted, style = MaterialTheme.typography.bodyMedium
            )
            steps.forEach { st -> StepRow(st) }
            val done = steps.count { it.state == StepState.OK || it.state == StepState.FAILED }
            Box(Modifier.fillMaxWidth().height(6.dp).background(Z.Tint.copy(alpha = 0.15f), CircleShape)) {
                Box(Modifier.fillMaxWidth(done / steps.size.coerceAtLeast(1).toFloat()).height(6.dp).background(Z.Accent, CircleShape))
            }
        }
    }
}

@Composable
private fun StepRow(st: LoadStep) {
    val icon = when (st.id) {
        "LIVE" -> Icons.Outlined.LiveTv; "MOVIE" -> Icons.Outlined.Movie
        "SERIES" -> Icons.Outlined.VideoLibrary; else -> Icons.Outlined.CalendarViewWeek
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = Z.Violet, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(st.label, color = Z.Text, style = MaterialTheme.typography.titleMedium)
            val sub = when (st.state) {
                StepState.WAITING -> "Aguardando"
                StepState.LOADING -> "Carregando…"
                StepState.OK -> if (st.count > 0) "${"%,d".format(st.count).replace(',', '.')} ${if (st.id == "EPG") "canais com guia" else "itens"}" else "Nenhum item"
                StepState.FAILED -> "Não disponível"
            }
            Text(sub, color = Z.Muted, style = MaterialTheme.typography.labelMedium)
        }
        when (st.state) {
            StepState.LOADING -> CircularProgressIndicator(color = Z.Violet, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            StepState.OK -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sucesso", color = Color(0xFF3FCF8E), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF3FCF8E), modifier = Modifier.size(22.dp))
            }
            StepState.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = Z.Live, modifier = Modifier.size(22.dp))
            StepState.WAITING -> Spacer(Modifier.size(22.dp))
        }
    }
}
