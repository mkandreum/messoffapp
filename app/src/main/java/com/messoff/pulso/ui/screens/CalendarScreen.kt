package com.messoff.pulso.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.data.CalEvent
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.haptics.Haptics
import com.messoff.pulso.ui.AppHeader
import com.messoff.pulso.ui.components.dashedBorder
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.ui.theme.teamColor
import com.messoff.pulso.util.Dates
import com.messoff.pulso.util.DayLayout
import com.messoff.pulso.util.Placement
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val DAY_START = 0
private const val DAY_END = 24 * 60
private const val SNAP = 15
private const val PAGE_CENTER = 50_000
private val HOUR_H = 68.dp
private val GUTTER = 54.dp
private val BOTTOM_ROOM = 120.dp

enum class CalView(val label: String) { Dia("Día"), Semana("Semana"), Agenda("Agenda"), Mes("Mes") }

/** La página del carrusel en la que cae un día, según la vista. */
private fun pageOf(view: CalView, day: LocalDate, today: LocalDate): Int = PAGE_CENTER + when (view) {
    CalView.Mes -> ChronoUnit.MONTHS.between(today.withDayOfMonth(1), day.withDayOfMonth(1)).toInt()
    CalView.Dia -> ChronoUnit.DAYS.between(today, day).toInt()
    else -> ChronoUnit.WEEKS.between(Dates.startOfWeek(today), Dates.startOfWeek(day)).toInt()
}

