package com.messoff.pulso.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.AuthState
import com.messoff.pulso.data.CalEvent
import com.messoff.pulso.data.Invitation
import com.messoff.pulso.data.Team
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.ui.components.AvatarStack
import com.messoff.pulso.ui.components.BtnKind
import com.messoff.pulso.ui.components.Divider
import com.messoff.pulso.ui.components.LiveBadge
import com.messoff.pulso.ui.components.PulsoButton
import com.messoff.pulso.ui.components.SectionLabel
import com.messoff.pulso.ui.components.dashedBorder
import com.messoff.pulso.ui.components.initials
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.TEAM_COLOR_KEYS
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.ui.theme.teamColor
import com.messoff.pulso.util.Dates
import kotlinx.coroutines.launch
import java.time.LocalDate

private val TEAM_COLOR_LABEL = mapOf(
    "rojo" to "Bermellón", "verde" to "Verde", "azul" to "Azul",
    "ocre" to "Ocre", "violeta" to "Violeta", "grafito" to "Grafito",
)

fun joinLink(vm: AppViewModel, code: String) = "${vm.serverUrl.trimEnd('/')}/?join=$code"

// ======================= Piezas compartidas =======================

/** Chips para mostrar u ocultar cada calendario (el mío y los de equipos). */
@Composable
fun CalendarChips(vm: AppViewModel, onTeams: () -> Unit) {
    val c = LocalPulso.current
    val teams by vm.teams.collectAsState()
    val hidden by vm.hiddenCalendars.collectAsState()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val items = listOf(Triple("me", "Mío", c.accent)) + teams.map { Triple(it.id, it.name, teamColor(it.color, c.night)) }
        items.forEach { (id, name, color) ->
            val off = id in hidden
            Row(
                Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (off) Color.Transparent else color.copy(alpha = 0.10f))
                    .then(
                        if (off) Modifier.dashedBorder(c.lineStrong, 999.dp)
                        else Modifier.border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp)),
                    )
                    .press(scaleTo = 0.94f, haptics = vm.haptics) { vm.toggleCalendar(id) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape).then(
                        if (off) Modifier.border(1.5.dp, c.ink3, CircleShape) else Modifier.background(color),
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(name, style = Type.sans(14.sp, FontWeight.SemiBold), color = if (off) c.ink3 else c.ink)
            }
        }
        Row(
            Modifier.height(38.dp).dashedBorder(c.lineStrong, 999.dp).clip(RoundedCornerShape(999.dp))
                .press(scaleTo = 0.94f, haptics = vm.haptics, onClick = onTeams).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LucideIcon(LucideData.Users, c.ink2, 16.dp)
            Spacer(Modifier.width(7.dp))
            Text(if (teams.isEmpty()) "Crear equipo" else "Equipos", style = Type.sans(14.sp, FontWeight.SemiBold), color = c.ink2)
        }
    }
}

/** "Solo para mí" o un equipo: dónde vive el evento. */
@Composable
fun CalendarPicker(vm: AppViewModel, value: String?, onChange: (String?) -> Unit, enabled: Boolean = true) {
    val c = LocalPulso.current
    val teams by vm.teams.collectAsState()
    if (teams.isEmpty()) return
    FlowRow(
        Modifier.alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val options = listOf<Triple<String?, String, Color>>(Triple(null, "Solo para mí", c.ink3)) +
            teams.map { Triple(it.id, it.name, teamColor(it.color, c.night)) }
        options.forEach { (id, name, color) ->
            val active = value == id
            Row(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) color.copy(alpha = 0.13f) else Color.Transparent)
                    .border(if (active) 1.5.dp else 1.dp, if (active) color else c.lineStrong, RoundedCornerShape(999.dp))
                    .press(enabled = enabled, haptics = vm.haptics) { onChange(id) }
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(name, style = Type.sans(15.sp, FontWeight.Medium), color = if (active) c.ink else c.ink2)
            }
        }
    }
}

