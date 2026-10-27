package com.messoff.pulso.data

import kotlinx.serialization.Serializable

enum class Category(val key: String, val label: String) {
    Trabajo("trabajo", "Trabajo"),
    Personal("personal", "Personal"),
    Salud("salud", "Salud"),
    Social("social", "Social");

    companion object {
        fun of(key: String?): Category = entries.firstOrNull { it.key == key } ?: Trabajo
    }
}

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val theme: String = "paper",
    val haptics: Boolean = true,
    val createdAt: String? = null,
)

@Serializable
data class ChecklistItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
)

@Serializable
data class CalEvent(
    val id: String,
    val title: String,
    /** YYYY-MM-DD */
    val date: String,
    /** Minutos desde medianoche. */
    val start: Int,
    val duration: Int,
    val category: String = "trabajo",
    val location: String = "",
    val note: String = "",
    val attendees: List<String> = emptyList(),
    val checklist: List<ChecklistItem> = emptyList(),
) {
    val cat: Category get() = Category.of(category)
    val end: Int get() = start + duration
}

@Serializable
data class Task(
    val id: String,
    val title: String,
    val due: String? = null,
    val tag: String = "personal",
    val done: Boolean = false,
) {
    val cat: Category get() = Category.of(tag)
}

@Serializable
data class FocusSession(
    val id: String,
    val minutes: Int,
    val completedAt: String,
)

@Serializable data class UserResponse(val user: User)
@Serializable data class BootstrapResponse(val user: User, val events: List<CalEvent>, val tasks: List<Task>)
@Serializable data class EventResponse(val event: CalEvent)
@Serializable data class TaskResponse(val task: Task)
@Serializable data class ScheduleResponse(val event: CalEvent, val task: Task)
@Serializable data class SessionsResponse(val sessions: List<FocusSession>)
@Serializable data class SessionResponse(val session: FocusSession)
@Serializable data class ErrorResponse(val error: String? = null, val code: String? = null)
