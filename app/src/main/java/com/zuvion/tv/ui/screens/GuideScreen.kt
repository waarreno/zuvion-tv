package com.zuvion.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zuvion.tv.data.Content
import com.zuvion.tv.data.EpgProgram
import com.zuvion.tv.data.Kind
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.components.Chip
import com.zuvion.tv.ui.components.ChannelLogo
import com.zuvion.tv.ui.components.EmptyView
import com.zuvion.tv.ui.components.Focusable
import com.zuvion.tv.ui.components.LoadingView
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.Z
import com.zuvion.tv.ui.theme.glass
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PER_MIN: Dp = 5.dp
private const val HOUR = 3_600_000L

@Composable
fun GuideScreen(state: AppState) {
    val tv = LocalIsTv.current
    val programs by state.epg.programs.collectAsState()
    val loadingEpg by state.epg.loading.collectAsState()
    val (cats, selected, select) = rememberCategoryPicker(state, Kind.LIVE)
    val channels = categoryItems(state, Kind.LIVE, selected)
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    // Janela: de 2h atrás até 12h à frente, alinhada em meia hora
    val windowStart = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, -2)
            set(Calendar.MINUTE, if (get(Calendar.MINUTE) >= 30) 30 else 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val windowEnd = windowStart + 14 * HOUR
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    var focusedProg by remember { mutableStateOf<Pair<Content, EpgProgram>?>(null) }

    LaunchedEffect(Unit) {
        val px = with(density) { (PER_MIN * ((System.currentTimeMillis() - windowStart) / 60000f - 20f)).toPx() }
        hScroll.scrollTo(px.toInt().coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize().padding(top = 16.dp, end = if (tv) 20.dp else 0.dp, bottom = if (tv) 16.dp else 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Cabeçalho com programa em foco
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).glass(RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val fp = focusedProg
                if (fp == null) {
                    Text("Guia de programação", style = MaterialTheme.typography.headlineMedium, color = Z.Text)
                    Text(if (loadingEpg) "Baixando o guia…" else "Navegue pela grade e aperte OK para assistir.", color = Z.Muted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val (c, p) = fp
                    val tag = when {
                        p.isNow() -> "AO VIVO"
                        p.end < System.currentTimeMillis() -> if (c.catchupDays > 0) "REPLAY DISPONÍVEL" else "JÁ EXIBIDO"
                        else -> "EM BREVE"
                    }
                    Text("${c.name.uppercase()} · $tag", style = MaterialTheme.typography.labelSmall, color = Z.Cyan, maxLines = 1)
                    Text(p.title, style = MaterialTheme.typography.headlineMedium, color = Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${fmt.format(Date(p.start))} – ${fmt.format(Date(p.end))}" + if (p.desc.isNotBlank()) " · ${p.desc}" else "",
                        color = Z.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats) { c -> Chip(c.name, c.id == selected, { select(c) }) }
        }
        when {
            channels == null -> LoadingView()
            programs.isEmpty() && !loadingEpg -> EmptyView("Esta lista não tem guia (EPG). Em Ajustes você pode informar uma URL XMLTV.")
            programs.isEmpty() -> LoadingView("Baixando o guia de programação…")
            channels.isEmpty() -> EmptyView("Nenhum canal nesta categoria.")
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).glass(RoundedCornerShape(24.dp)).padding(10.dp)) {
                // Régua de horários
                Row {
                    Box(Modifier.width(if (tv) 200.dp else 120.dp))
                    Box(Modifier.horizontalScroll(hScroll)) {
                        Row {
                            var t = windowStart
                            while (t < windowEnd) {
                                Text(fmt.format(Date(t)), color = Z.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(PER_MIN * 30))
                                t += HOUR / 2
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(channels) { c ->
                            GuideRow(state, c, channels, windowStart, windowEnd, hScroll, tv) { p -> focusedProg = c to p }
                        }
                    }
                    // Linha do "agora"
                    val nowOffset = PER_MIN * ((System.currentTimeMillis() - windowStart) / 60000f) - with(density) { hScroll.value.toDp() }
                    if (nowOffset > 0.dp) {
                        Box(Modifier.offset(x = (if (tv) 206.dp else 126.dp) + nowOffset).width(2.dp).fillMaxHeight().background(Z.Cyan))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    state: AppState, c: Content, all: List<Content>, windowStart: Long, windowEnd: Long,
    hScroll: androidx.compose.foundation.ScrollState, tv: Boolean, onFocusProg: (EpgProgram) -> Unit
) {
    val progs = remember(c, state.epg.programs.value) { state.epg.forChannel(c).filter { it.end > windowStart && it.start < windowEnd } }
    Row(Modifier.height(58.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Focusable({ state.play(c, all) }, Modifier.width(if (tv) 194.dp else 114.dp).fillMaxHeight(), RoundedCornerShape(14.dp), focusScale = 1.02f) {
            Row(Modifier.padding(horizontal = 8.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelLogo(c, 34.dp)
                Text(c.name, style = MaterialTheme.typography.labelLarge, color = Z.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.horizontalScroll(hScroll), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            if (progs.isEmpty()) {
                Box(Modifier.width(PER_MIN * 60 * 14).fillMaxHeight().background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("Sem informação", color = Z.Faint, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                var cursor = windowStart
                progs.forEach { p ->
                    val start = maxOf(p.start, windowStart)
                    if (start > cursor) Box(Modifier.width(PER_MIN * ((start - cursor) / 60000f)))
                    val end = minOf(p.end, windowEnd)
                    val w = PER_MIN * ((end - start) / 60000f)
                    cursor = end
                    val now = p.isNow()
                    val past = p.end < System.currentTimeMillis()
                    Box(Modifier.width(w).fillMaxHeight().padding(end = 4.dp)) {
                        Focusable(
                            onClick = {
                                when {
                                    now -> state.play(c, all)
                                    past && c.catchupDays > 0 -> {
                                        val u = state.repo.source?.catchupUrl(c, p.start, p.end)
                                        if (u != null) state.play(c, listOf(c), overrideUrl = u, subtitle = p.title) else state.showToast("Replay indisponível")
                                    }
                                    past -> state.showToast("Este canal não tem replay")
                                    else -> state.showToast("Começa às ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.start))}")
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(14.dp),
                            glass = now,
                            selected = now,
                            focusScale = 1.02f,
                            onFocus = { onFocusProg(p) }
                        ) {
                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = if (past) 0.03f else 0.07f)))
                            Column(Modifier.padding(horizontal = 10.dp).align(Alignment.CenterStart)) {
                                Text(p.title, style = MaterialTheme.typography.labelLarge, color = if (past) Z.Muted else Z.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.start)), style = MaterialTheme.typography.labelMedium, color = Z.Faint, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
