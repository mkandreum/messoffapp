package com.messoff.pulso.util

import com.messoff.pulso.data.CalEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Eventos repetidos: el servidor guarda una sola fila con la regla y aquí se expande.
 * La primera aparición conserva el id de la serie; las demás son "serie__fecha".
 */
object Recurrence {
    private const val SEPARATOR = "__"

    val OPTIONS = listOf(
        "none" to "No se repite",
        "daily" to "Cada día",
        "weekdays" to "De lunes a viernes",
        "weekly" to "Cada semana",
        "monthly" to "Cada mes",
    )

    fun seriesId(id: String) = id.substringBefore(SEPARATOR)

    /** La fecha de una aparición que no es la primera; null si es la primera o no se repite. */
    fun occurrenceDate(id: String): LocalDate? =
        if (SEPARATOR in id) runCatching { LocalDate.parse(id.substringAfter(SEPARATOR)) }.getOrNull() else null

    fun occurrenceId(seriesId: String, date: LocalDate, first: LocalDate) =
        if (date == first) seriesId else "$seriesId$SEPARATOR$date"

    /** Las fechas de la serie entre [from] y [to], ambas incluidas. */
    fun dates(event: CalEvent, from: LocalDate, to: LocalDate): List<LocalDate> {
        val first = LocalDate.parse(event.date)
        if (!event.isRepeating) return if (first in from..to) listOf(first) else emptyList()
        val until = event.repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val last = if (until != null && until < to) until else to
        val out = ArrayList<LocalDate>()
        fun add(day: LocalDate) {
            if (day >= from && day.toString() !in event.repeatSkip) out += day
        }
        when (event.repeat) {
            "daily", "weekdays" -> {
                var day = if (first < from) from else first
                while (day <= last) {
                    val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                    if (event.repeat == "daily" || !weekend) add(day)
                    day = day.plusDays(1)
                }
            }
            "weekly" -> {
                val skip = if (first < from) ChronoUnit.DAYS.between(first, from) / 7 else 0L
                var day = first.plusWeeks(skip)
                while (day <= last) {
                    add(day)
                    day = day.plusWeeks(1)
                }
            }
            "monthly" -> {
                var step = if (first < from) ChronoUnit.MONTHS.between(first, from) else 0L
                while (true) {
                    val day = first.plusMonths(step)
                    if (day > last) break
                    // Un día 31 no aparece en los meses que no lo tienen.
                    if (day.dayOfMonth == first.dayOfMonth) add(day)
                    step += 1
                }
            }
            else -> add(first)
        }
        return out
    }

    /** Sustituye cada serie por sus apariciones dentro de la ventana. */
    fun expand(events: List<CalEvent>, from: LocalDate, to: LocalDate): List<CalEvent> =
        events.flatMap { event ->
            if (!event.isRepeating) {
                listOf(event)
            } else {
                val first = LocalDate.parse(event.date)
                dates(event, from, to).map { day ->
                    event.copy(id = occurrenceId(event.id, day, first), date = day.toString())
                }
            }
        }
}