private fun dayOf(view: CalView, page: Int, today: LocalDate): LocalDate {
    val step = (page - PAGE_CENTER).toLong()
    return when (view) {
        CalView.Mes -> today.withDayOfMonth(1).plusMonths(step)
        CalView.Dia -> today.plusDays(step)
        else -> today.plusWeeks(step)
    }
}

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
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var view by rememberSaveable {
        mutableStateOf(CalView.entries.firstOrNull { it.name.lowercase() == vm.calViewName } ?: CalView.Agenda)
    }
    var anchorEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var direction by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var agendaJump by remember { mutableStateOf<LocalDate?>(null) }
    var stripJump by remember { mutableStateOf<LocalDate?>(null) }
    val anchor = LocalDate.ofEpochDay(anchorEpoch)

    val pager = key(view) { rememberPagerState(initialPage = pageOf(view, anchor, today)) { PAGE_CENTER * 2 } }

    // Lo que se ve manda: la fecha cambia mientras el dedo pasa de página.
    LaunchedEffect(pager, view) {
        if (view == CalView.Agenda) return@LaunchedEffect
        snapshotFlow { pager.currentPage }.distinctUntilChanged().drop(1).collect { page ->
            vm.haptics.play(Haptic.Selection)
            val next = dayOf(view, page, today)
            direction = if (next.isBefore(LocalDate.ofEpochDay(anchorEpoch))) -1 else 1
            anchorEpoch = next.toEpochDay()
        }
    }

    fun shift(step: Int) {
        vm.haptics.play(Haptic.Selection)
        direction = step
        if (view == CalView.Agenda) {
            agendaJump = anchor.plusWeeks(step.toLong())
        } else {
            scope.launch { pager.animateScrollToPage(pager.currentPage + step) }
        }
    }
    fun goToday() {
        vm.haptics.play(Haptic.Selection)
        direction = if (anchor.isAfter(today)) -1 else 1
        if (view == CalView.Agenda) {
            agendaJump = today
        } else {
            scope.launch { pager.animateScrollToPage(pageOf(view, today, today)) }
        }
        anchorEpoch = today.toEpochDay()
        stripJump = today
    }
    fun pickDay(day: LocalDate) {
        vm.haptics.play(Haptic.Selection)
        anchorEpoch = day.toEpochDay()
        view = CalView.Dia
        vm.calViewName = view.name.lowercase()
    }

    val monday = Dates.startOfWeek(anchor)
    val label = when (view) {
        CalView.Mes -> Dates.month(anchor).replaceFirstChar { it.uppercase() }
        CalView.Dia -> Dates.dateMedium(anchor)
        CalView.Semana -> Dates.weekRange(monday)
        CalView.Agenda -> Dates.month(anchor).replaceFirstChar { it.uppercase() }
    }
    val sub = if (view == CalView.Mes) "VISTA MENSUAL" else "SEMANA ${Dates.weekNumber(anchor)} · ${anchor.year}"
    val eyebrow = when (view) {
        CalView.Mes -> Dates.month(anchor)
        CalView.Dia -> Dates.dateLong(anchor)
        CalView.Semana, CalView.Agenda -> "Semana ${Dates.weekNumber(anchor)} · ${Dates.MONTHS[anchor.monthValue - 1]}"
    }

    // La cabecera grande se recoge al bajar y solo queda lo que hace falta para orientarse.
    var headerHeight by remember { mutableIntStateOf(0) }
    var collapsed by remember { mutableFloatStateOf(0f) }
    val collapse = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val next = (collapsed - available.y).coerceAtMost(headerHeight.toFloat())
                val used = next - collapsed
                collapsed = next
                return Offset(0f, -used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val next = (collapsed - available.y).coerceAtLeast(0f)
                val used = collapsed - next
                collapsed = next
                return Offset(0f, used)
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().nestedScroll(collapse)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                    headerHeight = placeable.height
                    val shift = collapsed.roundToInt().coerceAtMost(placeable.height)
                    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
                },
        ) {
            AppHeader(
                vm, eyebrow, "Tu", when (view) { CalView.Dia -> "día"; CalView.Semana -> "semana"; CalView.Agenda -> "agenda"; CalView.Mes -> "mes" },
                subtitle = {
                    Text(
                        when (view) {
                            CalView.Mes -> "Toca un día para abrirlo. Desliza para cambiar de mes."
                            CalView.Dia -> "Desliza para cambiar de día. Toca un hueco para crear."
                            CalView.Semana -> "Mantén pulsado para mover. Tira del borde para alargar."
                            CalView.Agenda -> "Lo que viene, día a día. Toca un evento para abrirlo."
                        },
                        style = Type.body, color = c.ink2,
                    )
                },
                onSearch = onSearch, onSettings = onSettings,
            )
            InvitationBanner(vm, onTeams)
            NextUpNote(vm, onOpen)
            Spacer(Modifier.height(8.dp))
            CalendarChips(vm, onTeams)
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            // Píldora de fecha
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(999.dp)).background(c.paper2)
                    .border(1.dp, c.line, RoundedCornerShape(999.dp)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(46.dp).press(scaleTo = 0.85f) { shift(-1) }, contentAlignment = Alignment.Center) {
                    LucideIcon(LucideData.ChevronLeft, c.ink, 22.dp, 1.9f)
                }
                Column(
                    Modifier.weight(1f).clipToBounds().press(scaleTo = 0.97f) { goToday() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(
                        label,
                        transitionSpec = {
                            (slideInHorizontally(spring(0.8f, 600f)) { direction * it / 5 } + fadeIn(tween(160))) togetherWith
                                fadeOut(tween(90)) using SizeTransform(clip = true) { _, _ -> snap() }
                        },
                        label = "dateLabel",
                    ) { text -> Text(text, style = Type.sans(17.sp, FontWeight.SemiBold), color = c.ink, maxLines = 1, softWrap = false) }
                    Text(sub, style = Type.sans(11.sp, FontWeight.Medium, 0.1.sp), color = c.ink3, maxLines = 1, softWrap = false)
                }
                Box(Modifier.size(46.dp).press(scaleTo = 0.85f) { shift(1) }, contentAlignment = Alignment.Center) {
                    LucideIcon(LucideData.ChevronRight, c.ink, 22.dp, 1.9f)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Selector de vista con indicador deslizante
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(999.dp)).background(c.paper3).border(1.dp, c.line, RoundedCornerShape(999.dp)).padding(4.dp),
                ) {
                    val w = maxWidth / CalView.entries.size
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
                    Modifier.height(44.dp).clip(RoundedCornerShape(999.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(999.dp))
                        .press(onClick = ::goToday).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Hoy", style = Type.sans(15.sp, FontWeight.Medium), color = c.ink) }
            }
            Spacer(Modifier.height(8.dp))
            if (view == CalView.Semana) {
                WeekStrip(vm, Dates.weekDays(anchor), today) { stripJump = it }
                Spacer(Modifier.height(8.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            if (view == CalView.Agenda) {
                AgendaList(
                    vm, agendaJump, onJumped = { agendaJump = null },
                    onTopDay = { anchorEpoch = it.toEpochDay() }, onOpen = onOpen, onSlot = onSlot,
                )
            } else {
                // Las páginas comparten el scroll vertical: pasar de día no te mueve de hora.
                val vScroll = key(view) { rememberScrollState() }
                val density = LocalDensity.current
                LaunchedEffect(view) {
                    val focus = (Dates.nowMinutes() - 90).coerceIn(7 * 60, DAY_END - 4 * 60)
                    vScroll.scrollTo(with(density) { minutesToDp(focus - DAY_START).roundToPx() })
                }
                HorizontalPager(
                    pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, pageSpacing = 12.dp,
                    userScrollEnabled = !dragging, key = { "$view-$it" },
                ) { page ->
                    val day = dayOf(view, page, today)
                    when (view) {
                        CalView.Mes -> MonthGrid(vm, day, onOpen, ::pickDay)
                        CalView.Dia -> WeekGrid(vm, listOf(day), vScroll, page < pager.currentPage, null, { dragging = it }, onOpen, onSlot, ::pickDay)
                        else -> WeekGrid(vm, Dates.weekDays(day), vScroll, page < pager.currentPage, stripJump, { dragging = it }, onOpen, onSlot, ::pickDay)
                    }
                }
            }
        }
    }
}

/** Los 7 días de la semana de un vistazo, con un punto si hay algo. Tocar lleva a ese día. */
@Composable
private fun WeekStrip(vm: AppViewModel, days: List<LocalDate>, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = LocalPulso.current
    val events by vm.events.collectAsState()
    val busy = remember(events) { events.mapTo(HashSet()) { it.date } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { day ->
            val isToday = day == today
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (isToday) c.accent.copy(alpha = 0.1f) else Color.Transparent)
                    .press(scaleTo = 0.9f, haptics = vm.haptics) { onPick(day) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Dates.WEEKDAYS_MINI[day.dayOfWeek.value - 1], style = Type.sans(10.5.sp, FontWeight.SemiBold), color = if (isToday) c.accent else c.ink3)
                Text("${day.dayOfMonth}", style = Type.sans(15.sp, if (isToday) FontWeight.Bold else FontWeight.Medium), color = if (isToday) c.accent else c.ink)
                Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(if (Dates.iso(day) in busy) c.ink3 else Color.Transparent))
            }
        }
    }
}

