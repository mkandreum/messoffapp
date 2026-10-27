package com.messoff.pulso.data

import android.content.Context
import com.messoff.pulso.BuildConfig

/** Preferencias locales: servidor, token de sesión y estado del temporizador. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("pulso", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("baseUrl", null) ?: BuildConfig.API_BASE
        set(value) = prefs.edit().putString("baseUrl", value.trim().trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().apply { if (value == null) remove("token") else putString("token", value) }.apply()

    /** Tema guardado para pintar el primer fotograma sin parpadeo. */
    var theme: String
        get() = prefs.getString("theme", "paper") ?: "paper"
        set(value) = prefs.edit().putString("theme", value).apply()

    /** Calendarios ocultos: "me" o el id de un equipo. */
    var hiddenCalendars: Set<String>
        get() = prefs.getStringSet("hiddenCalendars", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("hiddenCalendars", value).apply()

    var lastCalendar: String
        get() = prefs.getString("lastCalendar", "") ?: ""
        set(value) = prefs.edit().putString("lastCalendar", value).apply()

    var calView: String
        get() = prefs.getString("calView", "agenda") ?: "agenda"
        set(value) = prefs.edit().putString("calView", value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()

    var focusPreset: Int
        get() = prefs.getInt("focusPreset", 25)
        set(value) = prefs.edit().putInt("focusPreset", value).apply()

    /** Marca de tiempo (ms) en la que termina la sesión en curso; 0 si está en pausa. */
    var focusEndsAt: Long
        get() = prefs.getLong("focusEndsAt", 0L)
        set(value) = prefs.edit().putLong("focusEndsAt", value).apply()

    /** Segundos restantes en pausa. */
    var focusPaused: Int
        get() = prefs.getInt("focusPaused", 25 * 60)
        set(value) = prefs.edit().putInt("focusPaused", value).apply()
}
