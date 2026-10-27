package com.messoff.pulso.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.AuthState
import com.messoff.pulso.data.ApiException
import com.messoff.pulso.data.CalEvent
import com.messoff.pulso.data.Category
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.ui.QuickPreset
import com.messoff.pulso.ui.Tab
import com.messoff.pulso.ui.components.Avatar
import com.messoff.pulso.ui.components.BtnKind
import com.messoff.pulso.ui.components.CategoryTag
import com.messoff.pulso.ui.components.Divider
import com.messoff.pulso.ui.components.PulsoButton
import com.messoff.pulso.ui.components.PulsoChip
import com.messoff.pulso.ui.components.PulsoSwitch
import com.messoff.pulso.ui.components.SectionLabel
import com.messoff.pulso.ui.components.dashedBorder
import com.messoff.pulso.ui.components.initials
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.util.Dates
import com.messoff.pulso.util.QuickParse
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Hoja inferior con el aspecto de Pulso: papel, borde fino y asa. */
@Composable
private fun PulsoSheet(onClose: () -> Unit, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPulso.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        containerColor = c.paper2,
        scrimColor = Color.Black.copy(alpha = if (c.night) 0.6f else 0.38f),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (accent != null) Box(Modifier.fillMaxWidth().height(5.dp).background(accent))
                Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(c.lineStrong))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().imePadding(), content = content)
    }
}

@Composable
private fun InlineField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onDone: (() -> Unit)? = null,
    focus: FocusRequester? = null,
) {
    val c = LocalPulso.current
    Box(modifier) {
        if (value.isEmpty()) Text(placeholder, style = style, color = c.ink3)
        BasicTextField(
            value, onChange, singleLine = singleLine,
            textStyle = style.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        )
    }
}

private val DURATIONS = listOf(15, 30, 45, 60, 90, 120)

// ======================= Detalle del evento =======================

