package com.zuvion.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.zuvion.tv.ZuvionApp
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.title
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass

/**
 * Superfície focável: no controle remoto ganha anel branco + brilho e cresce levemente.
 * No toque funciona como clique normal (e toque longo).
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    onLongClick: (() -> Unit)? = null,
    focusScale: Float = 1.05f,
    glass: Boolean = true,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
    fill: Color? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Itens de telas cobertas ficam fora da busca de foco (senão as setas "travam" na TV).
    val covered = com.zuvion.tv.ui.theme.LocalCovered.current
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.focusGlow(shape) else Modifier)
            .then(if (glass) Modifier.glass(shape, strong = selected) else Modifier.clip(shape))
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (focused) Modifier.border(3.dp, Z.Focus, shape) else Modifier)
            .focusProperties { canFocus = !covered }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        content = { content(focused) }
    )
}

/** Brilho suave em volta do item focado (sem sombra de elevação, que deixava "quadrados" brancos em algumas TVs). */
private fun Modifier.focusGlow(shape: Shape): Modifier = drawBehind {
    for (i in 1..4) {
        val grow = i * 3.dp.toPx()
        val outline = shape.createOutline(androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
        translate(-grow, -grow) {
            drawOutline(outline, Z.VioletDeep.copy(alpha = 0.16f / i))
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    onFocus: (() -> Unit)? = null
) {
    Focusable(onClick, modifier, CircleShape, glass = !primary, onFocus = onFocus, fill = if (primary) Z.Primary else null) { f ->
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val c = if (primary) Z.OnPrimary else Z.Text
            if (icon != null) Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
            Text(text, color = c, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun IconGlassButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, tint: Color = Z.Text, active: Boolean = false) {
    Focusable(onClick, modifier.size(size), CircleShape, focusScale = 1.1f, selected = active) {
        Icon(icon, label, tint = tint, modifier = Modifier.align(Alignment.Center).size(size * 0.45f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Z.Text, modifier = modifier)
}

@Composable
fun Logo(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val ring = if (Z.dark) Color.White else Z.Violet
    Canvas(modifier.size(size)) {
        val s = this.size.width
        drawCircle(Brush.linearGradient(listOf(Z.Violet, Color(0xFF5BD8EE)), Offset.Zero, Offset(s, s)), radius = s * 0.25f)
        rotate(-24f) {
            drawOval(ring.copy(alpha = 0.85f), topLeft = Offset(s * 0.05f, s * 0.34f), size = Size(s * 0.9f, s * 0.32f), style = Stroke(width = s * 0.036f))
        }
        drawCircle(ring, radius = s * 0.055f, center = Offset(s * 0.885f, s * 0.32f))
    }
}

@Composable
fun Wordmark(fontSize: Int = 22) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Zuvion ", maxLines = 1, softWrap = false, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.zuvion.tv.ui.theme.Sora, color = Z.Text)
        Text("TV", maxLines = 1, softWrap = false, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.zuvion.tv.ui.theme.Sora, color = Z.Violet)
    }
}

@Composable
fun Poster(url: String?, seed: String, modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.background(Brush.linearGradient(listOf(Z.colorFor(seed), Z.colorFor(seed + "x").copy(alpha = 0.7f))))) {
        if (label != null) {
            Text(label, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(10.dp))
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Cartaz do TMDB quando há chave configurada; senão (ou se falhar), o do servidor. */
@Composable
fun rememberMetaPoster(item: Content): String? {
    val app = LocalContext.current.applicationContext as ZuvionApp
    val p by produceState(app.meta.cached(item)?.poster, item.key) {
        if (app.meta.enabled) app.meta.forContent(item)?.poster?.let { value = it }
    }
    return p?.replace("/w500/", "/w342/") ?: item.poster
}

/** Cartaz vertical (filmes/séries). */
@Composable
fun PosterCard(item: Content, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 140.dp, progress: Float? = null, onLongClick: (() -> Unit)? = null, onFocus: (() -> Unit)? = null) {
    val poster = rememberMetaPoster(item)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Focusable(onClick, Modifier.fillMaxWidth().height(width * 1.48f), RoundedCornerShape(18.dp), onLongClick = onLongClick, onFocus = onFocus) {
            Poster(poster, item.name, Modifier.fillMaxSize(), label = item.title)
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        Text(item.title, style = MaterialTheme.typography.labelMedium, color = Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Cartão horizontal (continuar assistindo / episódios). */
@Composable
fun WideCard(title: String, image: String?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 220.dp, progress: Float? = null, subtitle: String? = null, onFocus: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Focusable(onClick, Modifier.fillMaxWidth().height(width * 0.57f), RoundedCornerShape(22.dp), onFocus = onFocus, onLongClick = onLongClick) {
            Poster(image, title, Modifier.fillMaxSize())
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
        Text(title, style = MaterialTheme.typography.labelLarge, color = Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Z.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Z.Tint.copy(alpha = 0.22f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(Z.Accent))
    }
}

/** Logo de canal: imagem ou número sobre cor. */
@Composable
fun ChannelLogo(item: Content, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Z.colorFor(item.name)),
        contentAlignment = Alignment.Center
    ) {
        Text(if (item.number > 0) item.number.toString() else item.name.take(2).uppercase(), color = Color.White, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.SemiBold)
        if (!item.poster.isNullOrBlank()) {
            AsyncImage(item.poster, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().background(Color(0xFF14172B)).padding(size * 0.12f))
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null) {
    Focusable(onClick, modifier, CircleShape, glass = !selected, focusScale = 1.06f, onFocus = onFocus, fill = if (selected) Z.Primary else null) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Z.OnPrimary else Z.Text,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun Badge(text: String, color: Color? = null) {
    Row(
        Modifier.glass(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (color != null) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Z.Text)
    }
}

@Composable
fun LoadingView(text: String = "Carregando…", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Z.Violet, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(text, color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Z.Muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(32.dp))
    }
}
