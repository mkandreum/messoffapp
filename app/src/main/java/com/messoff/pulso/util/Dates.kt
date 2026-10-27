package com.messoff.pulso.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

object Dates {
    val WEEKDAYS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val WEEKDAYS_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    val WEEKDAYS_MINI = listOf("L", "M", "X", "J", "V", "S", "D")
    val MONTHS = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    fun today(): LocalDate = LocalDate.now()
    fun iso(d: LocalDate): String = d.toString()
    fun parse(s: String): LocalDate = runCatching { LocalDate.parse(s) }.getOrElse { today() }

    private fun pad(n: Int) = n.toString().padStart(2, '0')
    private fun dow(d: LocalDate) = d.dayOfWeek.value - 1

    fun time(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return "${pad(m / 60)}:${pad(m % 60)}"
    }

    fun range(start: Int, duration: Int) = "${time(start)} – ${time(start + duration)}"

    fun duration(min: Int): String {
        if (min < 60) return "$min min"
        val h = min / 60
        val rest = min % 60
        return if (rest == 0) "$h h" else "$h h $rest min"
    }

    /** "1 h", "1,5 h", "45 min" para los chips de duración. */
    fun chip(min: Int): String =
        if (min < 60) "$min min" else "${(min / 60.0).toString().removeSuffix(".0").replace('.', ',')} h"

    fun dateLong(d: LocalDate) = "${WEEKDAYS[dow(d)]}, ${d.dayOfMonth} de ${MONTHS[d.monthValue - 1]}"
    fun dateMedium(d: LocalDate) = "${WEEKDAYS_SHORT[dow(d)]} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1].take(3)}"
    fun month(d: LocalDate) = "${MONTHS[d.monthValue - 1]} ${d.year}"
    fun weekdayShort(d: LocalDate) = WEEKDAYS_SHORT[dow(d)]

    fun weekNumber(d: LocalDate) = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun startOfWeek(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun weekDays(anchor: LocalDate): List<LocalDate> = (0..6).map { startOfWeek(anchor).plusDays(it.toLong()) }

    fun weekRange(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        return if (monday.month == sunday.month) {
            "${monday.dayOfMonth} – ${sunday.dayOfMonth} ${MONTHS[sunday.monthValue - 1].take(3)}"
        } else {
            "${monday.dayOfMonth} ${MONTHS[monday.monthValue - 1].take(3)} – ${sunday.dayOfMonth} ${MONTHS[sunday.monthValue - 1].take(3)}"
        }
    }

    fun monthMatrix(anchor: LocalDate): List<LocalDate> {
        val start = startOfWeek(anchor.withDayOfMonth(1))
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }

    fun nowMinutes(now: LocalDateTime = LocalDateTime.now()) = now.hour * 60 + now.minute

    fun relativeDay(target: LocalDate, from: LocalDate = today()): String {
        val diff = ChronoUnit.DAYS.between(from, target)
        return when {
            diff == 0L -> "Hoy"
            diff == 1L -> "Mañana"
            diff == -1L -> "Ayer"
            diff in 2..6 -> WEEKDAYS_SHORT[dow(target)]
            else -> dateMedium(target)
        }
    }

    fun countdown(date: String, start: Int, now: LocalDateTime = LocalDateTime.now()): String {
        val target = parse(date).atStartOfDay().plusMinutes(start.toLong())
        val diff = ChronoUnit.MINUTES.between(now, target)
        if (diff <= 0) return "Ahora"
        if (diff < 60) return "en $diff min"
        val h = diff / 60
        val m = diff % 60
        if (h < 24) return "en $h h" + if (m > 0) " $m min" else ""
        return relativeDay(target.toLocalDate(), now.toLocalDate())
    }

    fun epochMillis(date: String, start: Int): Long =
        parse(date).atStartOfDay().plusMinutes(start.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun gmtLabel(): String {
        val seconds = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds
        val minutes = seconds / 60
        val sign = if (minutes >= 0) "+" else "−"
        val abs = kotlin.math.abs(minutes)
        return "GMT$sign${abs / 60}" + if (abs % 60 != 0) ":${pad(abs % 60)}" else ""
    }
}