@Composable
fun EventSheet(vm: AppViewModel, selectedId: String?, onClose: () -> Unit, onSelect: (String) -> Unit) {
    val events by vm.events.collectAsState()
    val event = events.firstOrNull { it.id == selectedId }
    if (selectedId == null || event == null) return
    val c = LocalPulso.current
    val context = LocalContext.current
    var draft by remember(selectedId) { mutableStateOf(event) }
    var newItem by remember(selectedId) { mutableStateOf("") }
    var newPerson by remember(selectedId) { mutableStateOf("") }

    fun patch(next: CalEvent) {
        draft = next
        vm.saveEvent(next)
    }

    val date = Dates.parse(draft.date)
    val conflict = events.firstOrNull { it.id != draft.id && it.date == draft.date && draft.start < it.end && draft.end > it.start }
    val checked = draft.checklist.count { it.done }

    PulsoSheet(onClose, accent = c.cat(draft.cat)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTag(draft.cat)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.Check, c.green, 14.dp, 2.4f)
                    Spacer(Modifier.width(4.dp))
                    Text("Guardado", style = Type.sans(12.sp, FontWeight.Medium), color = c.green)
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(42.dp).press(haptics = vm.haptics) {
                    vm.duplicateEvent(draft.id) { copy ->
                        vm.haptics.play(Haptic.Success)
                        onSelect(copy.id)
                        vm.notify("Evento duplicado")
                    }
                }, contentAlignment = Alignment.Center) { LucideIcon(LucideData.Copy, c.ink2, 20.dp) }
                Box(Modifier.size(42.dp).press(haptics = vm.haptics, onClick = onClose), contentAlignment = Alignment.Center) {
                    LucideIcon(LucideData.X, c.ink2, 22.dp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("${Dates.dateLong(date).uppercase()} · ${Dates.relativeDay(date).uppercase()}", style = Type.label, color = c.ink3)
            Spacer(Modifier.height(6.dp))
            InlineField(draft.title, { patch(draft.copy(title = it)) }, "Sin título", Type.serif(38.sp, lineHeight = 42.sp), singleLine = false)

            if (conflict != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.AlertTriangle, c.accent, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Se solapa con «${conflict.title}» · ${Dates.time(conflict.start)}", style = Type.sans(13.5.sp, FontWeight.Medium), color = c.accent)
                }
            }
            Spacer(Modifier.height(18.dp))

            Fact(LucideData.CalendarDays, "Fecha", "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthValue.toString().padStart(2, '0')}/${date.year}") {
                DatePickerDialog(context, { _, y, m, d ->
                    vm.haptics.play(Haptic.Selection)
                    patch(draft.copy(date = Dates.iso(LocalDate.of(y, m + 1, d))))
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }
            Spacer(Modifier.height(10.dp))
            Fact(LucideData.Clock3, "Hora · termina a las ${Dates.time(draft.end)}", "${Dates.time(draft.start)}   ·   ${Dates.duration(draft.duration)}") {
                TimePickerDialog(context, { _, h, m ->
                    vm.haptics.play(Haptic.Selection)
                    patch(draft.copy(start = h * 60 + m))
                }, draft.start / 60, draft.start % 60, true).show()
            }

            Section("Duración") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATIONS.forEach { m -> PulsoChip(Dates.chip(m), draft.duration == m, { patch(draft.copy(duration = m)) }, haptics = vm.haptics) }
                }
            }
            Section("Mover a") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Hoy" to LocalDate.now(),
                        "Mañana" to LocalDate.now().plusDays(1),
                        "+1 semana" to date.plusWeeks(1),
                    ).forEach { (label, target) ->
                        PulsoChip(label, false, {
                            if (Dates.iso(target) != draft.date) {
                                vm.haptics.play(Haptic.Snap)
                                patch(draft.copy(date = Dates.iso(target)))
                            }
                        }, dashed = true)
                    }
                }
            }
            Section("Repetir", LucideData.Repeat2) {
                val dow = date.dayOfWeek.value - 1
                val rest = (1..maxOf(0, 4 - dow)).map { it.toLong() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PulsoChip("Al día siguiente", false, { vm.repeatEvent(draft, listOf(1), "mañana también") })
                    PulsoChip("Resto de la semana", false, { vm.repeatEvent(draft, rest, "el resto de la semana") }, enabled = rest.isNotEmpty())
                    PulsoChip("4 semanas", false, { vm.repeatEvent(draft, listOf(7, 14, 21, 28), "4 semanas más") })
                }
            }
            Section("Categoría") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { cat ->
                        val active = draft.cat == cat
                        Row(
                            Modifier.height(40.dp).clip(RoundedCornerShape(999.dp))
                                .background(if (active) c.wash(cat) else Color.Transparent)
                                .border(if (active) 1.5.dp else 1.dp, if (active) c.cat(cat) else c.lineStrong, RoundedCornerShape(999.dp))
                                .press(haptics = vm.haptics) { patch(draft.copy(category = cat.key)) }
                                .padding(horizontal = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c.cat(cat)))
                            Spacer(Modifier.width(8.dp))
                            Text(cat.label, style = Type.sans(15.sp, FontWeight.Medium), color = c.ink)
                        }
                    }
                }
            }
            Section("Ubicación") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.MapPin, c.ink2, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    InlineField(draft.location, { patch(draft.copy(location = it)) }, "Añadir un lugar", Type.sans(16.sp), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Divider()
            }
            Section("Personas", LucideData.Users, aside = "${draft.attendees.size}") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.attendees.forEach { name ->
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(c.paper3).padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(name, 26.dp, c.paper2)
                            Spacer(Modifier.width(6.dp))
                            Text(name, style = Type.sans(14.sp), color = c.ink)
                            Box(Modifier.size(28.dp).press { patch(draft.copy(attendees = draft.attendees - name)) }, contentAlignment = Alignment.Center) {
                                LucideIcon(LucideData.X, c.ink3, 14.dp, 2f)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                InlineField(newPerson, { newPerson = it }, "+ Añadir persona", Type.sans(15.sp), onDone = {
                    val name = newPerson.trim()
                    if (name.isNotEmpty() && name !in draft.attendees) {
                        patch(draft.copy(attendees = draft.attendees + name))
                        vm.haptics.play(Haptic.Light)
                    }
                    newPerson = ""
                })
            }
            Section("Subtareas", aside = "$checked/${draft.checklist.size}") {
                if (draft.checklist.isNotEmpty()) {
                    val pct by animateFloatAsState(checked.toFloat() / draft.checklist.size, label = "checks")
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.paper3)) {
                        Box(Modifier.fillMaxWidth(pct).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.green))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                draft.checklist.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckBox(item.done, 20) {
                            vm.haptics.play(if (item.done) Haptic.Light else Haptic.Success)
                            patch(draft.copy(checklist = draft.checklist.map { if (it.id == item.id) it.copy(done = !it.done) else it }))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.text, style = Type.sans(15.sp).copy(textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
                            color = if (item.done) c.ink3 else c.ink, modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(36.dp).press { patch(draft.copy(checklist = draft.checklist.filterNot { it.id == item.id })) }, contentAlignment = Alignment.Center) {
                            LucideIcon(LucideData.X, c.ink3, 15.dp, 2f)
                        }
                    }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.Plus, c.ink3, 18.dp, 2f)
                    Spacer(Modifier.width(10.dp))
                    InlineField(newItem, { newItem = it }, "Añadir subtarea y pulsar Intro", Type.sans(15.sp), Modifier.weight(1f), onDone = {
                        val text = newItem.trim()
                        if (text.isNotEmpty()) {
                            patch(draft.copy(checklist = draft.checklist + vm.newChecklistItem(text)))
                            vm.haptics.play(Haptic.Light)
                        }
                        newItem = ""
                    })
                }
            }
            Section("Notas") {
                InlineField(
                    draft.note, { patch(draft.copy(note = it)) }, "Contexto, acuerdos, lo que no quieres olvidar…", Type.sans(15.sp, lineHeight = 21.sp),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(14.dp),
                    singleLine = false,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("Todo se guarda solo. Tus datos viven en tu cuenta.", style = Type.serif(16.sp, italic = true), color = c.ink3)
            Spacer(Modifier.height(18.dp))
        }
        Divider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.press {
                vm.haptics.play(Haptic.Error)
                val removed = vm.deleteEvent(draft.id)
                onClose()
                vm.notify("Evento eliminado", "Deshacer") { removed?.let { vm.restoreEvent(it) } }
            }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(LucideData.Trash2, c.ink2, 20.dp)
                Spacer(Modifier.width(8.dp))
                Text("Eliminar", style = Type.sans(16.sp, FontWeight.Medium), color = c.ink2)
            }
            Spacer(Modifier.weight(1f))
            com.messoff.pulso.ui.components.AvatarStack(draft.attendees, 3, 28.dp)
            Spacer(Modifier.width(10.dp))
            PulsoButton("Listo", onClose, haptics = vm.haptics, height = 52.dp)
        }
    }
}

