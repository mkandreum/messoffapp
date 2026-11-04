package com.messoff.pulso

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messoff.pulso.data.ApiException
import com.messoff.pulso.data.CalendarLinkResponse
import com.messoff.pulso.data.LocalStore
import com.messoff.pulso.data.PendingOp
import com.messoff.pulso.data.Snapshot
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
import com.messoff.pulso.reminders.Reminders
import com.messoff.pulso.util.Dates
import com.messoff.pulso.util.Recurrence
import com.messoff.pulso.widget.TodayWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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

/** Estado de la sincronización que se enseña en la barra superior. */
data class SyncState(val offline: Boolean = false, val pending: Int = 0)

/** Lo que pide abrir una notificación o el widget. */
sealed interface OpenRequest {
    data class Event(val id: String) : OpenRequest
    data object NewEvent : OpenRequest
}

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
    private val store = pulso.store
    val haptics = pulso.haptics

    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private val _events = MutableStateFlow<List<CalEvent>>(emptyList())

    private val _hidden = MutableStateFlow(session.hiddenCalendars)
    val hiddenCalendars: StateFlow<Set<String>> = _hidden.asStateFlow()

    /** Solo los eventos de los calendarios visibles. */
    val events: StateFlow<List<CalEvent>> = combine(_events, _hidden) { list, hidden ->
        val visible = if (hidden.isEmpty()) list else list.filterNot { (it.teamId ?: "me") in hidden }
        val today = LocalDate.now()
        Recurrence.expand(visible, today.minusDays(WINDOW_BACK), today.plusDays(WINDOW_AHEAD))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _teams = MutableStateFlow<List<Team>>(emptyList())
    val teams: StateFlow<List<Team>> = _teams.asStateFlow()

    private val _invitations = MutableStateFlow<List<Invitation>>(emptyList())
    val invitations: StateFlow<List<Invitation>> = _invitations.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    /** Sin las de equipo que tiene asignadas otra persona: lo que te toca a ti. */
    val myTasks: StateFlow<List<Task>> = combine(_tasks, _auth) { list, auth ->
        val me = (auth as? AuthState.SignedIn)?.user?.id
        list.filter { it.assigneeId == null || it.assigneeId == me }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = _openRequest.asStateFlow()

    // Cambios pendientes de enviar: sobreviven a cerrar la app y se envían en orden.
    private val queue = ArrayDeque(store.loadQueue())
    private val rejects = HashMap<String, (Throwable) -> Unit>()
    private var flushJob: Job? = null
    private var sending: String? = null
    private var dataReady = false

    private val _sync = MutableStateFlow(SyncState(pending = queue.size))
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(net: Network) {
            viewModelScope.launch {
                flush()
                syncNow()
            }
        }
    }

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
                val waiting = queue.isNotEmpty() || _sync.value.offline
                if (_auth.value is AuthState.SignedIn && (_teams.value.isNotEmpty() || waiting)) {
                    flush()
                    syncNow()
                }
            }
        }
        // La copia local, los avisos y el widget siguen a los datos, sin prisa.
        viewModelScope.launch {
            combine(_events, _tasks, _teams, _invitations, _hidden) { _, _, _, _, _ -> }.collectLatest {
                delay(600)
                persistLocal()
            }
        }
        runCatching { app.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(network) }
        boot()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(network) }
    }

    private suspend fun persistLocal() {
        val user = (_auth.value as? AuthState.SignedIn)?.user ?: return
        if (!dataReady) return
        val context = getApplication<Application>()
        val snapshot = Snapshot(user, _events.value, _tasks.value, _teams.value, _invitations.value)
        val hidden = _hidden.value
        withContext(Dispatchers.IO) {
            store.saveSnapshot(snapshot)
            Reminders.sync(context, snapshot.events.filterNot { (it.teamId ?: "me") in hidden })
            TodayWidget.refresh(context)
        }
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val id = intent.getStringExtra(MainActivity.EXTRA_EVENT)
        when {
            id != null -> _openRequest.value = OpenRequest.Event(id)
            intent.getBooleanExtra(MainActivity.EXTRA_NEW, false) -> _openRequest.value = OpenRequest.NewEvent
        }
        intent.removeExtra(MainActivity.EXTRA_EVENT)
        intent.removeExtra(MainActivity.EXTRA_NEW)
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    val serverUrl: String get() = session.baseUrl
    val cachedTheme: String get() = session.theme

    fun boot() {
        if (session.token == null || session.baseUrl.isBlank()) {
            _auth.value = AuthState.SignedOut()
            return
        }
        // Con copia local se entra al momento; el servidor confirma por detrás.
        val cached = store.loadSnapshot()
        if (cached != null) {
            applySnapshot(cached)
            applyUser(cached.user)
            _auth.value = AuthState.SignedIn(cached.user)
        } else {
            _auth.value = AuthState.Loading
        }
        viewModelScope.launch {
            try {
                val data = api.get<UserResponse>("/auth/me")
                signedIn(data.user)
            } catch (e: Throwable) {
                when {
                    e is ApiException && e.status == 401 -> clearLocal()
                    cached != null -> setOffline(true)
                    else -> _auth.value = AuthState.SignedOut(e.message ?: "No se pudo comprobar la sesión.")
                }
            }
        }
    }

    private fun applySnapshot(data: Snapshot) {
        _events.value = data.events
        _tasks.value = data.tasks
        _teams.value = data.teams
        _invitations.value = data.invitations
        dataReady = true
    }

    private fun setOffline(offline: Boolean) {
        _sync.update { it.copy(offline = offline) }
    }

    private fun signedIn(user: User) {
        // Otra cuenta en este móvil: lo guardado no es suyo.
        val cached = store.loadSnapshot()
        if (cached != null && cached.user.id != user.id) clearStored()
        applyUser(user)
        _auth.value = AuthState.SignedIn(user)
        loadData()
        loadSessions()
    }

    private fun clearStored() {
        store.clear()
        queue.clear()
        rejects.clear()
        dataReady = false
        _sync.value = SyncState()
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

    /**
     * Un cambio de datos: se guarda en la cola y se envía en cuanto se pueda.
     * Sin conexión no se deshace nada; solo se deshace si el servidor lo rechaza.
     */
    private fun send(method: String, path: String, body: JsonElement?, onReject: (Throwable) -> Unit) {
        val text = body?.toString()
        val last = queue.lastOrNull()
        if (method == "PATCH" && last != null && last.method == "PATCH" && last.path == path && last.id != sending) {
            // Dos ediciones seguidas del mismo evento viajan como una sola.
            val merged = JsonObject(api.json.parseToJsonElement(last.body ?: "{}").jsonObject + api.json.parseToJsonElement(text ?: "{}").jsonObject)
            queue[queue.lastIndex] = last.copy(body = merged.toString())
        } else {
            val op = PendingOp(uid(), method, path, text)
            queue.addLast(op)
            rejects[op.id] = onReject
        }
        publishQueue()
        flush()
    }

    private fun publishQueue() {
        store.saveQueue(queue.toList())
        _sync.update { it.copy(pending = queue.size) }
    }

    private fun flush() {
        if (flushJob?.isActive == true || queue.isEmpty()) return
        flushJob = viewModelScope.launch {
            while (queue.isNotEmpty()) {
                val op = queue.first()
                sending = op.id
                try {
                    api.raw(op.method, op.path, op.body?.let { api.json.parseToJsonElement(it) })
                    setOffline(false)
                    finishOp(op)
                } catch (e: ApiException) {
                    when {
                        e.status == 0 || e.status == 429 || e.status >= 500 -> {
                            if (e.status == 0) setOffline(true)
                            break
                        }
                        e.status == 401 -> break
                        // Ya estaba creado o ya no existe: no hay nada más que hacer con él.
                        e.status == 404 || e.status == 409 -> finishOp(op)
                        else -> {
                            val reject = rejects[op.id]
                            finishOp(op)
                            reject?.invoke(e)
                        }
                    }
                }
            }
            sending = null
            lastSync = System.currentTimeMillis()
        }
    }

    private fun finishOp(op: PendingOp) {
        queue.removeFirstOrNull()
        rejects.remove(op.id)
        publishQueue()
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
            flush()
            flushJob?.join()
            runCatching { api.raw("POST", "/auth/logout") }
            clearLocal()
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            flush()
            flushJob?.join()
            runCatching { api.raw("POST", "/auth/logout-all") }
            clearLocal()
        }
    }

    private fun clearLocal(notice: String? = null) {
        session.token = null
        _events.value = emptyList()
        _tasks.value = emptyList()
        _teams.value = emptyList()
        _invitations.value = emptyList()
        clearStored()
        val context = getApplication<Application>()
        Reminders.clear(context)
        TodayWidget.refresh(context)
        _auth.value = AuthState.SignedOut(notice)
    }

    // ---------- Suscripción ICS ----------

    suspend fun calendarLink(): String? = api.get<CalendarLinkResponse>("/calendar/link").url

    suspend fun createCalendarLink(): String? = api.post<CalendarLinkResponse>("/calendar/link", api.obj()).url

    suspend fun revokeCalendarLink() = api.delete("/calendar/link")

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
                flush()
                flushJob?.join()
                // Lo pendiente manda: traer ahora del servidor lo pisaría.
                if (queue.isNotEmpty()) return@launch
                val data = api.get<BootstrapResponse>("/bootstrap")
                _events.value = data.events
                _tasks.value = data.tasks
                _teams.value = data.teams
                _invitations.value = data.invitations
                dataReady = true
                applyUser(data.user)
                _auth.value = AuthState.SignedIn(data.user)
                setOffline(false)
                lastSync = System.currentTimeMillis()
            } catch (e: Throwable) {
                if (dataReady) {
                    if (e is ApiException && e.status == 0) setOffline(true)
                } else if (!(e is ApiException && e.status == 401)) {
                    _loadError.value = e.message ?: "No se pudo cargar tu agenda."
                }
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
        flush()
        if (inflight.get() > 0 || saveJobs.isNotEmpty()) return
        viewModelScope.launch { syncNow() }
    }

    /** Trae los datos del servidor sin pisar cambios en vuelo. */
    private suspend fun syncNow() {
        if (inflight.get() > 0 || saveJobs.isNotEmpty() || queue.isNotEmpty()) return
        runCatching { api.get<BootstrapResponse>("/bootstrap") }.onSuccess { data ->
            if (inflight.get() == 0 && saveJobs.isEmpty() && queue.isEmpty()) {
                _events.value = data.events
                _tasks.value = data.tasks
                _teams.value = data.teams
                _invitations.value = data.invitations
                dataReady = true
                setOffline(false)
                lastSync = System.currentTimeMillis()
            }
        }.onFailure { e ->
            if (e is ApiException && e.status == 0) setOffline(true)
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

    /** El evento tal como se ve: la serie o una de sus apariciones. */
    private fun findEvent(id: String): CalEvent? {
        val base = _events.value.firstOrNull { it.id == Recurrence.seriesId(id) } ?: return null
        val day = Recurrence.occurrenceDate(id) ?: return base
        return base.copy(id = id, date = day.toString())
    }

    private fun sendEventPatch(id: String, body: JsonElement, onReject: (Throwable) -> Unit) =
        send("PATCH", "/events/$id", body, onReject)

    fun addEvent(input: CalEvent): CalEvent {
        val event = clamp(input.copy(id = uid()))
        _events.update { it + event }
        send("POST", "/events", api.encode(event)) { e ->
            _events.update { list -> list.filterNot { it.id == event.id } }
            fail(e, "No se pudo crear el evento")
        }
        return event
    }

    /**
     * Guarda los cambios de un evento o de toda su serie. Devuelve el id con el que
     * queda: cambia si se mueve de día una aparición que no es la primera.
     */
    fun saveEvent(input: CalEvent): String {
        val seriesId = Recurrence.seriesId(input.id)
        val base = _events.value.firstOrNull { it.id == seriesId }
        val occurrence = Recurrence.occurrenceDate(input.id)
        val merged = when {
            occurrence == null || base == null -> input
            !input.isRepeating -> input.copy(id = seriesId, repeatSkip = emptyList())
            else -> {
                // Mover una aparición de día desplaza toda la serie lo mismo.
                val shift = ChronoUnit.DAYS.between(occurrence, Dates.parse(input.date))
                input.copy(id = seriesId, date = Dates.parse(base.date).plusDays(shift).toString(), repeatSkip = base.repeatSkip)
            }
        }
        val next = clamp(merged)
        _events.update { list -> list.map { if (it.id == next.id) next else it } }
        saveJobs.remove(seriesId)?.cancel()
        saveJobs[seriesId] = viewModelScope.launch {
            delay(350)
            saveJobs.remove(seriesId)
            if (next.title.isBlank()) return@launch
            sendEventPatch(seriesId, api.encode(next)) { e ->
                fail(e, "No se pudo guardar el evento")
                loadData()
            }
        }
        return if (occurrence != null && next.isRepeating) {
            Recurrence.occurrenceId(seriesId, Dates.parse(input.date), Dates.parse(next.date))
        } else {
            seriesId
        }
    }

    private fun setSkipped(seriesId: String, day: String, skipped: Boolean) {
        val base = _events.value.firstOrNull { it.id == seriesId } ?: return
        val skip = if (skipped) (base.repeatSkip + day).distinct() else base.repeatSkip - day
        _events.update { list -> list.map { if (it.id == seriesId) it.copy(repeatSkip = skip) else it } }
        sendEventPatch(seriesId, api.obj("repeatSkip" to api.encode(skip))) { e ->
            _events.update { list -> list.map { if (it.id == seriesId) base else it } }
            fail(e, "No se pudo cambiar la serie")
        }
    }

    /** Saca una aparición de su serie y la convierte en un evento suelto. Devuelve cómo deshacerlo. */
    private fun splitOff(occurrence: CalEvent, edit: (CalEvent) -> CalEvent): Pair<CalEvent, () -> Unit> {
        val seriesId = Recurrence.seriesId(occurrence.id)
        val day = occurrence.date
        saveJobs.remove(seriesId)?.cancel()
        setSkipped(seriesId, day, true)
        val single = addEvent(
            edit(
                occurrence.copy(
                    repeat = "none", repeatUntil = null, repeatSkip = emptyList(),
                    checklist = occurrence.checklist.map { it.copy(id = uid()) },
                ),
            ),
        )
        return single to {
            deleteEvent(single.id)
            setSkipped(seriesId, day, false)
        }
    }

    /** Para editar solo una aparición: la separa y devuelve el evento suelto. */
    fun detachOccurrence(id: String): CalEvent? {
        val occurrence = findEvent(id) ?: return null
        return splitOff(occurrence) { it }.first
    }

    /** Devuelve cómo deshacer el movimiento, o null si no cambió nada. */
    fun moveEvent(id: String, date: String, start: Int): (() -> Unit)? {
        val previous = findEvent(id) ?: return null
        if (previous.date == date && previous.start == start) return null
        if (previous.isRepeating) return splitOff(previous) { it.copy(date = date, start = start) }.second
        saveJobs.remove(id)?.cancel()
        val next = clamp(previous.copy(date = date, start = start))
        _events.update { list -> list.map { if (it.id == id) next else it } }
        sendEventPatch(id, api.encode(next)) { e ->
            _events.update { list -> list.map { if (it.id == id) previous else it } }
            fail(e, "No se pudo mover el evento")
        }
        return { moveEvent(id, previous.date, previous.start) }
    }

    /** Cambia la duración arrastrando el borde. Devuelve cómo deshacerlo. */
    fun resizeEvent(id: String, duration: Int): (() -> Unit)? {
        val previous = findEvent(id) ?: return null
        if (previous.duration == duration) return null
        if (previous.isRepeating) return splitOff(previous) { it.copy(duration = duration) }.second
        saveJobs.remove(id)?.cancel()
        val next = clamp(previous.copy(duration = duration))
        _events.update { list -> list.map { if (it.id == id) next else it } }
        sendEventPatch(id, api.encode(next)) { e ->
            _events.update { list -> list.map { if (it.id == id) previous else it } }
            fail(e, "No se pudo cambiar la duración")
        }
        return { resizeEvent(id, previous.duration) }
    }

    fun duplicateEvent(id: String, onDone: (CalEvent) -> Unit) {
        val source = findEvent(id) ?: return
        onDone(
            addEvent(
                source.copy(
                    title = "${source.title} (copia)", repeat = "none", repeatUntil = null, repeatSkip = emptyList(),
                    checklist = source.checklist.map { it.copy(id = uid(), done = false) },
                ),
            ),
        )
    }

    /** Borra el evento o la serie entera. */
    fun deleteEvent(id: String): CalEvent? {
        val seriesId = Recurrence.seriesId(id)
        val previous = _events.value.firstOrNull { it.id == seriesId } ?: return null
        saveJobs.remove(seriesId)?.cancel()
        _events.update { list -> list.filterNot { it.id == seriesId } }
        send("DELETE", "/events/$seriesId", null) { e ->
            _events.update { it + previous }
            fail(e, "No se pudo eliminar el evento")
        }
        return previous
    }

    /** Quita solo este día de la serie. Devuelve cómo deshacerlo. */
    fun deleteOccurrence(id: String): (() -> Unit)? {
        val occurrence = findEvent(id) ?: return null
        val seriesId = Recurrence.seriesId(id)
        setSkipped(seriesId, occurrence.date, true)
        return { setSkipped(seriesId, occurrence.date, false) }
    }

    fun restoreEvent(event: CalEvent) {
        _events.update { list -> if (list.any { it.id == event.id }) list else list + event }
        send("POST", "/events", api.encode(event)) { e ->
            _events.update { list -> list.filterNot { it.id == event.id } }
            fail(e, "No se pudo restaurar el evento")
        }
    }

    fun newChecklistItem(text: String) = ChecklistItem(uid(), text, false)

    private fun personName(teamId: String?, id: String?) =
        _teams.value.firstOrNull { it.id == teamId }?.members?.firstOrNull { it.id == id }?.name

    fun addTask(title: String, due: String?, tag: Category, teamId: String? = null, assigneeId: String? = null): Task {
        val me = (_auth.value as? AuthState.SignedIn)?.user
        val task = Task(
            uid(), title, due, tag.key, false, teamId, assigneeId.takeIf { teamId != null },
            personName(teamId, assigneeId), me?.id, me?.name,
        )
        _tasks.update { listOf(task) + it }
        send("POST", "/tasks", api.encode(task)) { e ->
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
            status = when (done) {
                null -> previous.status
                true -> "done"
                false -> if (previous.status == "done") "todo" else previous.status
            },
        )
        _tasks.update { list -> list.map { if (it.id == id) next else it } }
        val body = buildList {
            if (title != null) add("title" to title)
            if (due != null || clearDue) add("due" to (if (clearDue) null else due))
            if (tag != null) add("tag" to tag.key)
            if (done != null) add("done" to done)
        }
        send("PATCH", "/tasks/$id", api.obj(*body.toTypedArray())) { e ->
            _tasks.update { list -> list.map { if (it.id == id) previous else it } }
            fail(e, "No se pudo actualizar la tarea")
        }
    }

    /** Mueve la tarea a otra columna del tablero. */
    fun setTaskStatus(id: String, status: String) {
        val previous = _tasks.value.firstOrNull { it.id == id } ?: return
        if (previous.status == status) return
        val next = previous.copy(status = status, done = status == "done")
        _tasks.update { list -> list.map { if (it.id == id) next else it } }
        send("PATCH", "/tasks/$id", api.obj("status" to status)) { e ->
            _tasks.update { list -> list.map { if (it.id == id) previous else it } }
            fail(e, "No se pudo mover la tarea")
        }
    }

    /** Cambia el equipo de la tarea y quién se encarga. */
    fun placeTask(id: String, teamId: String?, assigneeId: String?) {
        val previous = _tasks.value.firstOrNull { it.id == id } ?: return
        val assignee = assigneeId.takeIf { teamId != null }
        val next = previous.copy(teamId = teamId, assigneeId = assignee, assigneeName = personName(teamId, assignee))
        _tasks.update { list -> list.map { if (it.id == id) next else it } }
        send("PATCH", "/tasks/$id", api.obj("teamId" to teamId, "assigneeId" to assignee)) { e ->
            _tasks.update { list -> list.map { if (it.id == id) previous else it } }
            fail(e, "No se pudo asignar la tarea")
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
        send("DELETE", "/tasks/$id", null) { e ->
            _tasks.update { it + previous }
            fail(e, "No se pudo eliminar la tarea")
        }
        return previous
    }

    fun restoreTask(task: Task) {
        _tasks.update { list -> if (list.any { it.id == task.id }) list else listOf(task) + list }
        send("POST", "/tasks", api.encode(task)) { e ->
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

    private companion object {
        // Ventana en la que se expanden las series: de sobra para cualquier vista.
        const val WINDOW_BACK = 180L
        const val WINDOW_AHEAD = 730L
    }
}
