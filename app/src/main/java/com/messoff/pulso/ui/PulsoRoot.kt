package com.messoff.pulso.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.AuthState
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.ui.components.LogoMark
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.screens.AuthScreen
import com.messoff.pulso.ui.screens.CalendarScreen
import com.messoff.pulso.ui.screens.EventSheet
import com.messoff.pulso.ui.screens.FocusPill
import com.messoff.pulso.ui.screens.FocusScreen
import com.messoff.pulso.ui.screens.QuickAddSheet
import com.messoff.pulso.ui.screens.SearchSheet
import com.messoff.pulso.ui.screens.SettingsSheet
import com.messoff.pulso.ui.screens.TasksScreen
import com.messoff.pulso.ui.screens.TeamsSheet
import com.messoff.pulso.ui.screens.TodayScreen
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.PulsoTheme
import com.messoff.pulso.ui.theme.Type
import java.time.LocalDate

enum class Tab(val label: String, val icon: List<String>) {
    Hoy("Hoy", LucideData.SunMedium),
    Calendario("Calendario", LucideData.CalendarDays),
    Tareas("Tareas", LucideData.ListChecks),
    Enfoque("Enfoque", LucideData.Timer),
}

data class QuickPreset(val date: LocalDate, val start: Int)

@Composable
fun PulsoRoot(vm: AppViewModel) {
    val auth by vm.auth.collectAsState()
    val user = (auth as? AuthState.SignedIn)?.user
    val night = (user?.theme ?: vm.cachedTheme) == "night"

    PulsoTheme(night) {
        val c = LocalPulso.current
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !night
                    isAppearanceLightNavigationBars = !night
                }
            }
        }

        Box(Modifier.fillMaxSize().background(c.paper)) {
            AnimatedContent(
                targetState = auth is AuthState.SignedIn,
                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
                label = "auth",
            ) { signedIn ->
                if (signedIn) Shell(vm) else if (auth !is AuthState.Loading) AuthScreen(vm, (auth as? AuthState.SignedOut)?.notice)
            }
            ToastHost(vm)
        }
    }
}

