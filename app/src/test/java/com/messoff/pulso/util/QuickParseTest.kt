package com.messoff.pulso.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

@Serializable
data class ParseCase(val text: String, val title: String, val date: String, val start: Int)

/** Los casos viven en un JSON idéntico al de la web: los dos analizadores deben coincidir. */
class QuickParseTest {
    private val now = LocalDateTime.of(2026, 11, 4, 12, 33)

    @Test
    fun casosCompartidos() {
        val raw = javaClass.classLoader!!.getResource("parse-cases.json")!!.readText()
        Json.decodeFromString<List<ParseCase>>(raw).forEach { case ->
            val parsed = QuickParse.parse(case.text, now)
            assertEquals(case.text, case.title, parsed.title)
            assertEquals(case.text, case.date, parsed.date.toString())
            assertEquals(case.text, case.start, parsed.start)
        }
    }
}
