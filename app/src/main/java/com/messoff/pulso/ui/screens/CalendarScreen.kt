package com.messoff.pulso.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.data.CalEvent
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.ui.AppHeader
import com.messoff.pulso.ui.components.dashedBorder
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.ui.theme.teamColor
import com.messoff.pulso.util.Dates
import java.time.LocalDate
import kotlin.math.roundToInt

private const val DAY_START = 7 * 60
private const val DAY_END = 23 * 60
private val HOUR_H = 68.dp
private val GUTTER = 54.dp

enum class CalView(val label: String) { Dia("Día"), Semana("Semana"), Agenda("Agenda"), Mes("Mes") }

@Composable
fun CalendarScreen(
    vm: AppViewModel,
    onOpen: (String) -> Unit,
    onSlot: (LocalDate, Int) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onTeams: () -> Unit = {},
) {
    val c = LocalPulso.current
    var view by rememberSaveable {
        mutableStateOf(CalView.entries.firstOrNull { it.name.lowercase() == vm.calViewName } ?: CalView.Agenda)
    }
    var anchorEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var direction by remember { mutableStateOf(1) }
    val anchor = LocalDate.ofEpochDay(anchorEpoch)

    fun shift(step: Int) {
        vm.haptics.play(Haptic.Selection)
        direction = step
        anchorEpoch = when (view) {
            CalView.Mes -> anchor.withDayOfMonth(1).plusMonths(step.toLong()).toEpochDay()
            CalView.Dia -> anchor.plusDays(step.toLong()).toEpochDay()
            CalView.Semana, CalView.Agenda -> anchor.plusWeeks(step.toLong()).toEpochDay()
        }
    }
    fun goToday() {
        vm.haptics.play(Haptic.Selection)
        direction = if (anchor.isAfter(LocalDate.now())) -1 else 1
        anchorEpoch = LocalDate.now().toEpochDay()
    }
    fun pickDay(day: LocalDate) {
        vm.haptics.play(Haptic.Selection)
        anchorEpoch = day.toEpochDay()
        view = CalView.Dia
    }

    val monday = Dates.startOfWeek(anchor)
    val label = when (view) {
        CalView.Mes -> Dates.month(anchor).replaceFirstChar { it.uppercase() }
        CalView.Dia -> Dates.dateMedium(anchor)
        CalView.Semana, CalView.Agenda -> Dates.weekRange(monday)
    }
    val sub = if (view == CalView.Mes) "VISTA MENSUAL" else "SEMANA ${Dates.weekNumber(anchor)} · ${anchor.year}"
    val eyebrow = when (view) {
        CalView.Mes -> Dates.month(anchor)
        CalView.Dia -> Dates.dateLong(anchor)
        CalView.Semana, CalView.Agenda -> "Semana ${Dates.weekNumber(anchor)} · ${Dates.MONTHS[anchor.monthValue - 1]}"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        AppHeader(
            vm, eyebrow, "Tu", when (view) { CalView.Dia -> "día"; CalView.Semana -> "semana"; CalView.Agenda -> "agenda"; CalView.Mes -> "mes" },
            subtitle = {
                Text(
                    when (view) {
                        CalView.Mes -> "Toca un día para abrirlo. Desliza para cambiar de mes."
                        CalView.Dia -> "Todo lo del día, con espacio. Toca un hueco para crear."
                        CalView.Semana -> "Mantén pulsado para mover. Toca un hueco para crear."
                        CalView.Agenda -> "Lo que viene, día a día. Toca un evento para abrirlo."
                    },
                    style = Type.body, color = c.ink2,
                )
            },
            onSearch = onSearch, onSettings = onSettings,
        )
        InvitationBanner(vm, onTeams)
        NextUpNote(vm, onOpen)
        Spacer(Modifier.height(16.dp))

        // Píldora de fecha
        Row(
            Modifier.fillMaxWidth().height(64.dp).shadow(10.dp, RoundedCornerShape(999.dp), ambientColor = Color(0x22000000))
                .clip(RoundedCornerShape(999.dp)).background(c.paper2).border(1.dp, c.line, RoundedCornerShape(999.dp)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).press(scaleTo = 0.85f) { shift(-1) }, contentAlignment = Alignment.Center) {
                LucideIcon(LucideData.ChevronLeft, c.ink, 22.dp, 1.9f)
            }
            Column(Modifier.weight(1f).press(scaleTo = 0.97f) { goToday() }, horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    label,
                    transitionSpec = {
                        (slideInHorizontally(spring(0.8f, 600f)) { direction * it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -direction * it / 4 } + fadeOut(tween(100)))
                    },
                    label = "dateLabel",
                ) { text -> Text(text, style = Type.sans(18.sp, FontWeight.SemiBold), color = c.ink, maxLines = 1) }
                Text(sub, style = Type.sans(11.5.sp, FontWeight.Medium, 0.1.sp), color = c.ink3)
            }
            Box(Modifier.size(48.dp).press(scaleTo = 0.85f) { shift(1) }, contentAlignment = Alignment.Center) {
                LucideIcon(LucideData.ChevronRight, c.ink, 22.dp, 1.9f)
            }
        }
        Spacer(Modifier.height(10.dp))

        // Selector Día / Semana / Mes con indicador deslizante
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(999.dp)).background(c.paper3).border(1.dp, c.line, RoundedCornerShape(999.dp)).padding(4.dp),
            ) {
                val w = maxWidth / 3
                val x by animateDpAsState(w * view.ordinal, spring(0.72f, Spring.StiffnessMediumLow), label = "seg")
                Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(c.ink))
                Row(Modifier.fillMaxSize()) {
                    CalView.entries.forEach { option ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight().press(scaleTo = 0.94f) {
                                if (view != option) {
                                    vm.haptics.play(Haptic.Selection)
                                    view = option
                                    vm.calViewName = option.name.lowercase()
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(option.label, style = Type.sans(14.sp, FontWeight.Medium), color = if (view == option) c.onInk else c.ink2)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(999.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(999.dp))
                    .press(onClick = ::goToday).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Hoy", style = Type.sans(15.sp, FontWeight.Medium), color = c.ink) }
        }
        Spacer(Modifier.height(10.dp))
        CalendarChips(vm, onTeams)
        Spacer(Modifier.height(10.dp))

        var swipe by remember { mutableFloatStateOf(0f) }
        AnimatedContent(
            targetState = Triple(view, anchorEpoch, if (view == CalView.Semana || view == CalView.Agenda) monday.toEpochDay() else anchorEpoch),
            contentKey = { (v, a, m) -> "$v-${if (v == CalView.Semana || v == CalView.Agenda) m else if (v == CalView.Mes) LocalDate.ofEpochDay(a).withDayOfMonth(1).toEpochDay() else a}" },
            transitionSpec = {
                (slideInHorizontally(spring(0.85f, 380f)) { direction * it / 8 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(180)) { -direction * it / 8 } + fadeOut(tween(140)))
            },
            label = "range",
            modifier = Modifier.pointerInput(view) {
                if (view == CalView.Semana) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { swipe = 0f },
                    onDragEnd = { if (swipe < -140) shift(1) else if (swipe > 140) shift(-1) },
                ) { _, amount -> swipe += amount }
            },
        ) { (v, a, _) ->
            val day = LocalDate.ofEpochDay(a)
            when (v) {
                CalView.Mes -> MonthGrid(vm, day, onOpen, ::pickDay)
                CalView.Dia -> WeekGrid(vm, listOf(day), onOpen, onSlot, ::pickDay)
                CalView.Semana -> WeekGrid(vm, Dates.weekDays(day), onOpen, onSlot, ::pickDay)
                CalView.Agenda -> AgendaList(vm, Dates.weekDays(day), onOpen, onSlot)
            }
        }
        Spacer(Modifier.height(140.dp))
    }
}

