package com.messoff.pulso

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messoff.pulso.data.ApiException
import com.messoff.pulso.data.BootstrapResponse
import com.messoff.pulso.data.CalEvent
import com.messoff.pulso.data.Category
import com.messoff.pulso.data.ChecklistItem
import com.messoff.pulso.data.EventResponse
import com.messoff.pulso.data.FocusSession
import com.messoff.pulso.data.Invitation
import com.messoff.pulso.data.Team
import com.messoff.pulso.data.TeamsResponse
import com.messoff.pulso.data.ScheduleResponse
import com.messoff.pulso.data.SessionResponse
import com.messoff.pulso.data.SessionsResponse
import com.messoff.pulso.data.Task
import com.messoff.pulso.data.TaskResponse
import com.messoff.pulso.data.TeamRef
import com.messoff.pulso.data.User
import com.messoff.pulso.data.UserResponse
import com.messoff.pulso.focus.FocusAlarm
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.util.Dates
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

sealed interface AuthState {
    data object Loading : AuthState
    data class SignedOut(val notice: String? = null) : AuthState
    data class SignedIn(val user: User) : AuthState
}

data class ToastMsg(
    val id: Long,
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

data class FocusState(
    val preset: Int = 25,
    val endsAt: Long = 0L,
    val paused: Int = 25 * 60,
    val sessions: List<FocusSession> = emptyList(),
) {
    val running: Boolean get() = endsAt > 0L
    fun remaining(nowMs: Long): Int =
        if (running) maxOf(0, ((endsAt - nowMs + 999) / 1000).toInt()) else paused
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val pulso = app as PulsoApp
    private val api = pulso.api
    private val session = pulso.session
    val haptics = pulso.haptics

    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private val _events = MutableStateFlow<List<CalEvent>>(emptyList())

    private val _hidden = MutableStateFlow(session.hiddenCalendars)
    val hiddenCalendars: StateFlow<Set<String>> = _hidden.asStateFlow()

    /** Solo los eventos de los calendarios visibles. */
    val events: StateFlow<List<CalEvent>> = combine(_events, _hidden) { list, hidden ->
        if (hidden.isEmpty()) list else list.filterNot { (it.teamId ?: "me") in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _teams = MutableStateFlow<List<Team>>(emptyList())
    val teams: StateFlow<List<Team>> = _teams.asStateFlow()

    private val _invitations = MutableStateFlow<List<Invitation>>(emptyList())
    val invitations: StateFlow<List<Invitation>> = _invitations.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _toast = MutableStateFlow<ToastMsg?>(null)
    val toast: StateFlow<ToastMsg?> = _toast.asStateFlow()

    private val _now = MutableStateFlow(LocalDateTime.now())
    val now: StateFlow<LocalDateTime> = _now.asStateFlow()

    private val _focus = MutableStateFlow(
        FocusState(session.focusPreset, session.focusEndsAt, session.focusPaused),
    )
    val focus: StateFlow<FocusState> = _focus.asStateFlow()

    private val saveJobs = HashMap<String, Job>()
    private val inflight = AtomicInteger(0)
    private var lastSync = 0L
    private var toastJob: Job? = null

    init {
        haptics.enabled = session.hapticsEnabled
        api.onUnauthorized = {
            session.token = null
            _auth.value = AuthState.SignedOut("Tu sesión ha caducado. Vuelve a entrar.")
        }
        viewModelScope.launch {
            while (true) {
                _now.value = LocalDateTime.now()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
        // Con equipos, los cambios de otras personas llegan solos cada medio minuto.
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (_auth.value is AuthState.SignedIn && _teams.value.isNotEmpty()) syncNow()
            }
        }
        boot()
    }

    val serverUrl: String get() = session.baseUrl
    val cachedTheme: String get() = session.theme

    fun boot() {
        if (session.token == null || session.baseUrl.isBlank()) {
            _auth.value = AuthState.SignedOut()
            return
        }
        _auth.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val data = api.get<UserResponse>("/auth/me")
                signedIn(data.user)
            } catch (e: Throwable) {
                if (e is ApiException && e.status == 401) {
                    session.token = null
                    _auth.value = AuthState.SignedOut()
                } else {
                    _auth.value = AuthState.SignedOut(e.message ?: "No se pudo comprobar la sesión.")
                }
            }
        }
    }

    private fun signedIn(user: User) {
        applyUser(user)
        _auth.value = AuthState.SignedIn(user)
        loadData()
        loadSessions()
    }

    private fun applyUser(user: User) {
        session.theme = user.theme
        session.hapticsEnabled = user.haptics
        haptics.enabled = user.haptics
    }

    // ---------- Avisos ----------

    fun notify(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        val msg = ToastMsg(System.nanoTime(), text, actionLabel, action)
        _toast.value = msg
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(if (actionLabel != null) 6000 else 4200)
            if (_toast.value?.id == msg.id) _toast.value = null
        }
    }

    fun dismissToast() {
        _toast.value = null
    }

    private fun fail(error: Throwable, fallback: String) {
        if (error is ApiException && error.status == 401) return
        haptics.play(Haptic.Warning)
        notify((error as? ApiException)?.message ?: fallback)
    }

    private fun track(block: suspend () -> Unit, onError: (Throwable) -> Unit) {
        inflight.incrementAndGet()
        viewModelScope.launch {
            try {
                block()
            } catch (e: Throwable) {
                onError(e)
            } finally {
                inflight.decrementAndGet()
                lastSync = System.currentTimeMillis()
            }
        }
    }

    // ---------- Sesión ----------

    suspend fun login(server: String, email: String, password: String) {
        session.baseUrl = server
        val data = api.post<UserResponse>("/auth/login", api.obj("email" to email.trim(), "password" to password))
        signedIn(data.user)
    }

    suspend fun register(server: String, name: String, email: String, password: String) {
        session.baseUrl = server
        val data = api.post<UserResponse>(
            "/auth/register",
            api.obj("name" to name.trim(), "email" to email.trim(), "password" to password),
        )
        signedIn(data.user)
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.raw("POST", "/auth/logout") }
            clearLocal()
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            runCatching { api.raw("POST", "/auth/logout-all") }
            clearLocal()
        }
    }

    private fun clearLocal(notice: String? = null) {
        session.token = null
        _events.value = emptyList()
        _tasks.value = emptyList()
        _auth.value = AuthState.SignedOut(notice)
    }

    suspend fun updateName(name: String) {
        val data = api.patch<UserResponse>("/auth/me", api.obj("name" to name))
        _auth.value = AuthState.SignedIn(data.user)
    }

    suspend fun changePassword(current: String, next: String) {
        val data = api.patch<UserResponse>(
            "/auth/password",
            api.obj("currentPassword" to current, "newPassword" to next),
        )
        _auth.value = AuthState.SignedIn(data.user)
    }

    suspend fun deleteAccount(password: String) {
        api.raw("POST", "/auth/delete", api.obj("password" to password))
        clearLocal()
    }

    private fun patchUser(vararg pairs: Pair<String, Any?>, local: (User) -> User) {
        val current = (_auth.value as? AuthState.SignedIn)?.user ?: return
        val next = local(current)
        applyUser(next)
        _auth.value = AuthState.SignedIn(next)
        viewModelScope.launch {
            try {
                val data = api.patch<UserResponse>("/auth/me", api.obj(*pairs))
                applyUser(data.user)
                _auth.value = AuthState.SignedIn(data.user)
            } catch (e: Throwable) {
                applyUser(current)
                _auth.value = AuthState.SignedIn(current)
                fail(e, "No se pudo guardar el ajuste")
            }
        }
    }

    fun setTheme(theme: String) = patchUser("theme" to theme) { it.copy(theme = theme) }
    fun setHaptics(on: Boolean) = patchUser("haptics" to on) { it.copy(haptics = on) }

    // ---------- Datos ----------

    fun loadData() {
        _loading.value = true
        _loadError.value = null
        viewModelScope.launch {
            try {
                val data = api.get<BootstrapResponse>("/bootstrap")
                _events.value = data.events
                _tasks.value = data.tasks
                _teams.value = data.teams
                _invitations.value = data.invitations
                applyUser(data.user)
                _auth.value = AuthState.SignedIn(data.user)
                lastSync = System.currentTimeMillis()
            } catch (e: Throwable) {
                if (!(e is ApiException && e.status == 401)) _loadError.value = e.message ?: "No se pudo cargar tu agenda."
            } finally {
                _loading.value = false
            }
        }
    }

    /** Al volver a la app: trae lo cambiado en otros dispositivos sin pisar cambios en vuelo. */
    fun refreshIfStale() {
        _now.value = LocalDateTime.now()
        if (_auth.value !is AuthState.SignedIn) return
        if (System.currentTimeMillis() - lastSync < 20_000) return
        if (inflight.get() > 0 || saveJobs.isNotEmpty()) return
        viewModelScope.launch { syncNow() }
    }

    /** Trae los datos del servidor sin pisar cambios en vuelo. */
    private suspend fun syncNow() {
        if (inflight.get() > 0 || saveJobs.isNotEmpty()) return
        runCatching { api.get<BootstrapResponse>("/bootstrap") }.onSuccess { data ->
            if (inflight.get() == 0 && saveJobs.isEmpty()) {
                _events.value = data.events
                _tasks.value = data.tasks
                _teams.value = data.teams
                _invitations.value = data.invitations
                lastSync = System.currentTimeMillis()
            }
        }
    }

    // ---------- Equipos ----------

    fun toggleCalendar(id: String) {
        val next = _hidden.value.let { if (id in it) it - id else it + id }
        _hidden.value = next
        session.hiddenCalendars = next
    }

    fun rememberCalendar(teamId: String?) {
        session.lastCalendar = teamId.orEmpty()
    }

    val lastCalendar: String? get() = session.lastCalendar.ifBlank { null }

    var calViewName: String
        get() = session.calView
        set(value) {
            session.calView = value
        }

    private fun applyTeams(data: TeamsResponse) {
        _teams.value = data.teams
        _invitations.value = data.invitations
    }

    private suspend fun teamCall(method: String, path: String, body: kotlinx.serialization.json.JsonElement? = null, failMessage: String = "No se pudo completar la acción"): TeamsResponse? {
        inflight.incrementAndGet()
        return try {
            val data = api.json.decodeFromString<TeamsResponse>(api.raw(method, path, body))
            applyTeams(data)
            data
        } catch (e: Throwable) {
            fail(e, failMessage)
            null
        } finally {
            inflight.decrementAndGet()
        }
    }

    suspend fun createTeam(name: String, color: String): String? =
        teamCall("POST", "/teams", api.obj("name" to name, "color" to color), "No se pudo crear el equipo")?.team?.id

    suspend fun updateTeam(id: String, name: String? = null, color: String? = null) {
        val fields = buildList {
            if (name != null) add("name" to name)
            if (color != null) add("color" to color)
        }
        teamCall("PATCH", "/teams/$id", api.obj(*fields.toTypedArray()))
    }

    suspend fun deleteTeam(id: String) {
        if (teamCall("DELETE", "/teams/$id") != null) syncNow()
    }

    suspend fun regenerateCode(id: String) {
        teamCall("POST", "/teams/$id/code", api.obj())
    }

    /** Devuelve si la persona ya tiene cuenta (null si falló). */
    suspend fun inviteToTeam(id: String, email: String): Boolean? =
        teamCall("POST", "/teams/$id/invites", api.obj("email" to email), "No se pudo invitar")?.registered

    suspend fun removeInvite(teamId: String, inviteId: String) {
        teamCall("DELETE", "/teams/$teamId/invites/$inviteId")
    }

    suspend fun acceptInvitation(id: String) {
        if (teamCall("POST", "/teams/invitations/$id/accept", api.obj()) != null) syncNow()
    }

    suspend fun declineInvitation(id: String) {
        teamCall("POST", "/teams/invitations/$id/decline", api.obj())
    }

    suspend fun joinTeam(code: String): TeamRef? {
        val data = teamCall("POST", "/teams/join", api.obj("code" to code), "No se pudo unir al equipo") ?: return null
        syncNow()
        return data.team
    }

    suspend fun leaveTeam(teamId: String, userId: String) {
        if (teamCall("DELETE", "/teams/$teamId/members/$userId") != null) syncNow()
    }

    private fun uid() = UUID.randomUUID().toString()

    private fun clamp(e: CalEvent): CalEvent {
        val start = e.start.coerceIn(0, 1434)
        val duration = e.duration.coerceIn(5, 1440 - start)
        return if (start == e.start && duration == e.duration) e else e.copy(start = start, duration = duration)
    }

    fun addEvent(input: CalEvent): CalEvent {
        val event = clamp(input.copy(id = uid()))
        _events.update { it + event }
        track({ api.post<EventResponse>("/events", api.encode(event)) }) { e ->
            _events.update { list -> list.filterNot { it.id == event.id } }
            fail(e, "No se pudo crear el evento")
        }
        return event
    }

    fun saveEvent(input: CalEvent) {
        val next = clamp(input)
        _events.update { list -> list.map { if (it.id == next.id) next else it } }
        saveJobs.remove(next.id)?.cancel()
        saveJobs[next.id] = viewModelScope.launch {
            delay(350)
            saveJobs.remove(next.id)
            if (next.title.isBlank()) return@launch
            inflight.incrementAndGet()
            try {
                api.patch<EventResponse>("/events/${next.id}", api.encode(next))
            } catch (e: Throwable) {
                fail(e, "No se pudo guardar el evento")
                loadData()
            } finally {
                inflight.decrementAndGet()
                lastSync = System.currentTimeMillis()
            }
        }
    }

    fun moveEvent(id: String, date: String, start: Int): CalEvent? {
        val previous = _events.value.firstOrNull { it.id == id } ?: return null
        if (previous.date == date && previous.start == start) return previous
        saveJobs.remove(id)?.cancel()
        val next = clamp(previous.copy(date = date, start = start))
        _events.update { list -> list.map { if (it.id == id) next else it } }
        track({ api.patch<EventResponse>("/events/$id", api.encode(next)) }) { e ->
            _events.update { list -> list.map { if (it.id == id) previous else it } }
            fail(e, "No se pudo mover el evento")
        }
        return previous
    }

    fun duplicateEvent(id: String, onDone: (CalEvent) -> Unit) {
        track({
            val data = api.post<EventResponse>("/events/$id/duplicate")
            _events.update { it + data.event }
            onDone(data.event)
        }) { e -> fail(e, "No se pudo duplicar el evento") }
    }

    fun deleteEvent(id: String): CalEvent? {
        val previous = _events.value.firstOrNull { it.id == id } ?: return null
        saveJobs.remove(id)?.cancel()
        _events.update { list -> list.filterNot { it.id == id } }
        track({ api.delete("/events/$id") }) { e ->
            _events.update { it + previous }
            fail(e, "No se pudo eliminar el evento")
        }
        return previous
    }

    fun restoreEvent(event: CalEvent) {
        _events.update { list -> if (list.any { it.id == event.id }) list else list + event }
        track({ api.post<EventResponse>("/events", api.encode(event)) }) { e ->
            _events.update { list -> list.filterNot { it.id == event.id } }
            fail(e, "No se pudo restaurar el evento")
        }
    }

    fun repeatEvent(event: CalEvent, offsets: List<Long>, label: String) {
        val base = Dates.parse(event.date)
        offsets.forEach { offset ->
            addEvent(
                event.copy(
                    date = Dates.iso(base.plusDays(offset)),
                    checklist = event.checklist.map { it.copy(id = uid(), done = false) },
                ),
            )
        }
        haptics.play(Haptic.Success)
        notify("Repetido $label")
    }

    fun newChecklistItem(text: String) = ChecklistItem(uid(), text, false)

    fun addTask(title: String, due: String?, tag: Category): Task {
        val task = Task(uid(), title, due, tag.key, false)
        _tasks.update { listOf(task) + it }
        track({ api.post<TaskResponse>("/tasks", api.encode(task)) }) { e ->
            _tasks.update { list -> list.filterNot { it.id == task.id } }
            fail(e, "No se pudo crear la tarea")
        }
        return task
    }

    fun updateTask(id: String, title: String? = null, due: String? = null, clearDue: Boolean = false, tag: Category? = null, done: Boolean? = null) {
        val previous = _tasks.value.firstOrNull { it.id == id } ?: return
        if (title != null && title.isBlank()) return
        val next = previous.copy(
            title = title ?: previous.title,
            due = if (clearDue) null else due ?: previous.due,
            tag = tag?.key ?: previous.tag,
            done = done ?: previous.done,
        )
        _tasks.update { list -> list.map { if (it.id == id) next else it } }
        val body = buildList {
            if (title != null) add("title" to title)
            if (due != null || clearDue) add("due" to (if (clearDue) null else due))
            if (tag != null) add("tag" to tag.key)
            if (done != null) add("done" to done)
        }
        track({ api.patch<TaskResponse>("/tasks/$id", api.obj(*body.toTypedArray())) }) { e ->
            _tasks.update { list -> list.map { if (it.id == id) previous else it } }
            fail(e, "No se pudo actualizar la tarea")
        }
    }

    fun toggleTask(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        val pendingBefore = _tasks.value.count { !it.done }
        updateTask(id, done = !task.done)
        if (!task.done && pendingBefore == 1) {
            viewModelScope.launch {
                delay(260)
                haptics.play(Haptic.Celebrate)
                notify("Bandeja vacía. Lo que queda es tiempo tuyo.")
            }
        }
    }

    fun deleteTask(id: String): Task? {
        val previous = _tasks.value.firstOrNull { it.id == id } ?: return null
        _tasks.update { list -> list.filterNot { it.id == id } }
        track({ api.delete("/tasks/$id") }) { e ->
            _tasks.update { it + previous }
            fail(e, "No se pudo eliminar la tarea")
        }
        return previous
    }

    fun restoreTask(task: Task) {
        _tasks.update { list -> if (list.any { it.id == task.id }) list else listOf(task) + list }
        track({ api.post<TaskResponse>("/tasks", api.encode(task)) }) { e ->
            _tasks.update { list -> list.filterNot { it.id == task.id } }
            fail(e, "No se pudo restaurar la tarea")
        }
    }

    fun scheduleTask(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        track({
            val data = api.post<ScheduleResponse>("/tasks/$id/schedule", api.obj())
            _events.update { it + data.event }
            _tasks.update { list -> list.map { if (it.id == id) data.task else it } }
            haptics.play(Haptic.Success)
            notify("«${task.title}» programada · ${Dates.dateMedium(Dates.parse(data.event.date))} ${Dates.time(data.event.start)}", "Deshacer") {
                deleteEvent(data.event.id)
                updateTask(id, done = false)
            }
        }) { e -> fail(e, "No se pudo programar la tarea") }
    }

    // ---------- Enfoque ----------

    private fun loadSessions() {
        viewModelScope.launch {
            runCatching { api.get<SessionsResponse>("/focus-sessions?date=${Dates.iso(Dates.today())}") }
                .onSuccess { data -> _focus.update { it.copy(sessions = data.sessions) } }
        }
    }

    private fun persistFocus(state: FocusState) {
        session.focusPreset = state.preset
        session.focusEndsAt = state.endsAt
        session.focusPaused = state.paused
    }

    fun chooseFocus(minutes: Int) {
        haptics.play(Haptic.Selection)
        val next = _focus.value.copy(preset = minutes, endsAt = 0L, paused = minutes * 60)
        _focus.value = next
        persistFocus(next)
        FocusAlarm.cancel(getApplication<Application>())
    }

    fun toggleFocus() {
        val current = _focus.value
        val next = if (current.running) {
            haptics.play(Haptic.Light)
            FocusAlarm.cancel(getApplication<Application>())
            current.copy(endsAt = 0L, paused = current.remaining(System.currentTimeMillis()))
        } else {
            haptics.play(Haptic.Heartbeat)
            val seconds = if (current.paused > 0) current.paused else current.preset * 60
            val endsAt = System.currentTimeMillis() + seconds * 1000L
            FocusAlarm.schedule(getApplication<Application>(), endsAt, current.preset)
            current.copy(endsAt = endsAt, paused = seconds)
        }
        _focus.value = next
        persistFocus(next)
    }

    fun resetFocus() {
        haptics.play(Haptic.Light)
        FocusAlarm.cancel(getApplication<Application>())
        val next = _focus.value.copy(endsAt = 0L, paused = _focus.value.preset * 60)
        _focus.value = next
        persistFocus(next)
    }

    /** Lo llama el temporizador visible cada segundo: cierra la sesión al llegar a cero. */
    fun checkFocusDone() {
        val current = _focus.value
        if (!current.running || current.remaining(System.currentTimeMillis()) > 0) return
        val minutes = current.preset
        val next = current.copy(endsAt = 0L, paused = 0)
        _focus.value = next
        persistFocus(next)
        haptics.play(Haptic.Celebrate)
        notify("Sesión de $minutes min completada. Eso es constancia.")
        viewModelScope.launch {
            try {
                val data = api.post<SessionResponse>("/focus-sessions", api.obj("minutes" to minutes))
                _focus.update { it.copy(sessions = listOf(data.session) + it.sessions) }
            } catch (e: Throwable) {
                notify("La sesión terminó, pero no se pudo guardar en el servidor.")
            }
        }
    }
}
