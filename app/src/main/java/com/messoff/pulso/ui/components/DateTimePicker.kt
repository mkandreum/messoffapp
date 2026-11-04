package com.messoff.pulso.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.haptics.Haptics
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.util.Dates
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private const val WEEK_CENTER = 5000

/**
 * Tira de 7 días que se desliza por semanas. Al tocar el mes se despliega
 * el mes completo, para saltar lejos sin deslizar.
 */
@Composable
fun DayStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit, haptics: Haptics, modifier: Modifier = Modifier) {
    val c = LocalPulso.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val thisMonday = remember { Dates.startOfWeek(today) }
    fun pageOf(day: LocalDate) = WEEK_CENTER + ChronoUnit.WEEKS.between(thisMonday, Dates.startOfWeek(day)).toInt()
    val pager = rememberPagerState(initialPage = pageOf(selected)) { WEEK_CENTER * 2 }
    var monthOpen by remember { mutableStateOf(false) }
    val shownMonday = thisMonday.plusWeeks((pager.currentPage - WEEK_CENTER).toLong())
    // El mes que se ve: el del día elegido si está en esta semana; si no, el de su jueves.
    val shownMonth = if (Dates.startOfWeek(selected) == shownMonday) selected else shownMonday.plusDays(3)

    LaunchedEffect(selected) {
        val target = pageOf(selected)
        if (target != pager.currentPage) pager.animateScrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().drop(1).collect { haptics.play(Haptic.Tick) }
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .press(scaleTo = 0.95f, haptics = haptics) { monthOpen = !monthOpen }
                    .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    Dates.month(shownMonth).replaceFirstChar { it.uppercase() },
                    transitionSpec = { (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 6 }) togetherWith fadeOut(tween(90)) },
                    label = "stripMonth",
                ) { Text(it, style = Type.sans(15.sp, FontWeight.SemiBold), color = c.ink) }
                Spacer(Modifier.width(4.dp))
                val turn by animateFloatAsState(if (monthOpen) 180f else 0f, spring(0.7f, Spring.StiffnessMedium), label = "chevron")
                LucideIcon(LucideData.ChevronDown, c.ink3, 16.dp, 2f, Modifier.graphicsLayer { rotationZ = turn })
            }
            Spacer(Modifier.weight(1f))
            if (selected != today) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).press(haptics = haptics) { onSelect(today) }.padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text("Hoy", style = Type.sans(14.sp, FontWeight.SemiBold), color = c.accent) }
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalPager(pager, Modifier.fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            val monday = thisMonday.plusWeeks((page - WEEK_CENTER).toLong())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0L..6L).forEach { i ->
                    val day = monday.plusDays(i)
                    DayCell(day, day == selected, day == today, Modifier.weight(1f), haptics) { onSelect(day) }
                }
            }
        }
        AnimatedVisibility(
            monthOpen,
            enter = expandVertically(spring(0.85f, Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            MiniMonth(shownMonth, selected, today, haptics) {
                onSelect(it)
                monthOpen = false
                scope.launch { pager.animateScrollToPage(pageOf(it)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, active: Boolean, today: Boolean, modifier: Modifier, haptics: Haptics, onClick: () -> Unit) {
    val c = LocalPulso.current
    val bg by animateColorAsState(if (active) c.ink else androidx.compose.ui.graphics.Color.Transparent, tween(160), label = "dayBg")
    val scale by animateFloatAsState(if (active) 1.04f else 1f, spring(0.55f, Spring.StiffnessMedium), label = "dayScale")
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .press(scaleTo = 0.9f, haptics = haptics, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            Dates.weekdayShort(day).uppercase(),
            style = Type.sans(10.5.sp, FontWeight.SemiBold, 0.06.sp),
            color = if (active) c.onInk else if (today) c.accent else c.ink3,
            maxLines = 1,
        )
        Text("${day.dayOfMonth}", style = Type.serif(23.sp), color = if (active) c.onInk else if (today) c.accent else c.ink, maxLines = 1)
    }
}

@Composable
private fun MiniMonth(month: LocalDate, selected: LocalDate, today: LocalDate, haptics: Haptics, onPick: (LocalDate) -> Unit) {
    val c = LocalPulso.current
    var shown by remember(month) { mutableStateOf(month.withDayOfMonth(1)) }
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).press(scaleTo = 0.85f, haptics = haptics) { shown = shown.minusMonths(1) }, contentAlignment = Alignment.Center) {
                LucideIcon(LucideData.ChevronLeft, c.ink2, 18.dp, 2f)
            }
            Text(
                Dates.month(shown).replaceFirstChar { it.uppercase() },
                style = Type.sans(14.sp, FontWeight.SemiBold), color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(36.dp).press(scaleTo = 0.85f, haptics = haptics) { shown = shown.plusMonths(1) }, contentAlignment = Alignment.Center) {
                LucideIcon(LucideData.ChevronRight, c.ink2, 18.dp, 2f)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Dates.WEEKDAYS_MINI.forEach {
                Text(it, style = Type.sans(11.sp, FontWeight.SemiBold), color = c.ink3, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Dates.monthMatrix(shown).chunked(7).forEach { week ->
            if (week.first().month == shown.month || week.last().month == shown.month) {
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val inMonth = day.month == shown.month
                        Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                            if (inMonth) {
                                Box(
                                    Modifier.size(34.dp).clip(RoundedCornerShape(999.dp))
                                        .background(if (day == selected) c.ink else androidx.compose.ui.graphics.Color.Transparent)
                                        .press(scaleTo = 0.85f, haptics = haptics) { onPick(day) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${day.dayOfMonth}",
                                        style = Type.sans(14.5.sp, if (day == today) FontWeight.Bold else FontWeight.Medium),
                                        color = if (day == selected) c.onInk else if (day == today) c.accent else c.ink,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val WHEEL_ROW = 54.dp
private const val WHEEL_ROWS = 3

/**
 * Hora digital con dos ruedas. Se arrastran con inercia y encajan en cada valor,
 * con un tic por muesca. Tocar el número central abre el teclado para una hora exacta.
 */
@Composable
fun TimeWheels(minutes: Int, onChange: (Int) -> Unit, haptics: Haptics, modifier: Modifier = Modifier) {
    val c = LocalPulso.current
    val hour = minutes / 60
    val minute = minutes % 60
    var editing by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(minutes)
    // Una hora escrita a mano (19:37) entra también en la rueda de 5 en 5.
    val minuteValues = remember(minute % 5 != 0, if (minute % 5 != 0) minute else 0) {
        ((0..55 step 5).toList() + minute).distinct().sorted()
    }

    Box(
        modifier.fillMaxWidth().height(WHEEL_ROW * WHEEL_ROWS).clip(RoundedCornerShape(20.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(WHEEL_ROW).clip(RoundedCornerShape(14.dp)).background(c.paper3))
        if (editing) {
            TimeEntry(minutes, onDone = { typed ->
                editing = false
                if (typed != null) {
                    haptics.play(Haptic.Selection)
                    onChange(typed)
                }
            })
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Wheel((0..23).toList(), hour, { onChange(it * 60 + latest % 60) }, haptics, Modifier.width(104.dp), wrapStrong = true) { editing = true }
                Text(":", style = Type.serif(40.sp), color = c.accent, modifier = Modifier.padding(bottom = 4.dp))
                Wheel(minuteValues, minute, { onChange(latest / 60 * 60 + it) }, haptics, Modifier.width(104.dp), wrapStrong = false) { editing = true }
            }
        }
    }
}

@Composable
private fun Wheel(
    values: List<Int>,
    value: Int,
    onValue: (Int) -> Unit,
    haptics: Haptics,
    modifier: Modifier,
    wrapStrong: Boolean,
    onTapCenter: () -> Unit,
) {
    val c = LocalPulso.current
    val view = LocalView.current
    val size = values.size
    val center = remember(size) { (Int.MAX_VALUE / 2) / size * size }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = center + values.indexOf(value).coerceAtLeast(0) - 1)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val emit by rememberUpdatedState(onValue)

    fun centerIndex(): Int {
        val info = state.layoutInfo
        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
        return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index ?: state.firstVisibleItemIndex + 1
    }
    fun valueAt(index: Int) = values[index.mod(size)]

    // Cada valor que cruza el centro: un tic y el cambio en vivo.
    LaunchedEffect(state, size) {
        var previous = valueAt(centerIndex())
        snapshotFlow { centerIndex() }.distinctUntilChanged().drop(1).collect { index ->
            val next = valueAt(index)
            val wrapped = wrapStrong && abs(next - previous) > 1
            haptics.detent(view, strong = wrapped)
            previous = next
            emit(next)
        }
    }
    // Al encajar: un toque seco que confirma.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().drop(1).filter { !it }.collect {
            haptics.play(Haptic.Light)
        }
    }
    // Si el valor cambia desde fuera (al escribir "19:30"), la rueda gira sola.
    LaunchedEffect(value, size) {
        if (!state.isScrollInProgress && valueAt(centerIndex()) != value) {
            val current = centerIndex()
            val from = current.mod(size)
            val to = values.indexOf(value).coerceAtLeast(0)
            var delta = to - from
            if (delta > size / 2) delta -= size
            if (delta < -size / 2) delta += size
            state.animateScrollToItem(current + delta - 1)
        }
    }

    LazyColumn(modifier.height(WHEEL_ROW * WHEEL_ROWS), state = state, flingBehavior = fling, horizontalAlignment = Alignment.CenterHorizontally) {
        items(Int.MAX_VALUE) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WHEEL_ROW)
                    .graphicsLayer {
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val distance = if (item != null) (item.offset + item.size / 2f - middle) / item.size else 1f
                        val away = abs(distance).coerceAtMost(1.6f)
                        scaleX = 1f - 0.2f * away
                        scaleY = 1f - 0.2f * away
                        alpha = (1f - 0.62f * away).coerceAtLeast(0.08f)
                        rotationX = -26f * distance.coerceIn(-1.6f, 1.6f)
                        cameraDistance = 14f * density
                    }
                    .press(scaleTo = 1f) {
                        if (index == centerIndex()) onTapCenter() else scope.launch { state.animateScrollToItem(index - 1) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(valueAt(index).toString().padStart(2, '0'), style = Type.serif(44.sp), color = c.ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TimeEntry(minutes: Int, onDone: (Int?) -> Unit) {
    val c = LocalPulso.current
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf(Dates.time(minutes).replace(":", "")) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun finish() {
        val digits = text.padStart(3, '0').padStart(4, '0')
        val h = digits.take(2).toIntOrNull()
        val m = digits.takeLast(2).toIntOrNull()
        onDone(if (h != null && m != null && h in 0..23 && m in 0..59) h * 60 + m else null)
    }

    BasicTextField(
        text,
        { next ->
            val digits = next.filter(Char::isDigit).take(4)
            text = digits
            if (digits.length == 4) finish()
        },
        singleLine = true,
        textStyle = Type.serif(44.sp).copy(color = c.ink, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish() }),
        modifier = Modifier.width(200.dp).focusRequester(focus),
    )
}