/** Aviso cuando alguien te ha invitado a un equipo. */
@Composable
fun InvitationBanner(vm: AppViewModel, onOpen: () -> Unit) {
    val c = LocalPulso.current
    val invitations by vm.invitations.collectAsState()
    if (invitations.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.accentSoft)
            .border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .press(scaleTo = 0.98f, haptics = vm.haptics, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(LucideData.Mail, c.accent, 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            if (invitations.size == 1) "${invitations[0].invitedBy} te ha invitado a «${invitations[0].teamName}»"
            else "Tienes ${invitations.size} invitaciones a equipos",
            style = Type.sans(15.sp, FontWeight.Medium), color = c.ink, modifier = Modifier.weight(1f),
        )
        Text("Ver", style = Type.sans(15.sp, FontWeight.Bold), color = c.accent)
    }
}

// ======================= Vista Agenda =======================

@Composable
fun AgendaList(vm: AppViewModel, days: List<LocalDate>, onOpen: (String) -> Unit, onSlot: (LocalDate, Int) -> Unit) {
    val c = LocalPulso.current
    val events by vm.events.collectAsState()
    val teams by vm.teams.collectAsState()
    val now by vm.now.collectAsState()
    val today = now.toLocalDate()
    val nowMin = Dates.nowMinutes(now)
    val auth by vm.auth.collectAsState()
    val myId = (auth as? AuthState.SignedIn)?.user?.id
    var showPast by remember(days.first()) { mutableStateOf(false) }

    val todayIndex = days.indexOf(today)
    val hiddenPast = if (!showPast && todayIndex > 0) todayIndex else 0
    val visible = days.drop(hiddenPast)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (hiddenPast > 0) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).dashedBorder(c.lineStrong, 14.dp).clip(RoundedCornerShape(14.dp))
                    .press(haptics = vm.haptics) { showPast = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIcon(LucideData.ChevronDown, c.ink3, 16.dp, 2f)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (hiddenPast == 1) "Ver el día anterior" else "Ver los $hiddenPast días anteriores",
                    style = Type.sans(14.sp, FontWeight.SemiBold), color = c.ink3,
                )
            }
        }
        if (days.sumOf { d -> events.count { it.date == Dates.iso(d) } } == 0) {
            com.messoff.pulso.ui.components.EmptyState("✳", "Semana en blanco.", "Toca un día para añadir lo primero que importe.")
        }
        visible.forEachIndexed { index, day ->
            val list = events.filter { it.date == Dates.iso(day) }.sortedWith(compareBy({ it.start }, { -it.duration }))
            val isToday = day == today
            val busy = list.sumOf { it.duration }
            val nextIdx = if (isToday) list.indexOfFirst { it.end > nowMin } else -1
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (list.isEmpty()) Modifier.dashedBorder(c.lineStrong, 22.dp)
                        else Modifier
                            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                            .clip(RoundedCornerShape(22.dp))
                            .background(c.paper2)
                            .border(if (isToday) 1.5.dp else 1.dp, if (isToday) c.accent.copy(alpha = 0.5f) else c.line, RoundedCornerShape(22.dp)),
                    )
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = if (list.isEmpty()) 8.dp else 14.dp, bottom = if (list.isEmpty()) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${day.dayOfMonth}",
                        style = Type.serif(if (list.isEmpty()) 30.sp else 44.sp), color = if (isToday) c.accent else if (list.isEmpty()) c.ink3 else c.ink,
                        modifier = Modifier.width(52.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(Dates.WEEKDAYS[day.dayOfWeek.value - 1], style = Type.sans(16.sp, FontWeight.SemiBold), color = c.ink)
                        Text(
                            (if (index == 0 || day.dayOfMonth == 1) "${Dates.MONTHS[day.monthValue - 1]} · " else "") +
                                if (list.isEmpty()) "libre" else "${list.size} ${if (list.size == 1) "evento" else "eventos"} · ${Dates.duration(busy)}",
                            style = Type.sans(13.sp), color = c.ink3,
                        )
                    }
                    if (isToday) {
                        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(c.accent).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text("HOY", style = Type.sans(11.sp, FontWeight.Bold, 0.08.sp), color = c.accentInk)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).border(1.dp, c.line, CircleShape).background(c.paper)
                            .press(scaleTo = 0.85f, haptics = vm.haptics) { onSlot(day, 9 * 60) },
                        contentAlignment = Alignment.Center,
                    ) { LucideIcon(LucideData.Plus, c.ink2, 18.dp, 2f) }
                }
                if (list.isNotEmpty()) {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        list.forEachIndexed { i, event ->
                            val past = isToday && event.end <= nowMin
                            val current = isToday && event.start <= nowMin && event.end > nowMin
                            if (isToday && i == nextIdx && !current) NowDivider(nowMin)
                            AgendaEvent(vm, event, teams, myId, past, current, onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowDivider(nowMin: Int) {
    val c = LocalPulso.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(8.dp))
        Text("AHORA · ${Dates.time(nowMin)}", style = Type.sans(11.sp, FontWeight.Bold, 0.08.sp), color = c.accent)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(c.accent.copy(alpha = 0.45f)))
    }
}