private fun minutesToDp(minutes: Int): Dp = HOUR_H * (minutes / 60f)

@Composable
private fun WeekGrid(
    vm: AppViewModel,
    days: List<LocalDate>,
    onOpen: (String) -> Unit,
    onSlot: (LocalDate, Int) -> Unit,
    onPickDay: (LocalDate) -> Unit,
) {
    val c = LocalPulso.current
    val events by vm.events.collectAsState()
    val teams by vm.teams.collectAsState()
    val now by vm.now.collectAsState()
    val today = LocalDate.now()
    val density = LocalDensity.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val totalH = minutesToDp(DAY_END - DAY_START)

    // Estado del arrastre
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    BoxWithConstraints(
        Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x22000000))
            .clip(RoundedCornerShape(24.dp)).background(c.paper2).border(1.dp, c.line, RoundedCornerShape(24.dp)),
    ) {
        val colW = if (days.size == 1) maxWidth - GUTTER else maxOf(96.dp, (maxWidth - GUTTER) / 3.25f)
        val todayIndex = days.indexOf(today)

        LaunchedEffect(days.first()) {
            val focus = if (todayIndex >= 0) (Dates.nowMinutes() - 90).coerceIn(DAY_START, DAY_END - 4 * 60) else 8 * 60
            vScroll.scrollTo(with(density) { minutesToDp(focus - DAY_START).roundToPx() })
            if (todayIndex > 0 && days.size > 3) hScroll.scrollTo(with(density) { (colW * todayIndex - 20.dp).roundToPx().coerceAtLeast(0) })
        }

        Column {
            // Cabecera de días
            Row(Modifier.fillMaxWidth().height(76.dp).drawBehind {
                drawLine(c.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }) {
                Box(Modifier.width(GUTTER).fillMaxHeight().drawBehind {
                    drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                }, contentAlignment = Alignment.BottomCenter) {
                    Text(Dates.gmtLabel(), style = Type.sans(10.sp, FontWeight.Medium), color = c.ink3, modifier = Modifier.padding(bottom = 10.dp))
                }
                Row(Modifier.horizontalScroll(hScroll, enabled = dragId == null)) {
                    days.forEach { day ->
                        val isToday = day == today
                        Column(
                            Modifier.width(colW).fillMaxHeight().background(if (isToday) c.accent.copy(alpha = 0.04f) else Color.Transparent)
                                .press(scaleTo = 0.96f) { onPickDay(day) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(Dates.weekdayShort(day).uppercase(), style = Type.sans(12.sp, FontWeight.SemiBold, 0.1.sp), color = if (isToday) c.accent else c.ink3)
                            Spacer(Modifier.height(2.dp))
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${day.dayOfMonth}", style = Type.serif(26.sp), color = if (isToday) c.accentInk else c.ink)
                            }
                        }
                    }
                }
            }

            // Cuerpo
            Box(Modifier.fillMaxWidth().height(560.dp).verticalScroll(vScroll, enabled = dragId == null)) {
                Row(Modifier.height(totalH)) {
                    // Horas
                    Box(Modifier.width(GUTTER).fillMaxHeight().drawBehind {
                        drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                    }) {
                        for (h in 7 until 23) {
                            Text(
                                "${h.toString().padStart(2, '0')}:00",
                                style = Type.serif(13.sp, italic = true), color = c.ink3,
                                modifier = Modifier.offset(x = 8.dp, y = minutesToDp(h * 60 - DAY_START) + if (h == 7) 2.dp else (-9).dp),
                            )
                        }
                    }
                    Row(Modifier.horizontalScroll(hScroll, enabled = dragId == null)) {
                        days.forEachIndexed { dayIndex, day ->
                            val date = Dates.iso(day)
                            val dayEvents = events.filter { it.date == date }.sortedWith(compareBy({ it.start }, { -it.duration }))
                            val stacked = HashSet<String>()
                            var maxEnd = -1
                            dayEvents.forEach { e ->
                                if (e.start < maxEnd) stacked += e.id
                                maxEnd = maxOf(maxEnd, e.end)
                            }
                            val isToday = day == today
                            Box(
                                Modifier
                                    .width(colW)
                                    .fillMaxHeight()
                                    .background(if (isToday) c.accent.copy(alpha = 0.035f) else Color.Transparent)
                                    .drawBehind {
                                        val hour = HOUR_H.toPx()
                                        var y = 0f
                                        while (y < size.height) {
                                            drawLine(c.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                                            drawLine(
                                                c.line.copy(alpha = 0.5f), Offset(0f, y + hour / 2), Offset(size.width, y + hour / 2), 1.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
                                            )
                                            y += hour
                                        }
                                        if (dayIndex > 0) drawLine(c.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
                                    }
                                    .pointerInput(day) {
                                        detectTapGestures { pos ->
                                            val minutes = DAY_START + (pos.y / HOUR_H.toPx() * 60).toInt()
                                            val snapped = ((minutes / 30) * 30).coerceIn(DAY_START, DAY_END - 30)
                                            onSlot(day, snapped)
                                        }
                                    },
                            ) {
                                // Previsualización del hueco de destino
                                val t = target
                                val dragged = dragId?.let { id -> events.firstOrNull { it.id == id } }
                                if (t != null && dragged != null && t.first == dayIndex) {
                                    val conflict = dayEvents.any { it.id != dragged.id && t.second < it.end && t.second + dragged.duration > it.start }
                                    Box(
                                        Modifier
                                            .offset(y = minutesToDp(t.second - DAY_START))
                                            .padding(horizontal = 4.dp)
                                            .fillMaxWidth()
                                            .height(minutesToDp(dragged.duration) - 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background((if (conflict) c.accent else c.ink).copy(alpha = 0.06f))
                                            .dashedBorder(if (conflict) c.accent else c.ink3, 12.dp, 1.5.dp)
                                            .padding(8.dp),
                                    ) {
                                        Text(
                                            Dates.time(t.second) + if (conflict) " · se solapa" else "",
                                            style = Type.sans(12.sp, FontWeight.Bold), color = if (conflict) c.accent else c.ink2,
                                        )
                                    }
                                }

                                dayEvents.forEach { event ->
                                    val isDragging = dragId == event.id
                                    EventBlock(
                                        event = event,
                                        stacked = event.id in stacked,
                                        teamTint = teams.firstOrNull { it.id == event.teamId }?.let { teamColor(it.color, c.night) },
                                        dragging = isDragging,
                                        dx = if (isDragging) dragDx else 0f,
                                        dy = if (isDragging) dragDy else 0f,
                                        onClick = { onOpen(event.id) },
                                        onDragStart = {
                                            vm.haptics.play(Haptic.Medium)
                                            dragId = event.id
                                            dragDx = 0f
                                            dragDy = 0f
                                            target = dayIndex to event.start
                                        },
                                        onDrag = { x, y ->
                                            dragDx += x
                                            dragDy += y
                                            val colPx = with(density) { colW.toPx() }
                                            val hourPx = with(density) { HOUR_H.toPx() }
                                            val idx = (dayIndex + (dragDx / colPx).roundToInt()).coerceIn(0, days.lastIndex)
                                            val start = ((event.start + dragDy / hourPx * 60) / 30f).roundToInt() * 30
                                            val next = idx to start.coerceIn(DAY_START, DAY_END - 30)
                                            if (next != target) {
                                                target = next
                                                vm.haptics.tick()
                                            }
                                        },
                                        onDragEnd = { commit ->
                                            val t2 = target
                                            dragId = null
                                            target = null
                                            dragDx = 0f
                                            dragDy = 0f
                                            if (!commit || t2 == null) {
                                                vm.haptics.play(Haptic.Warning)
                                                return@EventBlock
                                            }
                                            val newDate = Dates.iso(days[t2.first])
                                            if (newDate == event.date && t2.second == event.start) {
                                                vm.haptics.play(Haptic.Light)
                                                return@EventBlock
                                            }
                                            val previous = vm.moveEvent(event.id, newDate, t2.second)
                                            vm.haptics.play(Haptic.Snap)
                                            vm.notify(
                                                "«${event.title}» movido a ${Dates.dateMedium(days[t2.first])} · ${Dates.time(t2.second)}",
                                                "Deshacer",
                                            ) { previous?.let { vm.saveEvent(it) } }
                                        },
                                    )
                                }

                                // Línea de "ahora"
                                val nowMin = Dates.nowMinutes(now)
                                if (isToday && nowMin in DAY_START..DAY_END) {
                                    Box(Modifier.offset(y = minutesToDp(nowMin - DAY_START) - 4.dp).fillMaxWidth().height(8.dp).zIndex(5f)) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            val y = size.height / 2
                                            drawLine(c.accent, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                                            drawCircle(c.accent, 4.dp.toPx(), Offset(4.dp.toPx(), y))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventBlock(
    event: CalEvent,
    stacked: Boolean,
    teamTint: Color?,
    dragging: Boolean,
    dx: Float,
    dy: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: (Boolean) -> Unit,
) {
    val c = LocalPulso.current
    val cat = c.cat(event.cat)
    val compact = event.duration <= 30
    val showMeta = event.duration >= 90
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(0.6f, Spring.StiffnessMedium), label = "lift")
    val top = minutesToDp(event.start.coerceIn(DAY_START, DAY_END) - DAY_START)
    val height = minutesToDp((minOf(event.end, DAY_END) - maxOf(event.start, DAY_START)).coerceAtLeast(20)) - 4.dp

    Box(
        Modifier
            .offset(y = top)
            .zIndex(if (dragging) 10f else if (stacked) 2f else 1f)
            .padding(start = if (stacked) 26.dp else 4.dp, end = 4.dp, top = 2.dp)
            .fillMaxWidth()
            .height(height)
            .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .graphicsLayer {
                scaleX = 1f + 0.035f * lift
                scaleY = 1f + 0.035f * lift
                rotationZ = -1.6f * lift
                shadowElevation = (2 + 18 * lift).dp.toPx()
                shape = RoundedCornerShape(12.dp)
                clip = true
            }
            .background(c.wash(event.cat))
            .border(1.dp, cat.copy(alpha = if (dragging) 0.6f else 0.25f), RoundedCornerShape(12.dp))
            .drawBehind { drawRect(cat, size = size.copy(width = 4.dp.toPx())) }
            .pointerInput(event.id) {
                detectTapGestures(onTap = { onClick() })
            }
            .pointerInput(event.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd(true) },
                    onDragCancel = { onDragEnd(false) },
                ) { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
            .padding(start = 11.dp, end = 6.dp, top = if (compact) 4.dp else 7.dp),
    ) {
        if (teamTint != null) {
            Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(teamTint))
        }
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Dates.time(event.start), style = Type.sans(12.sp, FontWeight.Bold), color = c.ink)
                Spacer(Modifier.width(5.dp))
                Text(event.title, style = Type.sans(13.sp, FontWeight.SemiBold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Dates.time(event.start), style = Type.sans(12.5.sp, FontWeight.Bold), color = c.ink)
                    Spacer(Modifier.width(5.dp))
                    Text(Dates.duration(event.duration).replace(" min", "m"), style = Type.sans(11.5.sp), color = c.ink3, maxLines = 1)
                    if (event.checklist.isNotEmpty()) {
                        Spacer(Modifier.width(5.dp))
                        Text("${event.checklist.count { it.done }}/${event.checklist.size}", style = Type.sans(11.5.sp), color = c.ink3)
                    }
                }
                Text(event.title, style = Type.sans(14.5.sp, FontWeight.SemiBold, lineHeight = 18.sp), color = c.ink, maxLines = if (showMeta) 2 else 3, overflow = TextOverflow.Ellipsis)
                if (showMeta && event.location.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(LucideData.MapPin, c.ink3, 12.dp)
                        Spacer(Modifier.width(3.dp))
                        Text(event.location, style = Type.sans(12.sp), color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (showMeta && event.note.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(event.note, style = Type.sans(12.sp, lineHeight = 16.sp), color = c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (event.duration >= 60) {
                // Asa de puntos
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 6.dp).alpha(0.45f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(3.dp).clip(CircleShape).background(c.ink2))
                            Box(Modifier.size(3.dp).clip(CircleShape).background(c.ink2))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(vm: AppViewModel, anchor: LocalDate, onOpen: (String) -> Unit, onPickDay: (LocalDate) -> Unit) {
    val c = LocalPulso.current
    val events by vm.events.collectAsState()
    val cells = Dates.monthMatrix(anchor)
    val today = LocalDate.now()
    Column(
        Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x22000000))
            .clip(RoundedCornerShape(24.dp)).background(c.paper2).border(1.dp, c.line, RoundedCornerShape(24.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Dates.WEEKDAYS_MINI.forEach {
                Text(it, style = Type.sans(12.sp, FontWeight.SemiBold, 0.1.sp), color = c.ink3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(92.dp).drawBehind {
                drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }) {
                week.forEachIndexed { i, day ->
                    val date = Dates.iso(day)
                    val dayEvents = events.filter { it.date == date }.sortedBy { it.start }
                    val outside = day.month != anchor.month
                    val isToday = day == today
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .drawBehind { if (i > 0) drawLine(c.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                            .background(if (isToday) c.accent.copy(alpha = 0.05f) else Color.Transparent)
                            .alpha(if (outside) 0.4f else 1f)
                            .press(scaleTo = 0.95f) { onPickDay(day) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${day.dayOfMonth}", style = Type.serif(18.sp), color = if (isToday) c.accentInk else c.ink)
                        }
                        Spacer(Modifier.height(3.dp))
                        dayEvents.take(2).forEach { e ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(5.dp)).background(c.wash(e.cat))
                                    .press(scaleTo = 0.95f) { onOpen(e.id) }.padding(horizontal = 3.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(c.cat(e.cat)))
                                Spacer(Modifier.width(3.dp))
                                Text(e.title, style = Type.sans(10.5.sp, FontWeight.Medium), color = c.ink, maxLines = 1, overflow = TextOverflow.Clip)
                            }
                        }
                        if (dayEvents.size > 2) {
                            Text("+${dayEvents.size - 2} más", style = Type.sans(10.5.sp, FontWeight.SemiBold), color = c.ink3)
                        }
                    }
                }
            }
        }
    }
}