@Composable
private fun Shell(vm: AppViewModel) {
    val c = LocalPulso.current
    var tab by rememberSaveable { mutableStateOf(Tab.Calendario) }
    var selected by remember { mutableStateOf<String?>(null) }
    var quick by remember { mutableStateOf<QuickPreset?>(null) }
    var quickOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var teamsOpen by remember { mutableStateOf(false) }
    val loading by vm.loading.collectAsState()
    val error by vm.loadError.collectAsState()
    val focus by vm.focus.collectAsState()

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = tab != Tab.Calendario && selected == null && !quickOpen && !searchOpen && !settingsOpen && !teamsOpen) {
        tab = Tab.Calendario
    }

    val openEvent: (String) -> Unit = {
        vm.haptics.play(Haptic.Selection)
        selected = it
    }
    val openQuick: (QuickPreset?) -> Unit = {
        vm.haptics.play(Haptic.Medium)
        quick = it
        quickOpen = true
    }
    val navigate: (Tab) -> Unit = {
        if (it != tab) {
            vm.haptics.play(Haptic.Selection)
            tab = it
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading && vm.events.value.isEmpty() -> Splash("Cargando tu agenda…")
            error != null -> LoadError(error!!, onRetry = vm::loadData, onLogout = vm::logout)
            else -> AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (fadeIn(tween(240, delayMillis = 40)) + slideInVertically(tween(280)) { it / 40 } +
                        scaleIn(tween(280), initialScale = 0.985f)) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically(tween(160)) { if (forward) -it / 60 else it / 60 })
                },
                label = "tab",
            ) { current ->
                when (current) {
                    Tab.Hoy -> TodayScreen(vm, openEvent, onSearch = { searchOpen = true }, onSettings = { settingsOpen = true },
                        onGoWeek = { navigate(Tab.Calendario) }, onGoTasks = { navigate(Tab.Tareas) }, onGoFocus = { navigate(Tab.Enfoque) },
                        onTeams = { teamsOpen = true })
                    Tab.Calendario -> CalendarScreen(vm, openEvent, onSlot = { d, s -> openQuick(QuickPreset(d, s)) },
                        onSearch = { searchOpen = true }, onSettings = { settingsOpen = true }, onTeams = { teamsOpen = true })
                    Tab.Tareas -> TasksScreen(vm, onSearch = { searchOpen = true }, onSettings = { settingsOpen = true })
                    Tab.Enfoque -> FocusScreen(vm, openEvent, onSearch = { searchOpen = true }, onSettings = { settingsOpen = true })
                }
            }
        }

        AnimatedVisibility(
            visible = tab != Tab.Enfoque && (focus.running || (focus.paused in 1 until focus.preset * 60)),
            enter = fadeIn() + scaleIn(spring(0.6f, Spring.StiffnessMedium), 0.8f) + slideInVertically { it },
            exit = fadeOut() + scaleOut(targetScale = 0.9f) + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 96.dp),
        ) {
            FocusPill(vm) { navigate(Tab.Enfoque) }
        }

        BottomBar(
            tab = tab,
            onTab = navigate,
            onNew = { openQuick(null) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        EventSheet(vm, selected, onClose = { selected = null }, onSelect = { selected = it })
        QuickAddSheet(vm, open = quickOpen, preset = quick, onClose = { quickOpen = false }, onCreated = openEvent)
        SearchSheet(
            vm, open = searchOpen, onClose = { searchOpen = false },
            onNavigate = navigate, onOpenEvent = openEvent,
            onNewEvent = { openQuick(null) },
            onTeams = { teamsOpen = true },
        )
        SettingsSheet(vm, open = settingsOpen, onClose = { settingsOpen = false }, onTeams = { teamsOpen = true })
        TeamsSheet(vm, open = teamsOpen, onClose = { teamsOpen = false })
    }
}

@Composable
fun BottomBar(tab: Tab, onTab: (Tab) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPulso.current
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(24.dp, RoundedCornerShape(999.dp), ambientColor = Color(0x33000000), spotColor = Color(0x55000000))
                .clip(RoundedCornerShape(999.dp))
                .background(c.paper2.copy(alpha = 0.97f))
                .border(1.dp, c.line, RoundedCornerShape(999.dp))
                .padding(6.dp),
        ) {
            val addWidth = 64.dp
            val slot = (maxWidth - addWidth) / 4
            val index = tab.ordinal
            val x by animateDpAsState(
                slot * index + if (index >= 2) addWidth else 0.dp,
                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "indicator",
            )
            Box(
                Modifier
                    .offset(x = x)
                    .width(slot)
                    .height(56.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.ink),
            )
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Tab.entries.forEachIndexed { i, item ->
                    if (i == 2) Spacer(Modifier.width(addWidth))
                    val active = item == tab
                    Column(
                        Modifier.width(slot).height(56.dp).press(scaleTo = 0.9f) { onTab(item) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        LucideIcon(item.icon, if (active) c.onInk else c.ink2, 22.dp, 1.8f)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label,
                            style = Type.sans(11.sp, if (active) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (active) c.onInk else c.ink2,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        var spin by remember { mutableStateOf(0f) }
        val rotation by androidx.compose.animation.core.animateFloatAsState(spin, spring(0.5f, Spring.StiffnessMediumLow), label = "spin")
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-10).dp)
                .size(64.dp)
                .shadow(18.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                .clip(CircleShape)
                .background(c.paper)
                .padding(4.dp)
                .clip(CircleShape)
                .background(c.accent)
                .press(scaleTo = 0.88f) {
                    spin += 90f
                    onNew()
                },
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(LucideData.Plus, c.accentInk, 26.dp, 2.2f, Modifier.rotate(rotation), "Crear evento")
        }
    }
}

/** Cabecera editorial común: eyebrow, título serif, subtítulo y botones. */
@Composable
fun AppHeader(
    vm: AppViewModel,
    eyebrow: String,
    titlePlain: String,
    titleAccent: String,
    subtitle: @Composable () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = LocalPulso.current
    val auth by vm.auth.collectAsState()
    val name = (auth as? AuthState.SignedIn)?.user?.name ?: ""
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp, bottom = 14.dp)) {
        Column(Modifier.padding(end = 112.dp)) {
            com.messoff.pulso.ui.components.Eyebrow(eyebrow)
            Spacer(Modifier.height(6.dp))
            com.messoff.pulso.ui.components.PageTitle(titlePlain, titleAccent)
        }
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.messoff.pulso.ui.components.IconCircle(LucideData.Search, onSearch, haptics = vm.haptics, contentDescription = "Buscar")
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(c.paper2)
                    .border(3.dp, c.paper3, CircleShape)
                    .press(haptics = vm.haptics, onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text(com.messoff.pulso.ui.components.initials(name), style = Type.sans(15.sp, FontWeight.SemiBold), color = c.ink2)
            }
        }
    }
    Box(Modifier.padding(bottom = 14.dp)) { subtitle() }
}

