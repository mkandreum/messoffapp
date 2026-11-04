package com.messoff.pulso.util

import com.messoff.pulso.data.CalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun event(date: String, repeat: String, until: String? = null, skip: List<String> = emptyList()) =
        CalEvent(id = "a", title = "x", date = date, start = 600, duration = 60, repeat = repeat, repeatUntil = until, repeatSkip = skip)

    private fun days(e: CalEvent, from: String, to: String) =
        Recurrence.dates(e, LocalDate.parse(from), LocalDate.parse(to)).map { it.toString() }

    @Test
    fun semanal() = assertEquals(
        listOf("2026-11-05", "2026-11-12", "2026-11-19"),
        days(event("2026-11-05", "weekly"), "2026-11-01", "2026-11-20"),
    )

    @Test
    fun semanalDesdeMitadDeVentana() = assertEquals(
        listOf("2026-12-03"),
        days(event("2026-11-05", "weekly"), "2026-12-01", "2026-12-05"),
    )

    @Test
    fun laborablesSaltaFinDeSemana() = assertEquals(
        listOf("2026-11-06", "2026-11-09"),
        days(event("2026-11-06", "weekdays"), "2026-11-06", "2026-11-09"),
    )

    @Test
    fun mensualSinDia31() = assertEquals(
        listOf("2026-10-31", "2026-12-31", "2027-01-31"),
        days(event("2026-10-31", "monthly"), "2026-10-01", "2027-02-28"),
    )

    @Test
    fun respetaFinYExcepciones() = assertEquals(
        listOf("2026-11-02", "2026-11-04"),
        days(event("2026-11-02", "daily", "2026-11-04", listOf("2026-11-03")), "2026-11-01", "2026-11-30"),
    )

    @Test
    fun idsDeApariciones() {
        val out = Recurrence.expand(listOf(event("2026-11-05", "weekly")), LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-12"))
        assertEquals(listOf("a", "a__2026-11-12"), out.map { it.id })
        assertEquals("a", Recurrence.seriesId("a__2026-11-12"))
        assertEquals(LocalDate.parse("2026-11-12"), Recurrence.occurrenceDate("a__2026-11-12"))
        assertNull(Recurrence.occurrenceDate("a"))
    }

    @Test
    fun solapesEnColumnas() {
        val a = CalEvent("a", "a", "2026-11-05", 600, 60)
        val b = CalEvent("b", "b", "2026-11-05", 630, 60)
        val c = CalEvent("c", "c", "2026-11-05", 670, 30)
        val d = CalEvent("d", "d", "2026-11-05", 800, 30)
        val placed = DayLayout.place(listOf(a, b, c, d))
        assertEquals(Placement(0, 2), placed["a"])
        assertEquals(Placement(1, 2), placed["b"])
        assertEquals(Placement(0, 2), placed["c"])
        assertEquals(Placement(0, 1), placed["d"])
    }
}