@Composable
private fun AgendaEvent(
    vm: AppViewModel,
    event: CalEvent,
    teams: List<Team>,
    myId: String?,
    past: Boolean,
    current: Boolean,
    onOpen: (String) -> Unit,
) {
    val c = LocalPulso.current
    val cat = c.cat(event.cat)
    val team = teams.firstOrNull { it.id == event.teamId }
    val author = if (team != null && event.authorId != null && event.authorId != myId) event.authorName else null
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (past) 0.55f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) c.wash(event.cat) else Color.Transparent)
            .press(scaleTo = 0.985f, haptics = vm.haptics) { onOpen(event.id) }
            .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(60.dp), horizontalAlignment = Alignment.End) {
            Text(Dates.time(event.start), style = Type.sans(17.sp, FontWeight.SemiBold), color = c.ink)
            Text(Dates.duration(event.duration), style = Type.sans(12.5.sp), color = c.ink3)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(4.dp).height(56.dp).clip(RoundedCornerShape(2.dp)).background(cat))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.cat.label.uppercase(), style = Type.sans(11.sp, FontWeight.Bold, 0.1.sp), color = c.ink3)
                if (current) LiveBadge()
                if (team != null) {
                    val color = teamColor(team.color, c.night)
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.14f)).padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(5.dp))
                        Text(team.name, style = Type.sans(11.5.sp, FontWeight.SemiBold), color = c.ink, maxLines = 1)
                    }
                }
            }
            Text(event.title, style = Type.sans(17.sp, FontWeight.SemiBold, lineHeight = 21.sp), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (event.location.isNotBlank() || event.checklist.isNotEmpty() || author != null || event.attendees.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (event.location.isNotBlank()) {
                        LucideIcon(LucideData.MapPin, c.ink3, 14.dp)
                        Text(event.location, style = Type.sans(13.sp), color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    }
                    if (event.checklist.isNotEmpty()) Text("${event.checklist.count { it.done }}/${event.checklist.size} tareas", style = Type.sans(13.sp), color = c.ink3)
                    if (author != null) Text("de $author", style = Type.sans(13.sp), color = c.ink3)
                    AvatarStack(event.attendees, 3, 22.dp)
                }
            }
        }
    }
}

// ======================= Hoja de equipos =======================