@Composable
private fun Fact(icon: List<String>, label: String, value: String, onClick: () -> Unit) {
    val c = LocalPulso.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(16.dp))
            .press(scaleTo = 0.98f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(icon, c.ink2, 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = Type.label, color = c.ink3)
            Spacer(Modifier.height(2.dp))
            Text(value, style = Type.sans(18.sp, FontWeight.Medium), color = c.ink)
        }
        LucideIcon(LucideData.ChevronRight, c.ink3, 18.dp)
    }
}

@Composable
private fun Section(label: String, icon: List<String>? = null, aside: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        SectionLabel(label, icon = icon, aside = aside)
        content()
    }
}

// ======================= Nuevo evento =======================

private val EXAMPLES = listOf("Café con Lucía mañana 09:30", "Gimnasio viernes 18:00", "Revisar OKR hoy 16:30", "Cena con Ana sábado 21:00")

@Composable
fun QuickAddSheet(vm: AppViewModel, open: Boolean, preset: QuickPreset?, onClose: () -> Unit, onCreated: (String) -> Unit) {
    if (!open) return
    val c = LocalPulso.current
    var text by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf<Category?>(null) }
    var duration by remember { mutableStateOf(60) }
    val focus = remember { FocusRequester() }
    val parsed = QuickParse.parse(text, LocalDateTime.now(), preset?.date, preset?.start)
    val category = manual ?: parsed.category ?: Category.Trabajo

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (text.isBlank()) return
        val event = vm.addEvent(
            CalEvent(
                id = "", title = parsed.title, date = Dates.iso(parsed.date), start = parsed.start,
                duration = duration, category = category.key,
            ),
        )
        vm.haptics.play(Haptic.Success)
        onClose()
        vm.notify("«${parsed.title}» · ${Dates.time(parsed.start)}", "Abrir") { onCreated(event.id) }
    }

    PulsoSheet(onClose) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(LucideData.Sparkles, c.accent, 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("NUEVO EVENTO", style = Type.kicker, color = c.ink2, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).press(onClick = onClose), contentAlignment = Alignment.Center) { LucideIcon(LucideData.X, c.ink2, 22.dp) }
            }
            Spacer(Modifier.height(10.dp))
            InlineField(
                text, {
                    text = it
                    manual = null
                },
                "Café con Lucía mañana 09:30", Type.serif(32.sp, lineHeight = 36.sp), focus = focus, onDone = ::submit,
            )
            Spacer(Modifier.height(14.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.CalendarDays, c.ink2, 17.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(Dates.dateLong(parsed.date), style = Type.sans(14.5.sp), color = c.ink)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.Clock3, c.ink2, 17.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(Dates.range(parsed.start, duration), style = Type.sans(14.5.sp), color = c.ink)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.cat(category)))
                    Spacer(Modifier.width(6.dp))
                    Text(category.label, style = Type.sans(14.5.sp), color = c.ink2)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EXAMPLES.forEach { ex ->
                    Box(
                        Modifier.dashedBorder(c.lineStrong, 999.dp).clip(RoundedCornerShape(999.dp))
                            .press(haptics = vm.haptics) {
                                text = ex
                                manual = null
                            }.padding(horizontal = 14.dp, vertical = 9.dp),
                    ) { Text(ex, style = Type.sans(13.5.sp), color = c.ink2) }
                }
            }
            Section("Duración") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATIONS.forEach { m -> PulsoChip(Dates.chip(m), duration == m, { duration = m }, haptics = vm.haptics) }
                }
            }
            Section("Categoría") { TagPicker(vm, category) { manual = it } }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Escribe la hora y el día; nosotros ordenamos el resto.", style = Type.sans(13.sp), color = c.ink3, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                PulsoButton("Crear", ::submit, enabled = text.isNotBlank(), trailing = LucideData.ArrowRight, height = 52.dp)
            }
        }
    }
}