@Composable
fun Splash(label: String) {
    val c = LocalPulso.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        LogoMark(52.dp)
        Spacer(Modifier.height(10.dp))
        Text("messoff", style = Type.serif(34.sp, italic = true), color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(label, style = Type.bodySmall, color = c.ink3)
    }
}

@Composable
fun LoadError(message: String, onRetry: () -> Unit, onLogout: () -> Unit) {
    val c = LocalPulso.current
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("messoff", style = Type.serif(34.sp, italic = true), color = c.ink)
        Spacer(Modifier.height(10.dp))
        Text("No hemos podido cargar tu agenda.", style = Type.body, color = c.ink2)
        Spacer(Modifier.height(6.dp))
        Text(message, style = Type.bodySmall, color = c.accent)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.messoff.pulso.ui.components.PulsoButton("Reintentar", onRetry)
            com.messoff.pulso.ui.components.PulsoButton("Cerrar sesión", onLogout, kind = com.messoff.pulso.ui.components.BtnKind.Ghost)
        }
    }
}

/** Aviso inferior en tinta, con acción (Deshacer) y deslizable para descartar. */
@Composable
fun ToastHost(vm: AppViewModel) {
    val c = LocalPulso.current
    val toast by vm.toast.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedContent(
            targetState = toast,
            transitionSpec = {
                (slideInVertically(spring(0.7f, Spring.StiffnessMediumLow)) { it } + fadeIn()) togetherWith
                    (slideOutVertically { it / 2 } + fadeOut(tween(140)))
            },
            contentKey = { it?.id },
            label = "toast",
        ) { msg ->
            if (msg == null) {
                Spacer(Modifier.height(1.dp))
            } else {
                var drag by remember(msg.id) { mutableStateOf(0f) }
                Row(
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 14.dp, end = 14.dp, bottom = 96.dp)
                        .offset(x = drag.dp)
                        .fillMaxWidth()
                        .shadow(20.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.ink)
                        .pointerInput(msg.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = { if (kotlin.math.abs(drag) > 90) vm.dismissToast() else drag = 0f },
                            ) { _, amount -> drag += amount / density }
                        }
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        msg.text, style = Type.sans(14.sp), color = c.onInk, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    if (msg.actionLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.accent)
                                .press(haptics = vm.haptics, haptic = Haptic.Light) {
                                    msg.action?.invoke()
                                    vm.dismissToast()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(msg.actionLabel.uppercase(), style = Type.sans(12.sp, FontWeight.Bold, 0.06.sp), color = c.accentInk)
                        }
                    }
                }
            }
        }
    }
}