@Composable
fun TeamsSheet(vm: AppViewModel, open: Boolean, onClose: () -> Unit) {
    if (!open) return
    val c = LocalPulso.current
    val scope = rememberCoroutineScope()
    val teams by vm.teams.collectAsState()
    val invitations by vm.invitations.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("azul") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val selected = teams.firstOrNull { it.id == selectedId }
    LaunchedEffect(selected, selectedId) { if (selectedId != null && selected == null) selectedId = null }

    PulsoSheet(onClose) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CALENDARIOS COMPARTIDOS", style = Type.kicker, color = c.ink2)
                    Text(buildAnnotated(c.accent), style = Type.serif(38.sp), color = c.ink)
                }
                Box(Modifier.size(42.dp).press(onClick = onClose), contentAlignment = Alignment.Center) { LucideIcon(LucideData.X, c.ink2, 22.dp) }
            }
            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = selected?.id,
                transitionSpec = {
                    val forward = targetState != null
                    (slideInHorizontally(spring(0.85f, Spring.StiffnessMediumLow)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally { if (forward) -it / 6 else it / 6 } + fadeOut(tween(120)))
                },
                label = "teams",
            ) { id ->
                val team = teams.firstOrNull { it.id == id }
                if (team != null) {
                    TeamDetail(vm, team, onBack = { selectedId = null })
                } else {
                    Column {
                        if (invitations.isNotEmpty()) {
                            SectionHeader("Invitaciones")
                            invitations.forEach { InvitationCard(vm, it) }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (teams.isEmpty() && !creating) {
                            com.messoff.pulso.ui.components.EmptyState("✳", "Todavía no tienes equipos.", "Crea uno e invita a quien quieras, aunque aún no tenga cuenta.")
                        }
                        teams.forEach { t ->
                            val tc = teamColor(t.color, c.night)
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(c.paper)
                                    .border(1.dp, c.line, RoundedCornerShape(16.dp))
                                    .press(scaleTo = 0.985f, haptics = vm.haptics) { selectedId = t.id }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TeamBadge(t.name, tc, 46)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = Type.sans(17.sp, FontWeight.SemiBold), color = c.ink)
                                    Text(
                                        "${t.members.size} ${if (t.members.size == 1) "persona" else "personas"}" +
                                            (if (t.invites.isNotEmpty()) " · ${t.invites.size} pendiente${if (t.invites.size == 1) "" else "s"}" else "") +
                                            if (t.isOwner) " · Admin" else "",
                                        style = Type.sans(13.sp), color = c.ink3,
                                    )
                                }
                                LucideIcon(LucideData.ChevronRight, c.ink3, 18.dp)
                            }
                        }
                        if (creating) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                InlineField(
                                    name, { name = it.take(50) }, "Nombre (Diseño, Familia, Club…)", Type.sans(17.sp),
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(12.dp)).padding(14.dp),
                                )
                                ColorPicker(vm, color) { color = it }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                                    PulsoButton("Cancelar", { creating = false }, kind = BtnKind.Ghost, height = 46.dp)
                                    PulsoButton("Crear equipo", {
                                        busy = true
                                        scope.launch {
                                            val id = vm.createTeam(name.trim(), color)
                                            busy = false
                                            if (id != null) {
                                                vm.haptics.play(Haptic.Success)
                                                name = ""
                                                creating = false
                                                selectedId = id
                                            }
                                        }
                                    }, enabled = !busy && name.isNotBlank(), height = 46.dp)
                                }
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth().height(52.dp).dashedBorder(c.lineStrong, 16.dp).clip(RoundedCornerShape(16.dp))
                                    .press(haptics = vm.haptics) { creating = true },
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LucideIcon(LucideData.Plus, c.ink2, 18.dp, 2f)
                                Spacer(Modifier.width(8.dp))
                                Text("Crear un equipo", style = Type.sans(15.sp, FontWeight.SemiBold), color = c.ink2)
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        SectionHeader("¿Tienes un código o un enlace?")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InlineField(
                                code, { code = it }, "Pega aquí el código o el enlace", Type.sans(16.sp),
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(12.dp)).padding(14.dp),
                            )
                            PulsoButton("Unirme", {
                                val value = code.trim().substringAfter("join=")
                                busy = true
                                scope.launch {
                                    val joined = vm.joinTeam(value)
                                    busy = false
                                    if (joined != null) {
                                        vm.haptics.play(Haptic.Success)
                                        vm.notify("Ya formas parte de «${joined.name}»")
                                        code = ""
                                        selectedId = joined.id
                                    }
                                }
                            }, kind = BtnKind.Ghost, enabled = !busy && code.isNotBlank(), height = 50.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun buildAnnotated(accent: Color) = androidx.compose.ui.text.buildAnnotatedString {
    append("Tus ")
    pushStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
    append("equipos")
    pop()
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), style = Type.label, color = LocalPulso.current.ink3, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp))
}