// ======================= Buscar =======================

private data class Hit(val group: String, val label: String, val hint: String?, val icon: List<String>?, val dot: Category?, val run: () -> Unit)

@Composable
fun SearchSheet(
    vm: AppViewModel,
    open: Boolean,
    onClose: () -> Unit,
    onNavigate: (Tab) -> Unit,
    onOpenEvent: (String) -> Unit,
    onNewEvent: () -> Unit,
) {
    if (!open) return
    val c = LocalPulso.current
    val events by vm.events.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val auth by vm.auth.collectAsState()
    val night = (auth as? AuthState.SignedIn)?.user?.theme == "night"
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun go(action: () -> Unit) {
        vm.haptics.play(Haptic.Selection)
        onClose()
        action()
    }

    val all = buildList {
        add(Hit("Navegar", "Ir a Hoy", null, LucideData.SunMedium, null) { onNavigate(Tab.Hoy) })
        add(Hit("Navegar", "Ir al calendario", null, LucideData.CalendarDays, null) { onNavigate(Tab.Calendario) })
        add(Hit("Navegar", "Ir a la bandeja", null, LucideData.ListChecks, null) { onNavigate(Tab.Tareas) })
        add(Hit("Navegar", "Ir a Enfoque", null, LucideData.Timer, null) { onNavigate(Tab.Enfoque) })
        add(Hit("Acciones", "Crear evento", null, LucideData.Plus, null, onNewEvent))
        add(Hit("Acciones", if (night) "Cambiar a modo papel" else "Cambiar a modo noche", null, if (night) LucideData.Sun else LucideData.Moon, null) {
            vm.setTheme(if (night) "paper" else "night")
        })
        events.sortedWith(compareBy({ it.date }, { it.start })).forEach { e ->
            add(Hit("Eventos", e.title, "${Dates.dateMedium(Dates.parse(e.date))} · ${Dates.time(e.start)}", null, e.cat) { onOpenEvent(e.id) })
        }
        tasks.filter { !it.done }.forEach { t ->
            add(Hit("Tareas", t.title, t.due?.let { Dates.dateMedium(Dates.parse(it)) } ?: "Sin fecha", null, t.cat) { onNavigate(Tab.Tareas) })
        }
    }
    val q = query.trim().lowercase()
    val hits = if (q.isEmpty()) {
        all.filter { it.group == "Navegar" || it.group == "Acciones" } + all.filter { it.group == "Eventos" }.take(3) + all.filter { it.group == "Tareas" }.take(2)
    } else {
        all.filter { it.label.lowercase().contains(q) || (it.hint ?: "").lowercase().contains(q) || it.group.lowercase().contains(q) }
    }.take(16)

    PulsoSheet(onClose) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(LucideData.Search, c.ink2, 22.dp)
            Spacer(Modifier.width(12.dp))
            InlineField(query, { query = it }, "Buscar eventos, tareas o secciones…", Type.sans(18.sp), Modifier.weight(1f), focus = focus, onDone = {
                hits.firstOrNull()?.let { go(it.run) }
            })
        }
        Spacer(Modifier.height(14.dp))
        Divider()
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (hits.isEmpty()) Text("Nada coincide con «$query».", style = Type.serif(18.sp, italic = true), color = c.ink3, modifier = Modifier.padding(14.dp))
            hits.forEachIndexed { i, hit ->
                if (i == 0 || hits[i - 1].group != hit.group) {
                    Text(hit.group.uppercase(), style = Type.label, color = c.ink3, modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp))
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).press(scaleTo = 0.98f) { go(hit.run) }.padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.paper3), contentAlignment = Alignment.Center) {
                        if (hit.icon != null) LucideIcon(hit.icon, c.ink, 18.dp)
                        if (hit.dot != null) Box(Modifier.size(9.dp).clip(CircleShape).background(c.cat(hit.dot)))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(hit.label, style = Type.sans(16.sp, FontWeight.Medium), color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hit.hint != null) Text(hit.hint, style = Type.sans(13.sp), color = c.ink3)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ======================= Cuenta y ajustes =======================

@Composable
fun SettingsSheet(vm: AppViewModel, open: Boolean, onClose: () -> Unit) {
    if (!open) return
    val c = LocalPulso.current
    val scope = rememberCoroutineScope()
    val auth by vm.auth.collectAsState()
    val user = (auth as? AuthState.SignedIn)?.user ?: return
    var name by remember { mutableStateOf(user.name) }
    var showPassword by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: ApiException) {
                vm.haptics.play(Haptic.Error)
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    PulsoSheet(onClose) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tu cuenta", style = Type.serif(38.sp), color = c.ink, modifier = Modifier.weight(1f))
                Box(Modifier.size(42.dp).press(onClick = onClose), contentAlignment = Alignment.Center) { LucideIcon(LucideData.X, c.ink2, 22.dp) }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    Text(initials(user.name), style = Type.sans(20.sp, FontWeight.SemiBold), color = c.accent)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, style = Type.sans(18.sp, FontWeight.SemiBold), color = c.ink)
                    Text(user.email, style = Type.sans(14.sp), color = c.ink3)
                }
                LucideIcon(LucideData.ShieldCheck, c.green, 18.dp)
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, style = Type.sans(14.sp, FontWeight.Medium), color = c.accent)
            }
            Spacer(Modifier.height(18.dp))
            Divider()

            Section("Perfil") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InlineField(name, { name = it }, "Tu nombre", Type.sans(16.sp),
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(15.dp))
                    Spacer(Modifier.width(10.dp))
                    PulsoButton("Guardar", {
                        run {
                            vm.updateName(name.trim())
                            vm.haptics.play(Haptic.Success)
                            vm.notify("Nombre actualizado")
                        }
                    }, kind = BtnKind.Ghost, enabled = !busy && name.isNotBlank() && name.trim() != user.name, height = 52.dp)
                }
            }

            Section("Tema") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("paper" to "Papel", "night" to "Noche").forEach { (key, label) ->
                        val active = user.theme == key
                        val swatch = if (key == "night") com.messoff.pulso.ui.theme.NightColors else com.messoff.pulso.ui.theme.PaperColors
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).border(if (active) 2.5.dp else 1.dp, if (active) c.ink else c.line, RoundedCornerShape(18.dp))
                                .press(haptics = vm.haptics) { vm.setTheme(key) }.padding(12.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(12.dp)).background(swatch.paper2).border(1.dp, swatch.line, RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Box(Modifier.size(56.dp, 12.dp).clip(RoundedCornerShape(3.dp)).background(swatch.accent))
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(swatch.paper4))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LucideIcon(if (key == "night") LucideData.Moon else LucideData.Sun, c.ink, 18.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = Type.sans(15.sp, FontWeight.Medium), color = c.ink)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Divider()
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Respuesta háptica", style = Type.sans(17.sp, FontWeight.SemiBold), color = c.ink)
                    Text("Vibra al abrir, mover y completar.", style = Type.bodySmall, color = c.ink3)
                }
                PulsoSwitch(user.haptics, { vm.setHaptics(it) }, vm.haptics)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Haptic.Light to "Toque", Haptic.Medium to "Medio", Haptic.Celebrate to "Logro", Haptic.Error to "Aviso").forEach { (kind, label) ->
                    Row(
                        Modifier.weight(1f).height(44.dp).dashedBorder(c.lineStrong, 12.dp).clip(RoundedCornerShape(12.dp)).press(scaleTo = 0.92f) { vm.haptics.play(kind) },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LucideIcon(LucideData.Zap, c.ink2, 15.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = Type.sans(13.5.sp), color = c.ink)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (vm.haptics.supported) "MessOff usa el motor háptico del móvil: clics nítidos, golpes secos y un latido al empezar el foco." else "Este dispositivo no tiene motor de vibración.",
                style = Type.sans(13.sp), color = c.ink3,
            )

            Spacer(Modifier.height(22.dp))
            Divider()
            Section("Seguridad") {
                Row(Modifier.fillMaxWidth().press { showPassword = !showPassword }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.KeyRound, c.ink2, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Cambiar contraseña", style = Type.sans(16.sp), color = c.ink, modifier = Modifier.weight(1f))
                    LucideIcon(LucideData.ChevronRight, c.ink3, 18.dp)
                }
                AnimatedVisibility(showPassword, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        SecretField(current, { current = it }, "Contraseña actual")
                        SecretField(next, { next = it }, "Nueva contraseña (mín. 8)")
                        Spacer(Modifier.height(10.dp))
                        PulsoButton("Actualizar contraseña", {
                            if (next.length < 8) {
                                error = "La nueva contraseña debe tener al menos 8 caracteres."
                            } else run {
                                vm.changePassword(current, next)
                                vm.haptics.play(Haptic.Success)
                                vm.notify("Contraseña actualizada. Las otras sesiones se han cerrado.")
                                showPassword = false
                                current = ""
                                next = ""
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(Modifier.fillMaxWidth().press {
                    onClose()
                    vm.logout()
                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.LogOut, c.ink2, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Cerrar sesión", style = Type.sans(16.sp), color = c.ink, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().press {
                    onClose()
                    vm.logoutAll()
                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.ShieldCheck, c.ink2, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Cerrar sesión en todos los dispositivos", style = Type.sans(16.sp), color = c.ink, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().press { confirmDelete = !confirmDelete }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.Trash2, c.accent, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Eliminar cuenta", style = Type.sans(16.sp), color = c.accent, modifier = Modifier.weight(1f))
                }
                AnimatedVisibility(confirmDelete, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        Text("Se borrarán tus eventos, tareas y sesiones. No se puede deshacer.", style = Type.bodySmall, color = c.ink2)
                        SecretField(deletePassword, { deletePassword = it }, "Tu contraseña para confirmar")
                        Spacer(Modifier.height(10.dp))
                        PulsoButton("Eliminar definitivamente", {
                            run {
                                vm.deleteAccount(deletePassword)
                                vm.haptics.play(Haptic.Error)
                                onClose()
                            }
                        }, kind = BtnKind.Accent, enabled = !busy && deletePassword.isNotEmpty(), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Servidor: ${vm.serverUrl}", style = Type.sans(12.sp), color = c.ink3)
        }
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val c = LocalPulso.current
    Box(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(15.dp)) {
        if (value.isEmpty()) Text(placeholder, style = Type.sans(16.sp), color = c.ink3)
        BasicTextField(
            value, onChange, singleLine = true, textStyle = Type.sans(16.sp).copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
    }
}
