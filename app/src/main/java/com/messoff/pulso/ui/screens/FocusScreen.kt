package com.messoff.pulso.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.ui.AppHeader
import com.messoff.pulso.ui.components.BtnKind
import com.messoff.pulso.ui.components.Panel
import com.messoff.pulso.ui.components.PanelHead
import com.messoff.pulso.ui.components.PulsoButton
import com.messoff.pulso.ui.components.PulsoSwitch
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.util.Dates
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private val PRESETS = listOf(25, 50, 90)

fun clock(seconds: Int) = "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

/** Reloj que avanza 4 veces por segundo mientras el foco corre. */
@Composable
private fun rememberFocusClock(vm: AppViewModel): Long {
    val focus by vm.focus.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focus.running) {
        while (focus.running) {
            now = System.currentTimeMillis()
            vm.checkFocusDone()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    return now
}

@Composable
fun FocusScreen(vm: AppViewModel, onOpen: (String) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val c = LocalPulso.current
    val focus by vm.focus.collectAsState()
    val events by vm.events.collectAsState()
    val now by vm.now.collectAsState()
    val auth by vm.auth.collectAsState()
    val nowMs = rememberFocusClock(vm)
    val remaining = focus.remaining(nowMs)
    val total = focus.preset * 60
    val progress = (1f - remaining.toFloat() / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(progress, tween(if (focus.running) 950 else 350), label = "ring")
    val pulse by rememberInfiniteTransition(label = "beat").animateFloat(
        1f, 1.025f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "beatScale",
    )
    val upcoming = events.map { it to Dates.epochMillis(it.date, it.start) }.filter { it.second >= System.currentTimeMillis() }.minByOrNull { it.second }?.first
    val totalToday = focus.sessions.sumOf { it.minutes }
    val hapticsOn = (auth as? com.messoff.pulso.AuthState.SignedIn)?.user?.haptics ?: true

    ScreenList {
        item {
            AppHeader(
                vm, Dates.dateLong(now.toLocalDate()), "Modo", "enfoque",
                subtitle = { Text("Una cosa a la vez. El resto puede esperar.", style = Type.body, color = c.ink2) },
                onSearch = onSearch, onSettings = onSettings,
            )
        }
        item {
            Panel(Modifier.fillMaxWidth(), radius = 26.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("SESIÓN DE ENFOQUE", style = Type.kicker, color = c.ink2, modifier = Modifier.weight(1f))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (focus.running) c.accent else c.ink3))
                        Spacer(Modifier.width(6.dp))
                        Text(if (focus.running) "EN CURSO" else "EN PAUSA", style = Type.label, color = if (focus.running) c.accent else c.ink3)
                    }
                    Spacer(Modifier.height(18.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth(0.86f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        val ring = c.accent
                        Canvas(Modifier.fillMaxSize().scale(if (focus.running) pulse else 1f)) {
                            val stroke = 3.dp.toPx()
                            val inset = stroke * 2
                            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                            drawCircle(c.paper4, radius = arcSize.width / 2, style = Stroke(stroke))
                            if (focus.running) {
                                drawCircle(
                                    Brush.radialGradient(listOf(ring.copy(alpha = 0.10f), Color.Transparent)),
                                    radius = size.width / 2,
                                )
                            }
                            drawArc(
                                ring, -90f, 360f * animated, false,
                                topLeft = Offset(inset, inset), size = arcSize,
                                style = Stroke(stroke * 1.6f, cap = StrokeCap.Round),
                            )
                            for (i in 0 until 4) {
                                rotate(i * 90f) {
                                    drawLine(c.ink3, Offset(size.width / 2, inset - 8.dp.toPx()), Offset(size.width / 2, inset + 4.dp.toPx()), 1.5.dp.toPx())
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (focus.running) "CONCENTRADO" else "LISTO", style = Type.label, color = c.ink3)
                            AnimatedContent(
                                clock(remaining),
                                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                                label = "clock",
                            ) { text ->
                                Text(text, style = Type.serif(84.sp, lineHeight = 88.sp), color = c.ink)
                            }
                            Text("${focus.preset} minutos · sin interrupciones", style = Type.sans(13.sp), color = c.ink3)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    // Presets con indicador
                    BoxWithConstraints(Modifier.width(270.dp).height(46.dp).clip(RoundedCornerShape(999.dp)).background(c.paper3).padding(4.dp)) {
                        val w = maxWidth / 3
                        val index = PRESETS.indexOf(focus.preset).coerceAtLeast(0)
                        val x by animateDpAsState(w * index, spring(0.72f, Spring.StiffnessMediumLow), label = "preset")
                        Box(Modifier.offset(x = x).width(w).fillMaxHeight().shadow(3.dp, RoundedCornerShape(999.dp)).clip(RoundedCornerShape(999.dp)).background(c.paper2))
                        Row(Modifier.fillMaxSize()) {
                            PRESETS.forEach { m ->
                                Box(Modifier.weight(1f).fillMaxHeight().press(scaleTo = 0.92f) { vm.chooseFocus(m) }, contentAlignment = Alignment.Center) {
                                    Text("$m min", style = Type.sans(14.sp, FontWeight.SemiBold), color = if (focus.preset == m) c.ink else c.ink2)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PulsoButton(
                            if (focus.running) "Pausar" else if (remaining == 0) "Otra vez" else "Empezar",
                            vm::toggleFocus,
                            icon = if (focus.running) LucideData.Pause else LucideData.Play,
                            height = 54.dp,
                        )
                        PulsoButton("Reiniciar", vm::resetFocus, kind = BtnKind.Ghost, icon = LucideData.RotateCcw, height = 54.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(LucideData.Volume2, c.ink3, 15.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Al terminar, el móvil vibra con un patrón suave de logro.", style = Type.sans(12.5.sp), color = c.ink3)
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    PanelHead("Después de esto", aside = "Siguiente")
                    Spacer(Modifier.height(12.dp))
                    if (upcoming == null) {
                        Text("Sin eventos próximos. Tiempo regalado.", style = Type.serif(18.sp, italic = true), color = c.ink3)
                    } else {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.wash(upcoming.cat))
                                .border(1.dp, c.cat(upcoming.cat).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                .press(scaleTo = 0.98f, haptics = vm.haptics) { onOpen(upcoming.id) }.padding(16.dp),
                        ) {
                            Text("${Dates.dateMedium(Dates.parse(upcoming.date)).uppercase()} · ${Dates.time(upcoming.start)}", style = Type.label, color = c.ink3)
                            Spacer(Modifier.height(4.dp))
                            Text(upcoming.title, style = Type.serif(26.sp), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Dates.countdown(upcoming.date, upcoming.start, now) + if (upcoming.location.isNotBlank()) " · ${upcoming.location}" else "",
                                    style = Type.sans(13.sp), color = c.ink2, modifier = Modifier.weight(1f),
                                )
                                LucideIcon(LucideData.ArrowUpRight, c.ink2, 17.dp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    PanelHead("Ritual de arranque")
                    listOf(
                        "Una sola cosa" to "Escribe el resultado en una línea. Si no cabe, no es una sesión.",
                        "Silencia lo que no importa" to "Notificaciones fuera. La bandeja espera.",
                        "Veinte minutos honestos" to "Empieza aunque no estés inspirado. La inspiración llega tarde.",
                    ).forEachIndexed { i, (title, body) ->
                        Row(Modifier.padding(top = 14.dp)) {
                            Text("0${i + 1}", style = Type.serif(20.sp, italic = true), color = c.accent, modifier = Modifier.width(36.dp))
                            Column {
                                Text(title, style = Type.sans(15.sp, FontWeight.SemiBold), color = c.ink)
                                Text(body, style = Type.bodySmall, color = c.ink3)
                            }
                        }
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    PanelHead("Hoy", aside = Dates.duration(totalToday))
                    Spacer(Modifier.height(8.dp))
                    if (focus.sessions.isEmpty()) {
                        Text("Aún sin sesiones. La primera es la que cuesta.", style = Type.serif(18.sp, italic = true), color = c.ink3)
                    }
                    focus.sessions.forEach { s ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c.salud))
                            Spacer(Modifier.width(10.dp))
                            Text("${s.minutes} min de enfoque", style = Type.sans(15.sp), color = c.ink, modifier = Modifier.weight(1f))
                            val time = runCatching { Instant.parse(s.completedAt).atZone(ZoneId.systemDefault()).toLocalTime() }.getOrNull()
                            Text(time?.let { Dates.time(it.hour * 60 + it.minute) } ?: "", style = Type.sans(13.sp), color = c.ink3)
                        }
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vibración", style = Type.sans(17.sp, FontWeight.SemiBold), color = c.ink)
                        Text("Respuesta táctil en cada gesto.", style = Type.bodySmall, color = c.ink3)
                    }
                    PulsoSwitch(hapticsOn, { vm.setHaptics(it) }, vm.haptics)
                }
            }
        }
    }
}

@Composable
fun FocusPill(vm: AppViewModel, onOpen: () -> Unit) {
    val c = LocalPulso.current
    val focus by vm.focus.collectAsState()
    val nowMs = rememberFocusClock(vm)
    val remaining = focus.remaining(nowMs)
    val progress = 1f - remaining.toFloat() / (focus.preset * 60)
    Row(
        Modifier.shadow(18.dp, RoundedCornerShape(999.dp)).clip(RoundedCornerShape(999.dp)).background(c.ink).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.press(haptics = vm.haptics, onClick = onOpen).padding(start = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(26.dp)) {
                drawCircle(c.onInk.copy(alpha = 0.18f), style = Stroke(3.dp.toPx()))
                drawArc(c.accent, -90f, 360f * progress, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            Spacer(Modifier.width(10.dp))
            Text(clock(remaining), style = Type.serif(26.sp), color = c.onInk)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.accent).press(scaleTo = 0.85f) { vm.toggleFocus() },
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(if (focus.running) LucideData.Pause else LucideData.Play, c.accentInk, 17.dp, 2.2f)
        }
    }
}