@Composable
private fun TeamBadge(name: String, color: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(color), contentAlignment = Alignment.Center) {
        Text(initials(name), style = Type.sans((size * 0.34f).sp, FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun ColorPicker(vm: AppViewModel, value: String, onChange: (String) -> Unit) {
    val c = LocalPulso.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TEAM_COLOR_KEYS.forEach { key ->
            val color = teamColor(key, c.night)
            val active = key == value
            Box(
                Modifier.size(42.dp)
                    .then(if (active) Modifier.border(2.dp, color, CircleShape) else Modifier)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(color)
                    .press(scaleTo = 0.85f, haptics = vm.haptics) { onChange(key) },
                contentAlignment = Alignment.Center,
            ) {
                if (active) LucideIcon(LucideData.Check, Color.White, 16.dp, 3f, contentDescription = TEAM_COLOR_LABEL[key])
            }
        }
    }
}

@Composable
private fun InvitationCard(vm: AppViewModel, invitation: Invitation) {
    val c = LocalPulso.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val color = teamColor(invitation.color, c.night)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(invitation.teamName, style = Type.sans(16.sp, FontWeight.SemiBold), color = c.ink)
            Text("${invitation.invitedBy} te ha invitado", style = Type.sans(13.sp), color = c.ink3)
        }
        PulsoButton("No", { busy = true; scope.launch { vm.declineInvitation(invitation.id); busy = false } }, kind = BtnKind.Ghost, enabled = !busy, height = 42.dp)
        Spacer(Modifier.width(8.dp))
        PulsoButton("Aceptar", {
            busy = true
            scope.launch {
                vm.acceptInvitation(invitation.id)
                vm.haptics.play(Haptic.Success)
                vm.notify("Ya formas parte de «${invitation.teamName}»")
                busy = false
            }
        }, enabled = !busy, height = 42.dp)
    }
}

