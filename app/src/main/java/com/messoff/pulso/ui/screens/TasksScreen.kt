package com.messoff.pulso.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.data.Category
import com.messoff.pulso.data.Task
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.ui.AppHeader
import com.messoff.pulso.ui.components.CategoryDot
import com.messoff.pulso.ui.components.Panel
import com.messoff.pulso.ui.components.PanelHead
import com.messoff.pulso.ui.components.PulsoChip
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.util.Dates
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Filter(val label: String) { Todas("Todas"), Hoy("Hoy"), Semana("Esta semana"), Hechas("Hechas") }

@Composable
fun TasksScreen(vm: AppViewModel, onSearch: () -> Unit, onSettings: () -> Unit) {
    val c = LocalPulso.current
    val tasks by vm.tasks.collectAsState()
    var filter by rememberSaveable { mutableStateOf(Filter.Todas) }
    var draft by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf(Category.Personal) }

    val today = Dates.iso(Dates.today())
    val tomorrow = Dates.iso(Dates.today().plusDays(1))
    val weekEnd = Dates.iso(Dates.today().plusDays(6))
    val pending = tasks.filter { !it.done }
    val completed = tasks.filter { it.done }
    val visible = when (filter) {
        Filter.Hechas -> completed
        Filter.Hoy -> pending.filter { it.due != null && it.due <= today }
        Filter.Semana -> pending.filter { it.due != null && it.due in today..weekEnd }
        Filter.Todas -> pending
    }
    val groups = listOf("Atrasadas", "Hoy", "Mañana", "Esta semana", "Más adelante", "Sin fecha").mapNotNull { key ->
        val items = visible.filter { t ->
            val d = t.due
            when (key) {
                "Atrasadas" -> d != null && d < today
                "Hoy" -> d == today
                "Mañana" -> d == tomorrow
                "Esta semana" -> d != null && d > tomorrow && d <= weekEnd
                "Más adelante" -> d != null && d > weekEnd
                else -> d == null
            }
        }
        if (items.isEmpty()) null else key to items
    }
    val progress = if (tasks.isEmpty()) 0f else completed.size.toFloat() / tasks.size
    val animatedProgress by animateFloatAsState(progress, spring(0.8f, 120f), label = "progress")

    fun submit() {
        val title = draft.trim()
        if (title.isEmpty()) return
        vm.addTask(title, today, tag)
        draft = ""
        vm.haptics.play(Haptic.Success)
        vm.notify("Tarea añadida a la bandeja")
    }

    ScreenList {
        item {
            AppHeader(
                vm, "${completed.size} ${if (completed.size == 1) "completada" else "completadas"}", "Tu", "bandeja",
                subtitle = {
                    Text("${pending.size} ${if (pending.size == 1) "pendiente" else "pendientes"} · elige la siguiente cosa pequeña.", style = Type.body, color = c.ink2)
                },
                onSearch = onSearch, onSettings = onSettings,
            )
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    // Nueva tarea
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LucideIcon(LucideData.Plus, c.accent, 20.dp, 2f)
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f)) {
                                if (draft.isEmpty()) Text("Añadir algo pequeño, concreto…", style = Type.sans(16.sp), color = c.ink3)
                                BasicTextField(
                                    draft, { draft = it }, singleLine = true,
                                    textStyle = Type.sans(16.sp).copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submit() }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TagPicker(vm, tag) { tag = it }
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(if (draft.isBlank()) c.ink3.copy(alpha = 0.5f) else c.ink)
                                    .press(enabled = draft.isNotBlank(), onClick = ::submit).padding(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text("Añadir", style = Type.sans(15.sp, FontWeight.SemiBold), color = c.onInk) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Filter.entries.forEach { f ->
                            val count = when (f) { Filter.Todas -> pending.size; Filter.Hechas -> completed.size; else -> null }
                            PulsoChip(if (count != null) "${f.label}  $count" else f.label, filter == f, { filter = f }, haptics = vm.haptics)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (groups.isEmpty()) {
                        com.messoff.pulso.ui.components.EmptyState(
                            "✓", "Nada por aquí.",
                            if (filter == Filter.Hechas) "Aún no has cerrado nada. Todo llega." else "La bandeja está en calma. Disfruta el hueco.",
                        )
                    }
                    groups.forEach { (key, items) ->
                        Row(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(key.uppercase(), style = Type.label, color = if (key == "Atrasadas") c.accent else c.ink2)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.clip(CircleShape).background(c.paper3).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("${items.size}", style = Type.sans(12.sp, FontWeight.SemiBold), color = c.ink2)
                            }
                        }
                        items.forEach { task ->
                            androidx.compose.runtime.key(task.id) {
                                TaskRow(vm, task, onSchedule = { vm.scheduleTask(task.id) }, swipeable = true, editable = true)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.paper3)) {
                            Box(Modifier.fillMaxWidth(animatedProgress).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.accent))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${completed.size} de ${tasks.size} · ${(progress * 100).roundToInt()}%", style = Type.sans(13.sp), color = c.ink3)
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    PanelHead("La semana en tareas")
                    Spacer(Modifier.height(12.dp))
                    Row {
                        MiniStat("Pendientes", pending.size, Modifier.weight(1f))
                        MiniStat("Atrasadas", pending.count { it.due != null && it.due < today }, Modifier.weight(1f))
                        MiniStat("Hechas", completed.size, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(LucideData.Check, c.green, 15.dp, 2f)
                        Spacer(Modifier.width(8.dp))
                        Text("Desliza a la derecha para completar, a la izquierda para eliminar. Toca una tarea para editarla.", style = Type.bodySmall, color = c.ink2)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int, modifier: Modifier) {
    val c = LocalPulso.current
    Column(modifier) {
        Text(label.uppercase(), style = Type.label, color = c.ink3)
        Text("$value", style = Type.serif(34.sp), color = c.ink)
    }
}

@Composable
fun TagPicker(vm: AppViewModel, selected: Category, onSelect: (Category) -> Unit) {
    val c = LocalPulso.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Category.entries.forEach { cat ->
            val active = cat == selected
            val ring by animateColorAsState(if (active) c.ink else androidx.compose.ui.graphics.Color.Transparent, label = "ring")
            Box(
                Modifier.size(34.dp).clip(CircleShape).border(2.dp, ring, CircleShape).padding(4.dp).clip(CircleShape)
                    .background(c.cat(cat)).press(scaleTo = 0.85f, haptics = vm.haptics) { onSelect(cat) },
                contentAlignment = Alignment.Center,
            ) {
                if (active) Box(Modifier.size(8.dp).clip(CircleShape).background(c.paper2))
            }
        }
    }
}

private const val THRESHOLD = 90f

@Composable
fun TaskRow(
    vm: AppViewModel,
    task: Task,
    onSchedule: (() -> Unit)? = null,
    swipeable: Boolean = false,
    editable: Boolean = false,
) {
    val c = LocalPulso.current
    val scope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    val offset = remember(task.id) { Animatable(0f) }
    var armed by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var title by remember(task.title) { mutableStateOf(task.title) }
    val today = Dates.iso(Dates.today())
    val overdue = task.due != null && !task.done && task.due < today
    val dueLabel = task.due?.let { Dates.relativeDay(Dates.parse(it)) } ?: "Sin fecha"
    val px = offset.value / localDensity.density

    fun toggle() {
        vm.haptics.play(if (task.done) Haptic.Light else Haptic.Success)
        vm.toggleTask(task.id)
    }

    fun remove() {
        vm.haptics.play(Haptic.Error)
        val removed = vm.deleteTask(task.id) ?: return
        vm.notify("«${task.title}» eliminada", "Deshacer") { vm.restoreTask(removed) }
    }

    fun commitTitle() {
        val next = title.replace(Regex("\\s+"), " ").trim()
        if (next.isNotEmpty() && next != task.title) vm.updateTask(task.id, title = next) else title = task.title
    }

    Column {
        Box {
            if (swipeable && px != 0f) {
                Row(
                    Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).height(56.dp).background(c.personalWash).alpha((px / THRESHOLD).coerceIn(0f, 1f)).padding(start = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LucideIcon(LucideData.Check, c.personal, 20.dp, 2.4f, Modifier.scale(if (armed == 1) 1.2f else 0.9f))
                        Spacer(Modifier.width(8.dp))
                        Text(if (task.done) "Pendiente" else "Hecha", style = Type.sans(14.sp, FontWeight.SemiBold), color = c.personal)
                    }
                    Row(
                        Modifier.weight(1f).height(56.dp).background(c.accentSoft).alpha((-px / THRESHOLD).coerceIn(0f, 1f)).padding(end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End,
                    ) {
                        Text("Eliminar", style = Type.sans(14.sp, FontWeight.SemiBold), color = c.accent)
                        Spacer(Modifier.width(8.dp))
                        LucideIcon(LucideData.Trash2, c.accent, 20.dp, 2f, Modifier.scale(if (armed == -1) 1.2f else 0.9f))
                    }
                }
            }
            Row(
                Modifier
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .fillMaxWidth()
                    .background(c.paper2)
                    .then(
                        if (swipeable && !editing) Modifier.pointerInput(task.id, task.done) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val action = armed
                                    armed = 0
                                    scope.launch { offset.animateTo(0f, spring(0.6f, Spring.StiffnessMedium)) }
                                    if (action == 1) toggle() else if (action == -1) remove()
                                },
                                onDragCancel = {
                                    armed = 0
                                    scope.launch { offset.animateTo(0f) }
                                },
                            ) { change, amount ->
                                change.consume()
                                // Resistencia elástica pasado el umbral
                                val current = offset.value / density
                                val factor = if (abs(current) > THRESHOLD) 0.45f else 1f
                                scope.launch { offset.snapTo(offset.value + amount * factor) }
                                val dp = (offset.value + amount * factor) / density
                                val next = if (dp > THRESHOLD) 1 else if (dp < -THRESHOLD) -1 else 0
                                if (next != armed) {
                                    armed = next
                                    vm.haptics.play(if (next == 0) Haptic.Tick else Haptic.Swipe)
                                }
                            }
                        } else Modifier,
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(task.done) { toggle() }
                Spacer(Modifier.width(14.dp))
                Column(
                    Modifier.weight(1f).then(
                        if (editable) Modifier.press(scaleTo = 0.99f, haptics = vm.haptics) {
                            title = task.title
                            editing = !editing
                        } else Modifier,
                    ),
                ) {
                    Text(
                        task.title,
                        style = Type.sans(16.sp, FontWeight.Medium).copy(textDecoration = if (task.done) TextDecoration.LineThrough else null),
                        color = when {
                            editing -> c.accent
                            task.done -> c.ink3
                            else -> c.ink
                        },
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryDot(task.cat, 7.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(dueLabel, style = Type.sans(13.sp, if (overdue) FontWeight.SemiBold else FontWeight.Normal), color = if (overdue) c.accent else c.ink3)
                    }
                }
                if (onSchedule != null && !task.done) {
                    Box(Modifier.size(42.dp).press(scaleTo = 0.85f, haptics = vm.haptics, onClick = onSchedule), contentAlignment = Alignment.Center) {
                        LucideIcon(LucideData.CalendarPlus, c.ink2, 21.dp, 1.7f)
                    }
                }
            }
        }

        AnimatedVisibility(editing && editable, enter = expandVertically(spring(0.8f, 500f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 40.dp, bottom = 14.dp)) {
                BasicTextField(
                    title, { title = it }, singleLine = true,
                    textStyle = Type.sans(16.sp, FontWeight.Medium).copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        commitTitle()
                        editing = false
                    }),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.paper).border(1.dp, c.lineStrong, RoundedCornerShape(12.dp)).padding(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Hoy" to today,
                        "Mañana" to Dates.iso(Dates.today().plusDays(1)),
                        "+1 semana" to Dates.iso(Dates.today().plusDays(7)),
                        "Sin fecha" to null,
                    ).forEach { (label, value) ->
                        PulsoChip(label, task.due == value, {
                            if (value == null) vm.updateTask(task.id, clearDue = true) else vm.updateTask(task.id, due = value)
                        }, haptics = vm.haptics)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagPicker(vm, task.cat) { vm.updateTask(task.id, tag = it) }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.press(haptics = null) { remove() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(LucideData.Trash2, c.accent, 17.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar", style = Type.sans(14.sp, FontWeight.Medium), color = c.accent)
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(c.ink).press(haptics = vm.haptics) {
                            commitTitle()
                            editing = false
                        }.padding(horizontal = 16.dp, vertical = 9.dp),
                    ) { Text("Listo", style = Type.sans(14.sp, FontWeight.SemiBold), color = c.onInk) }
                }
            }
        }
        com.messoff.pulso.ui.components.Divider(dashed = true)
    }
}

@Composable
fun CheckBox(done: Boolean, size: Int = 24, onClick: () -> Unit) {
    val c = LocalPulso.current
    val bg by animateColorAsState(if (done) c.ink else androidx.compose.ui.graphics.Color.Transparent, label = "check")
    val scale by animateFloatAsState(if (done) 1f else 0.92f, spring(0.4f, Spring.StiffnessMedium), label = "checkScale")
    Box(
        Modifier
            .size((size + 14).dp)
            .press(scaleTo = 0.8f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size.dp).scale(scale).clip(RoundedCornerShape(7.dp)).background(bg)
                .border(1.8.dp, if (done) c.ink else c.ink3, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(done, enter = scaleIn(spring(0.45f, Spring.StiffnessHigh)), exit = scaleOut()) {
                LucideIcon(LucideData.Check, c.onInk, (size * 0.65f).dp, 3f)
            }
        }
    }
}
