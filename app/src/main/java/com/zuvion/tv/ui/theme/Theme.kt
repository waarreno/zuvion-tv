package com.zuvion.tv.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.zuvion.tv.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cores do app. Leem [dark] (estado do Compose), então trocar o tema
 * recompõe tudo sem precisar passar cores por parâmetro.
 */
object Z {
    var dark by mutableStateOf(true)
    private fun c(d: Long, l: Long) = Color(if (dark) d else l)

    val Space get() = c(0xFF060814, 0xFFEEF0FA)
    val SpaceDeep get() = c(0xFF03040B, 0xFFE3E6F5)
    val Text get() = c(0xFFF4F5FF, 0xFF12142A)
    val Muted get() = c(0xFFBDC1E3, 0xFF4B4F72)
    val Faint get() = c(0xFF8C90B3, 0xFF7D81A3)
    val Violet get() = c(0xFFB9AEFF, 0xFF6246EA)
    val VioletDeep = Color(0xFF7C60FF)
    val Cyan get() = c(0xFF8FE6F2, 0xFF0B8EAA)
    val CyanDeep = Color(0xFF26C4E4)
    val Live = Color(0xFFFF4D63)
    val Ink = Color(0xFF0B0D1F)
    /** Cor de contraste (branco no escuro, tinta no claro) para véus e trilhos. */
    val Tint get() = if (dark) Color.White else Color(0xFF1B1E3C)
    /** Botão principal: branco no escuro, tinta no claro. */
    val Primary get() = c(0xFFF4F5FF, 0xFF1B1E3C)
    val OnPrimary get() = c(0xFF0B0D1F, 0xFFFFFFFF)
    val Focus get() = if (dark) Color.White else Color(0xFF6246EA)
    val GlassHi get() = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.82f)
    val GlassLo get() = if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.55f)
    val Panel get() = if (dark) Color(0xF0101429) else Color(0xF5F7F8FF)
    val Accent get() = Brush.linearGradient(listOf(Violet, if (dark) Color(0xFF5BD8EE) else Color(0xFF16A9C8)))

    val palette = listOf(
        Color(0xFF6E5BFF), Color(0xFF18A9C9), Color(0xFFD9468A), Color(0xFFE29A2E),
        Color(0xFF3FBF8A), Color(0xFF5A6CFF), Color(0xFFC2417A), Color(0xFF1E7F7A)
    )
    fun colorFor(seed: String): Color = palette[(seed.hashCode() and 0x7fffffff) % palette.size]
}

val LocalIsTv = staticCompositionLocalOf { false }

/** true quando a tela está coberta por outra (viva por baixo): seus itens não recebem foco. */
val LocalCovered = androidx.compose.runtime.compositionLocalOf { false }

/** Último item focado de uma tela, para devolver o foco a ele ao voltar (sem fixar itens de listas). */
class LayerFocus { var last: androidx.compose.ui.focus.FocusRequester? = null }
val LocalLayerFocus = staticCompositionLocalOf<LayerFocus?> { null }

fun isTvDevice(ctx: Context): Boolean {
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        !ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

/** Inter para texto e Sora para títulos (as mesmas fontes do protótipo, licença OFL). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold)
)
val Sora = FontFamily(
    Font(R.font.sora_semibold, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold)
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-0.8).sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
)

@Composable
fun ZuvionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (Z.dark) darkColorScheme(
            primary = Z.Violet, onPrimary = Z.Ink, secondary = Z.Cyan,
            background = Z.Space, surface = Z.Space, onBackground = Z.Text, onSurface = Z.Text,
            surfaceVariant = Color(0xFF1A1D33), onSurfaceVariant = Z.Muted, error = Z.Live
        ) else lightColorScheme(
            primary = Z.Violet, onPrimary = Color.White, secondary = Z.Cyan,
            background = Z.Space, surface = Color.White, onBackground = Z.Text, onSurface = Z.Text,
            surfaceVariant = Color(0xFFE3E6F5), onSurfaceVariant = Z.Muted, error = Z.Live
        ),
        typography = typography
    ) {
        CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge, content = content)
    }
}

/** Fundo "espaço profundo" com nebulosas violeta e ciano. */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Z.Space)
            .drawBehind {
                val w = size.width; val h = size.height
                val k = if (Z.dark) 1f else 0.45f
                drawCircle(
                    Brush.radialGradient(listOf(Z.VioletDeep.copy(alpha = 0.55f * k), Color.Transparent), center = Offset(w * 0.85f, h * 0.02f), radius = w * 0.55f),
                    radius = w * 0.55f, center = Offset(w * 0.85f, h * 0.02f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Z.CyanDeep.copy(alpha = 0.28f * k), Color.Transparent), center = Offset(w * 0.02f, h * 0.98f), radius = w * 0.45f),
                    radius = w * 0.45f, center = Offset(w * 0.02f, h * 0.98f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFD65CFF).copy(alpha = 0.12f * k), Color.Transparent), center = Offset(w * 0.5f, h * 0.6f), radius = w * 0.3f),
                    radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.6f)
                )
                val stars = listOf(0.12f to 0.18f, 0.34f to 0.72f, 0.58f to 0.22f, 0.76f to 0.64f, 0.9f to 0.34f, 0.22f to 0.48f, 0.66f to 0.88f, 0.44f to 0.08f, 0.05f to 0.6f)
                if (Z.dark) stars.forEachIndexed { i, (x, y) ->
                    drawCircle(Color.White.copy(alpha = if (i % 2 == 0) 0.6f else 0.35f), radius = if (i % 3 == 0) 1.6f else 1.1f, center = Offset(w * x, h * y))
                }
            },
        content = content
    )
}

/** Vidro líquido: gradiente translúcido + borda iluminada. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            when {
                !Z.dark && strong -> listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.78f))
                strong -> listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f))
                else -> listOf(Z.GlassHi, Z.GlassLo)
            }
        )
    )
    .border(
        1.dp,
        if (Z.dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f)))
        else Brush.verticalGradient(listOf(Color.White, Color(0xFF1B1E3C).copy(alpha = 0.10f))),
        shape
    )


/** Esmaece suavemente o conteúdo que rola para baixo de cabeçalhos (topo) e da barra (base). */
fun Modifier.fadeEdges(top: androidx.compose.ui.unit.Dp = 22.dp, bottom: androidx.compose.ui.unit.Dp = 22.dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val t = (top.toPx() / h).coerceIn(0f, 0.4f)
        val b = (bottom.toPx() / h).coerceIn(0f, 0.4f)
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, t to Color.Black, 1f - b to Color.Black, 1f to Color.Transparent),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
        )
    }
