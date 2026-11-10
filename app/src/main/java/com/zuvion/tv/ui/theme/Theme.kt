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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Z {
    val Space = Color(0xFF060814)
    val SpaceDeep = Color(0xFF03040B)
    val Text = Color(0xFFF4F5FF)
    val Muted = Color(0xFFBDC1E3)
    val Faint = Color(0xFF8C90B3)
    val Violet = Color(0xFFB9AEFF)
    val VioletDeep = Color(0xFF7C60FF)
    val Cyan = Color(0xFF8FE6F2)
    val CyanDeep = Color(0xFF26C4E4)
    val Live = Color(0xFFFF5A6E)
    val Ink = Color(0xFF0B0D1F)
    val GlassHi = Color.White.copy(alpha = 0.16f)
    val GlassLo = Color.White.copy(alpha = 0.05f)
    val Accent = Brush.linearGradient(listOf(Violet, Color(0xFF5BD8EE)))

    val palette = listOf(
        Color(0xFF6E5BFF), Color(0xFF18A9C9), Color(0xFFD9468A), Color(0xFFE29A2E),
        Color(0xFF3FBF8A), Color(0xFF5A6CFF), Color(0xFFC2417A), Color(0xFF1E7F7A)
    )
    fun colorFor(seed: String): Color = palette[(seed.hashCode() and 0x7fffffff) % palette.size]
}

val LocalIsTv = staticCompositionLocalOf { false }

fun isTvDevice(ctx: Context): Boolean {
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        !ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

private val typography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-0.8).sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
)

@Composable
fun ZuvionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Z.Violet, onPrimary = Z.Ink, secondary = Z.Cyan,
            background = Z.Space, surface = Z.Space, onBackground = Z.Text, onSurface = Z.Text,
            surfaceVariant = Color(0xFF1A1D33), onSurfaceVariant = Z.Muted, error = Z.Live
        ),
        typography = typography,
        content = content
    )
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
                drawCircle(
                    Brush.radialGradient(listOf(Z.VioletDeep.copy(alpha = 0.55f), Color.Transparent), center = Offset(w * 0.85f, h * 0.02f), radius = w * 0.55f),
                    radius = w * 0.55f, center = Offset(w * 0.85f, h * 0.02f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Z.CyanDeep.copy(alpha = 0.28f), Color.Transparent), center = Offset(w * 0.02f, h * 0.98f), radius = w * 0.45f),
                    radius = w * 0.45f, center = Offset(w * 0.02f, h * 0.98f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFD65CFF).copy(alpha = 0.12f), Color.Transparent), center = Offset(w * 0.5f, h * 0.6f), radius = w * 0.3f),
                    radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.6f)
                )
                val stars = listOf(0.12f to 0.18f, 0.34f to 0.72f, 0.58f to 0.22f, 0.76f to 0.64f, 0.9f to 0.34f, 0.22f to 0.48f, 0.66f to 0.88f, 0.44f to 0.08f, 0.05f to 0.6f)
                stars.forEachIndexed { i, (x, y) ->
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
            if (strong) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f))
            else listOf(Z.GlassHi, Z.GlassLo)
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))),
        shape
    )