@Composable
private fun TeamDetail(vm: AppViewModel, team: Team, onBack: () -> Unit) {
    val c = LocalPulso.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by vm.auth.collectAsState()
    val me = (auth as? AuthState.SignedIn)?.user
    val color = teamColor(team.color, c.night)
    var name by remember(team.name) { mutableStateOf(team.name) }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val link = joinLink(vm, team.joinCode)

    fun copy(text: String, message: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MessOff", text))
        vm.haptics.play(Haptic.Success)
        vm.notify(message)
    }

    Column {
        Row(Modifier.press(haptics = vm.haptics, onClick = onBack).padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(LucideData.ChevronLeft, c.ink2, 18.dp, 2f)
            Text("Equipos", style = Type.sans(14.sp, FontWeight.SemiBold), color = c.ink2)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(team.name, color, 50)
            Spacer(Modifier.width(14.dp))
            if (team.isOwner) {
                InlineField(
                    name, { name = it.take(50) }, "Nombre del equipo", Type.serif(32.sp), Modifier.weight(1f),
                )
                if (name.trim().isNotEmpty() && name.trim() != team.name) {
                    PulsoButton("Guardar", { scope.launch { vm.updateTeam(team.id, name = name.trim()) } }, kind = BtnKind.Ghost, height = 42.dp)
                }
            } else {
                Text(team.name, style = Type.serif(32.sp), color = c.ink, modifier = Modifier.weight(1f))
            }
        }
        if (team.isOwner) {
            Spacer(Modifier.height(14.dp))
            ColorPicker(vm, team.color) { key -> scope.launch { vm.updateTeam(team.id, color = key) } }
        }

        Spacer(Modifier.height(8.dp))
        SectionLabel("Personas", icon = LucideData.Users, aside = "${team.members.size}", modifier = Modifier.padding(top = 18.dp))
        team.members.forEach { member ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.paper3), contentAlignment = Alignment.Center) {
                    Text(initials(member.name), style = Type.sans(14.sp, FontWeight.Bold), color = c.ink2)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(member.name + if (member.id == me?.id) " (tú)" else "", style = Type.sans(16.sp, FontWeight.SemiBold), color = c.ink)
                    Text(member.email, style = Type.sans(13.sp), color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (member.role == "owner") {
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(c.accentSoft).padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text("ADMIN", style = Type.sans(11.sp, FontWeight.Bold, 0.06.sp), color = c.accent)
                    }
                } else if (team.isOwner) {
                    Box(
                        Modifier.size(38.dp).press(haptics = vm.haptics, haptic = Haptic.Warning) { scope.launch { vm.leaveTeam(team.id, member.id) } },
                        contentAlignment = Alignment.Center,
                    ) { LucideIcon(LucideData.X, c.ink3, 18.dp, 2f, contentDescription = "Quitar a ${member.name}") }
                }
            }
        }
        team.invites.forEach { invite ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(0.85f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).dashedBorder(c.lineStrong, 21.dp), contentAlignment = Alignment.Center) { LucideIcon(LucideData.Mail, c.ink3, 18.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(invite.email, style = Type.sans(16.sp, FontWeight.SemiBold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Pendiente · aún no ha aceptado", style = Type.sans(13.sp), color = c.ink3)
                }
                Box(Modifier.size(38.dp).press { scope.launch { vm.removeInvite(team.id, invite.id) } }, contentAlignment = Alignment.Center) {
                    LucideIcon(LucideData.X, c.ink3, 18.dp, 2f, contentDescription = "Cancelar invitación")
                }
            }
        }

        if (team.isOwner) {
            SectionLabel("Invitar", icon = LucideData.UserPlus, modifier = Modifier.padding(top = 22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InlineField(
                    email, { email = it }, "correo@ejemplo.com", Type.sans(16.sp),
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(12.dp)).padding(14.dp),
                )
                PulsoButton("Invitar", {
                    busy = true
                    scope.launch {
                        val registered = vm.inviteToTeam(team.id, email.trim().lowercase())
                        busy = false
                        if (registered != null) {
                            email = ""
                            vm.haptics.play(Haptic.Success)
                            vm.notify(
                                if (registered) "Invitación enviada: la verá al abrir MessOff."
                                else "Invitación guardada. Cuando se registre con ese email, la verá. También puedes mandarle el enlace.",
                            )
                        }
                    }
                }, enabled = !busy && email.contains('@'), height = 50.dp)
            }
            Text(
                "Vale para personas con y sin cuenta: si todavía no se han registrado, la invitación espera a que lo hagan.",
                style = Type.sans(13.sp), color = c.ink3, modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionLabel("Enlace para unirse", icon = LucideData.Link2, modifier = Modifier.padding(top = 22.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(12.dp))
                .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(link, style = Type.sans(13.sp), color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).press(haptics = vm.haptics) { copy(link, "Enlace copiado") }, contentAlignment = Alignment.Center) {
                LucideIcon(LucideData.Copy, c.ink2, 18.dp, contentDescription = "Copiar enlace")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PulsoButton("Compartir enlace", {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Únete a ${team.name} en MessOff")
                    putExtra(Intent.EXTRA_TEXT, "Únete a «${team.name}» en MessOff: $link")
                }
                context.startActivity(Intent.createChooser(send, "Compartir equipo"))
            }, haptics = vm.haptics, height = 48.dp)
            if (team.isOwner) {
                PulsoButton("Renovar", {
                    scope.launch {
                        vm.regenerateCode(team.id)
                        vm.notify("Enlace renovado: el anterior ya no funciona.")
                    }
                }, kind = BtnKind.Ghost, icon = LucideData.RefreshCw, haptics = vm.haptics, height = 48.dp)
            }
        }
        Text(
            "Quien abra el enlace, cree su cuenta (o entre) y se una verá y podrá editar el calendario del equipo.",
            style = Type.sans(13.sp), color = c.ink3, modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(22.dp))
        Divider(dashed = true)
        Spacer(Modifier.height(12.dp))
        if (team.isOwner) {
            if (confirmDelete) {
                Text("Los eventos del equipo vuelven al calendario de quien los creó.", style = Type.sans(13.sp), color = c.ink3)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    PulsoButton("Cancelar", { confirmDelete = false }, kind = BtnKind.Ghost, height = 46.dp)
                    PulsoButton("Eliminar equipo", {
                        scope.launch {
                            vm.haptics.play(Haptic.Error)
                            vm.deleteTeam(team.id)
                            onBack()
                        }
                    }, kind = BtnKind.Accent, height = 46.dp)
                }
            } else {
                Row(Modifier.press { confirmDelete = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(LucideData.Trash2, c.accent, 19.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar equipo", style = Type.sans(15.sp, FontWeight.Medium), color = c.accent)
                }
            }
        } else if (me != null) {
            Row(Modifier.press(haptics = vm.haptics, haptic = Haptic.Warning) {
                scope.launch {
                    vm.leaveTeam(team.id, me.id)
                    onBack()
                }
            }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(LucideData.LogOut, c.accent, 19.dp)
                Spacer(Modifier.width(8.dp))
                Text("Salir del equipo", style = Type.sans(15.sp, FontWeight.Medium), color = c.accent)
            }
        }
    }
}
