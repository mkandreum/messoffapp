package com.messoff.pulso.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.AuthState
import com.messoff.pulso.data.CalEvent
import com.messoff.pulso.ui.AppHeader
import com.messoff.pulso.ui.components.AvatarStack
import com.messoff.pulso.ui.components.CategoryDot
import com.messoff.pulso.ui.components.LiveBadge
import com.messoff.pulso.ui.components.Panel
import com.messoff.pulso.ui.components.PanelHead
import com.messoff.pulso.ui.components.dashedBorder
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.util.Dates

private const val WORK_START = 8 * 60
private const val WORK_END = 21 * 60

private data class Gap(val start: Int, val end: Int)

private fun gaps(events: List<CalEvent>): List<Gap> {
    val out = mutableListOf<Gap>()
    var cursor = WORK_START
    for (e in events.filter { it.start < WORK_END && it.end > WORK_START }.sortedBy { it.start }) {
        if (e.start - cursor >= 30) out += Gap(cursor, minOf(e.start, WORK_END))
        cursor = maxOf(cursor, e.end)
    }
    if (WORK_END - cursor >= 30) out += Gap(cursor, WORK_END)
    return out
}

@Composable
fun TodayScreen(
    vm: AppViewModel,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onGoWeek: () -> Unit,
    onGoTasks: () -> Unit,
    onGoFocus: () -> Unit,
    onTeams: () -> Unit = {},
) {
    val c = LocalPulso.current
    val events by vm.events.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val now by vm.now.collectAsState()
    val auth by vm.auth.collectAsState()
    val first = (auth as? AuthState.SignedIn)?.user?.name?.trim()?.split(" ")?.firstOrNull().orEmpty().ifBlank { "tú" }

    val today = Dates.iso(now.toLocalDate())
    val nowMin = Dates.nowMinutes(now)
    val todayEvents = events.filter { it.date == today }.sortedBy { it.start }
    val current = todayEvents.firstOrNull { it.start <= nowMin && it.end > nowMin }
    val next = todayEvents.firstOrNull { it.start > nowMin }
    val busy = todayEvents.sumOf { it.duration }
    val free = gaps(todayEvents)
    val freeMinutes = free.sumOf { it.end - it.start }
    val pending = tasks.filter { !it.done }
    val dueAll = pending.filter { it.due != null && it.due <= today }
    val pendingToday = pending.count { it.due == today }
    val week = Dates.weekDays(now.toLocalDate())
    val weekBusy = week.map { d -> events.filter { it.date == Dates.iso(d) }.sumOf { it.duration } }
    val bestGap = free.maxByOrNull { it.end - it.start }

    ScreenList {
        item {
            AppHeader(
                vm, Dates.dateLong(now.toLocalDate()), "Hola,", first,
                subtitle = {
                    Text(
                        buildAnnotatedString {
                            append("Hoy tienes ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) {
                                append("${todayEvents.size} ${if (todayEvents.size == 1) "evento" else "eventos"}")
                            }
                            append(" y ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) {
                                append("$pendingToday ${if (pendingToday == 1) "pendiente" else "pendientes"}")
                            }
                            append(". El resto puede esperar.")
                        },
                        style = Type.body, color = c.ink2,
                    )
                },
                onSearch = onSearch, onSettings = onSettings,
            )
        }
        item { InvitationBanner(vm, onTeams) }
        item { NextUpNote(vm, onOpen) }

        item {
            Column(
                Modifier.clip(RoundedCornerShape(22.dp)).border(1.dp, c.line, RoundedCornerShape(22.dp)),
            ) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Stat("Eventos", "${todayEvents.size}", "hoy", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                    Stat("Tiempo ocupado", Dates.duration(busy), "de 13 h", Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Stat("Huecos libres", "${free.size}", "${Dates.duration(freeMinutes)} sin plan", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                    Stat("Pendientes", "${pending.size}", "${dueAll.size} para hoy", Modifier.weight(1f), dark = true)
                }
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    PanelHead("Agenda de hoy", action = "Ver semana", onAction = onGoWeek)
                    Text("${Dates.duration(busy)} planificados · margen de sobra", style = Type.bodySmall, color = c.ink3)
                    Spacer(Modifier.height(16.dp))
                    if (todayEvents.isEmpty()) {
                        com.messoff.pulso.ui.components.EmptyState("✳", "Hoy no hay nada escrito.", "Un día entero por delante. Elige una cosa que importe.")
                    }
                    todayEvents.forEach { event ->
                        TimelineRow(vm, event, nowMin, current?.id == event.id, onOpen)
                    }
                    Spacer(Modifier.height(8.dp))
                    com.messoff.pulso.ui.components.Divider(dashed = true)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(LucideData.Clock3, c.ink2, 17.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("HUECOS PARA TI", style = Type.label, color = c.ink2)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (free.isEmpty()) Text("Día completo", style = Type.bodySmall, color = c.ink3)
                        free.forEach { gap ->
                            Row(
                                Modifier.dashedBorder(c.lineStrong, 999.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${Dates.time(gap.start)} – ${Dates.time(gap.end)}", style = Type.sans(13.5.sp), color = c.ink2)
                                Spacer(Modifier.width(6.dp))
                                Text(Dates.duration(gap.end - gap.start), style = Type.sans(12.sp), color = c.ink3)
                            }
                        }
                    }
                }
            }
        }

        item { NowCard(vm, current, next, nowMin, now, onGoFocus, onGoWeek) }

        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    PanelHead("Pendientes", action = "Bandeja", onAction = onGoTasks)
                    Spacer(Modifier.height(6.dp))
                    if (dueAll.isEmpty()) Text("Nada urgente. Buen síntoma.", style = Type.serif(18.sp, italic = true), color = c.ink3, modifier = Modifier.padding(vertical = 8.dp))
                    dueAll.take(4).forEach { task ->
                        TaskRow(vm, task, onSchedule = { vm.scheduleTask(task.id) })
                    }
                }
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    PanelHead("Ritmo semanal", aside = Dates.duration(weekBusy.sum()))
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        week.forEachIndexed { i, day ->
                            val isToday = day == now.toLocalDate()
                            val pct = (weekBusy[i] / 480f).coerceIn(0.04f, 1f)
                            val animated by animateFloatAsState(pct, spring(0.7f, 120f), label = "bar")
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.weight(1f).width(22.dp).clip(RoundedCornerShape(7.dp)).background(c.paper3), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().fillMaxHeight(animated).clip(RoundedCornerShape(7.dp)).background(if (isToday) c.accent else c.ink2.copy(alpha = 0.55f)))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(Dates.WEEKDAYS_MINI[i], style = Type.sans(12.sp, if (isToday) FontWeight.Bold else FontWeight.Medium), color = if (isToday) c.accent else c.ink3)
                            }
                        }
                    }
                    if (bestGap != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LucideIcon(LucideData.Sparkles, c.accent, 15.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Tu mejor hueco: ${Dates.time(bestGap.start)} – ${Dates.time(bestGap.end)}", style = Type.bodySmall, color = c.ink2)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "«Deja aire entre las cosas.\nTambién es parte del plan.»",
                style = Type.serif(22.sp, italic = true, lineHeight = 26.sp), color = c.ink3,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, hint: String, modifier: Modifier, dark: Boolean = false) {
    val c = LocalPulso.current
    Column(
        modifier.background(if (dark) c.ink else c.paper2).padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(label.uppercase(), style = Type.label, color = if (dark) c.onInk.copy(alpha = 0.7f) else c.ink2, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = Type.serif(36.sp, lineHeight = 40.sp), color = if (dark) c.onInk else c.ink, maxLines = 1)
        Text(hint, style = Type.sans(13.sp), color = if (dark) c.onInk.copy(alpha = 0.7f) else c.ink3, maxLines = 1)
    }
}

@Composable
private fun TimelineRow(vm: AppViewModel, event: CalEvent, nowMin: Int, isCurrent: Boolean, onOpen: (String) -> Unit) {
    val c = LocalPulso.current
    val isPast = event.end <= nowMin
    val catColor = c.cat(event.cat)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 12.dp).alpha(if (isPast) 0.55f else 1f)) {
        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.End) {
            Text(Dates.time(event.start), style = Type.sans(17.sp, FontWeight.SemiBold), color = c.ink)
            Text(Dates.time(event.end), style = Type.sans(13.sp), color = c.ink3)
        }
        Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            Box(Modifier.padding(top = 7.dp).size(10.dp).clip(CircleShape).background(if (isCurrent) c.accent else c.ink3))
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isCurrent) c.wash(event.cat) else Color.Transparent)
                .border(1.dp, if (isCurrent) catColor.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(16.dp))
                .drawBehind { drawLine(catColor, Offset(1.5.dp.toPx(), 10.dp.toPx()), Offset(1.5.dp.toPx(), size.height - 10.dp.toPx()), 3.dp.toPx()) }
                .press(scaleTo = 0.98f) { onOpen(event.id) }
                .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.cat.label.uppercase(), style = Type.label, color = c.ink3)
                Spacer(Modifier.width(10.dp))
                if (isCurrent) LiveBadge()
                if (isPast) Text("HECHO", style = Type.label, color = c.ink3.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(4.dp))
            Text(event.title, style = Type.sans(18.sp, FontWeight.Medium), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val checks = event.checklist.size
            if (event.location.isNotBlank() || event.attendees.isNotEmpty() || checks > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.location.isNotBlank()) {
                        LucideIcon(LucideData.MapPin, c.ink3, 15.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(event.location, style = Type.sans(13.sp), color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                        Spacer(Modifier.width(10.dp))
                    }
                    if (checks > 0) {
                        Text("${event.checklist.count { it.done }}/$checks tareas", style = Type.sans(13.sp), color = c.ink3)
                        Spacer(Modifier.width(10.dp))
                    }
                    AvatarStack(event.attendees, 3, 22.dp)
                }
            }
        }
    }
}