private fun minutesToDp(minutes: Int): Dp = HOUR_H * (minutes / 60f)

@Composable
private fun WeekGrid(
    vm: AppViewModel,
    days: List<LocalDate>,
    vScroll: androidx.compose.foundation.ScrollState,
    startAtEnd: Boolean,
    jumpTo: LocalDate?,
    onDragging: (Boolean) -> Unit,
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
    // Una semana a la que se vuelve hacia atrás se abre por su final, para seguir el gesto.
    val hScroll = rememberScrollState(if (startAtEnd) Int.MAX_VALUE else 0)
    val totalH = minutesToDp(DAY_END - DAY_START)
    val colPxRef = remember { floatArrayOf(1f) }

    // Estado del arrastre: el desplazamiento incluye lo que la rejilla se ha movido sola.
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableIntStateOf(0) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var startH by remember { mutableIntStateOf(0) }
    var startV by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var bodySize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    fun effectiveX() = dragDx + (hScroll.value - startH)
    fun effectiveY() = dragDy + (vScroll.value - startV)

    fun updateTarget(event: CalEvent) {
        val hourPx = with(density) { HOUR_H.toPx() }
        val idx = (dragOrigin + (effectiveX() / colPxRef[0]).roundToInt()).coerceIn(0, days.lastIndex)
        val start = ((event.start + effectiveY() / hourPx * 60) / SNAP).roundToInt() * SNAP
        val next = idx to start.coerceIn(DAY_START, DAY_END - SNAP)
        if (next != target) {
            target = next
            vm.haptics.tick()
        }
    }

    // Cerca de un borde, la rejilla se mueve sola mientras arrastras.
    LaunchedEffect(dragId) {
        val id = dragId ?: return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        val hourPx = with(density) { HOUR_H.toPx() }
        while (true) {
            withFrameMillis { }
            val event = events.firstOrNull { it.id == id } ?: break
            val y = (event.start - DAY_START) / 60f * hourPx + effectiveY() - vScroll.value
            val speedV = when {
                y < edge -> -9f
                y > bodySize.height - edge -> 9f
                else -> 0f
            }
            if (speedV != 0f) vScroll.scrollBy(speedV)
            val x = (dragOrigin + 0.5f) * colPxRef[0] + effectiveX() - hScroll.value
            val speedH = when {
                x < edge -> -10f
                x > bodySize.width - edge -> 10f
                else -> 0f
            }
            if (speedH != 0f && days.size > 1) hScroll.scrollBy(speedH)
            if (speedV != 0f || speedH != 0f) updateTarget(event)
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(c.paper2)
            .border(1.dp, c.line, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
    ) {
        val colW = if (days.size == 1) maxWidth - GUTTER else maxOf(96.dp, (maxWidth - GUTTER) / 3.25f)
        colPxRef[0] = with(density) { colW.toPx() }
        val todayIndex = days.indexOf(today)

        LaunchedEffect(days.first()) {
            if (todayIndex > 0 && days.size > 3) hScroll.scrollTo(with(density) { (colW * todayIndex - 20.dp).roundToPx().coerceAtLeast(0) })
        }
        LaunchedEffect(jumpTo) {
            val index = jumpTo?.let { days.indexOf(it) } ?: -1
            if (index >= 0) hScroll.animateScrollTo(with(density) { (colW * index).roundToPx() })
        }

        Column {
            // Cabecera de días
            Row(Modifier.fillMaxWidth().height(44.dp).drawBehind {
                drawLine(c.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }) {
                Box(Modifier.width(GUTTER).fillMaxHeight().drawBehind {
                    drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                }, contentAlignment = Alignment.Center) {
                    Text(Dates.gmtLabel(), style = Type.sans(10.sp, FontWeight.Medium), color = c.ink3)
                }
                Row(Modifier.horizontalScroll(hScroll, enabled = dragId == null)) {
                    days.forEach { day ->
                        val isToday = day == today
                        Row(
                            Modifier.width(colW).fillMaxHeight().background(if (isToday) c.accent.copy(alpha = 0.04f) else Color.Transparent)
                                .press(scaleTo = 0.96f) { onPickDay(day) },
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Dates.weekdayShort(day).uppercase(), style = Type.sans(12.sp, FontWeight.SemiBold, 0.1.sp), color = if (isToday) c.accent else c.ink3)
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${day.dayOfMonth}", style = Type.serif(21.sp), color = if (isToday) c.accentInk else c.ink)
                            }
                        }
                    }
                }
            }

            // Cuerpo: llega hasta abajo, con hueco para que nada quede bajo la barra.
            Box(Modifier.fillMaxSize().onSizeChanged { bodySize = it }.verticalScroll(vScroll, enabled = dragId == null)) {
                Row(Modifier.height(totalH + BOTTOM_ROOM)) {
                    // Horas
                    Box(Modifier.width(GUTTER).height(totalH).drawBehind {
                        drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                    }) {
                        for (h in 0 until 24) {
                            Text(
                                "${h.toString().padStart(2, '0')}:00",
                                style = Type.serif(13.sp, italic = true), color = c.ink3,
                                modifier = Modifier.offset(x = 8.dp, y = minutesToDp(h * 60 - DAY_START) + if (h == 0) 2.dp else (-9).dp),
                            )
                        }
                    }
                    Row(Modifier.horizontalScroll(hScroll, enabled = dragId == null)) {
                        days.forEachIndexed { dayIndex, day ->
                            val date = Dates.iso(day)
                            val dayEvents = remember(events, date) { events.filter { it.date == date } }
                            val placed = remember(dayEvents) { DayLayout.place(dayEvents) }
                            val isToday = day == today
                            val holdsDrag = dragId != null && dayEvents.any { it.id == dragId }
                            Box(
                                Modifier
                                    .width(colW)
                                    .height(totalH)
                                    // La columna del evento arrastrado va por encima de las demás.
                                    .zIndex(if (holdsDrag) 1f else 0f)
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
                                        placement = placed[event.id] ?: Placement(0, 1),
                                        colWidth = colW,
                                        haptics = vm.haptics,
                                        teamTint = teams.firstOrNull { it.id == event.teamId }?.let { teamColor(it.color, c.night) },
                                        dragging = isDragging,
                                        dx = if (isDragging) effectiveX() else 0f,
                                        dy = if (isDragging) effectiveY() else 0f,
                                        onClick = { onOpen(event.id) },
                                        onDragStart = {
                                            vm.haptics.play(Haptic.Medium)
                                            dragId = event.id
                                            dragOrigin = dayIndex
                                            dragDx = 0f
                                            dragDy = 0f
                                            startH = hScroll.value
                                            startV = vScroll.value
                                            target = dayIndex to event.start
                                            onDragging(true)
                                        },
                                        onDrag = { x, y ->
                                            dragDx += x
                                            dragDy += y
                                            updateTarget(event)
                                        },
                                        onDragEnd = { commit ->
                                            val t2 = target
                                            dragId = null
                                            target = null
                                            dragDx = 0f
                                            dragDy = 0f
                                            onDragging(false)
                                            if (!commit || t2 == null) {
                                                vm.haptics.play(Haptic.Warning)
                                                return@EventBlock
                                            }
                                            val undo = vm.moveEvent(event.id, Dates.iso(days[t2.first]), t2.second)
                                            if (undo == null) {
                                                vm.haptics.play(Haptic.Light)
                                                return@EventBlock
                                            }
                                            vm.haptics.play(Haptic.Snap)
                                            vm.notify(
                                                "«${event.title}» movido a ${Dates.dateMedium(days[t2.first])} · ${Dates.time(t2.second)}",
                                                "Deshacer",
                                            ) { undo() }
                                        },
                                        onResize = { duration ->
                                            val undo = vm.resizeEvent(event.id, duration)
                                            if (undo != null) {
                                                vm.haptics.play(Haptic.Snap)
                                                vm.notify("«${event.title}» dura ${Dates.duration(duration)}", "Deshacer") { undo() }
                                            }
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
    placement: Placement,
    colWidth: Dp,
    haptics: Haptics,
    teamTint: Color?,
    dragging: Boolean,
    dx: Float,
    dy: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    onResize: (Int) -> Unit,
) {
    val c = LocalPulso.current
    val density = LocalDensity.current
    val cat = c.cat(event.cat)
    // Mientras se estira el borde, la tarjeta ya enseña la duración nueva.
    var stretch by remember(event.id, event.duration) { mutableFloatStateOf(0f) }
    var resizing by remember { mutableStateOf(false) }
    val hourPx = with(density) { HOUR_H.toPx() }
    val shownDuration = (((event.duration + stretch / hourPx * 60) / SNAP).roundToInt() * SNAP)
        .coerceIn(SNAP, DAY_END - event.start)
    val compact = shownDuration <= 30
    val showMeta = shownDuration >= 90 && placement.columns == 1
    val lift by animateFloatAsState(if (dragging || resizing) 1f else 0f, spring(0.6f, Spring.StiffnessMedium), label = "lift")
    val top = minutesToDp(event.start - DAY_START)
    val height = minutesToDp(shownDuration.coerceAtLeast(20)) - 4.dp
    val width = colWidth / placement.columns

    Box(
        Modifier
            .offset(x = width * placement.column, y = top)
            .zIndex(if (dragging || resizing) 10f else 1f + placement.column)
            .width(width)
            .padding(start = if (placement.column == 0) 4.dp else 1.dp, end = if (placement.column == placement.columns - 1) 4.dp else 1.dp, top = 2.dp)
            .height(height)
            .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .graphicsLayer {
                scaleX = 1f + 0.035f * lift
                scaleY = 1f + (if (resizing) 0f else 0.035f) * lift
                rotationZ = if (resizing) 0f else -1.6f * lift
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
            },
    ) {
        Box(Modifier.padding(start = 11.dp, end = 6.dp, top = if (compact) 4.dp else 7.dp)) {
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
                        Text(Dates.duration(shownDuration).replace(" min", "m"), style = Type.sans(11.5.sp), color = c.ink3, maxLines = 1)
                        if (event.isRepeating) {
                            Spacer(Modifier.width(5.dp))
                            LucideIcon(LucideData.Repeat2, c.ink3, 12.dp)
                        }
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
            }
        }
        // Asa inferior: tirar de ella cambia la duración, de 15 en 15 minutos.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(16.dp)
                .pointerInput(event.id, event.duration) {
                    var shown = event.duration
                    detectVerticalDragGestures(
                        onDragStart = {
                            resizing = true
                            haptics.play(Haptic.Medium)
                        },
                        onDragEnd = {
                            resizing = false
                            val next = ((event.duration + stretch / hourPx * 60) / SNAP).roundToInt() * SNAP
                            onResize(next.coerceIn(SNAP, DAY_END - event.start))
                        },
                        onDragCancel = {
                            resizing = false
                            stretch = 0f
                        },
                    ) { change, amount ->
                        change.consume()
                        stretch += amount
                        val next = ((event.duration + stretch / hourPx * 60) / SNAP).roundToInt() * SNAP
                        if (next != shown) {
                            shown = next
                            haptics.tick()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!compact) Box(Modifier.alpha(0.45f).size(22.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(c.ink2))
        }
    }
}

@Composable
private fun MonthGrid(vm: AppViewModel, anchor: LocalDate, onOpen: (String) -> Unit, onPickDay: (LocalDate) -> Unit) {
    val c = LocalPulso.current
    val events by vm.events.collectAsState()
    val cells = Dates.monthMatrix(anchor)
    val today = LocalDate.now()
    val byDate = remember(events) { events.groupBy { it.date } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.paper2).border(1.dp, c.line, RoundedCornerShape(24.dp)),
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
                        val dayEvents = byDate[Dates.iso(day)].orEmpty().sortedBy { it.start }
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
        Spacer(Modifier.height(BOTTOM_ROOM))
    }
}