@Composable
private fun NowCard(
    vm: AppViewModel,
    current: CalEvent?,
    next: CalEvent?,
    nowMin: Int,
    now: java.time.LocalDateTime,
    onGoFocus: () -> Unit,
    onGoWeek: () -> Unit,
) {
    val c = LocalPulso.current
    val glow = c.accent
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.ink)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.42f), Color.Transparent), center = Offset(size.width - 40.dp.toPx(), 0f), radius = 180.dp.toPx()),
                    radius = 180.dp.toPx(), center = Offset(size.width - 40.dp.toPx(), 0f),
                )
            }
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (current != null) "AHORA MISMO" else "LO SIGUIENTE", style = Type.kicker, color = c.onInk.copy(alpha = 0.65f), modifier = Modifier.weight(1f))
            if (current != null) LiveBadge(color = if (c.night) c.accent else Color(0xFFF2B27A))
        }
        Spacer(Modifier.height(10.dp))
        val (title, meta) = when {
            current != null -> current.title to "${Dates.range(current.start, current.duration)} · ${current.location.ifBlank { "Sin ubicación" }}"
            next != null -> next.title to "Empieza ${Dates.countdown(next.date, next.start, now)} · ${Dates.time(next.start)}"
            else -> "Día terminado." to "Cierra el portátil. Mañana empieza con luz a las 8:00."
        }
        Text(title, style = Type.serif(38.sp, lineHeight = 40.sp), color = c.onInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(meta, style = Type.sans(15.sp), color = c.onInk.copy(alpha = 0.75f))
        if (current != null) {
            Spacer(Modifier.height(16.dp))
            val progress = ((nowMin - current.start).toFloat() / current.duration).coerceIn(0f, 1f)
            val animated by animateFloatAsState(progress, label = "nowbar")
            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                drawRoundRect(c.onInk.copy(alpha = 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                drawRoundRect(glow, size = size.copy(width = size.width * animated), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    current != null -> "${Dates.duration(current.end - nowMin)} restantes"
                    next != null -> next.location.ifBlank { "Sin ubicación" }
                    else -> "Sin eventos pendientes"
                },
                style = Type.sans(14.sp), color = c.onInk.copy(alpha = 0.7f), modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, c.onInk.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                    .press(haptics = vm.haptics) { if (current == null && next == null) onGoWeek() else onGoFocus() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (current == null && next == null) "Ver mañana" else "Enfocar", style = Type.sans(15.sp, FontWeight.SemiBold), color = c.onInk)
                Spacer(Modifier.width(6.dp))
                LucideIcon(LucideData.ArrowUpRight, c.onInk, 16.dp)
            }
        }
    }
}
