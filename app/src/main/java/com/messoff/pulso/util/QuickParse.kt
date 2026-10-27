package com.messoff.pulso.util

import com.messoff.pulso.data.Category
import java.time.LocalDate
import java.time.LocalDateTime

/** Port del analizador de la web: "Café con Lucía mañana 09:30". */
data class ParsedEvent(
    val title: String,
    val date: LocalDate,
    val start: Int,
    val category: Category?,
    val matchedDay: Boolean,
    val matchedTime: Boolean,
)

object QuickParse {
    private val weekdayWords = mapOf(
        "domingo" to 7, "lunes" to 1, "martes" to 2, "miércoles" to 3, "miercoles" to 3,
        "jueves" to 4, "viernes" to 5, "sábado" to 6, "sabado" to 6,
    )

    private val categoryWords = listOf(
        listOf("comida", "cena", "café", "cafe", "cerveza", "caña", "cumpleaños", "concierto", "cine", "brunch") to Category.Social,
        listOf("gimnasio", "yoga", "correr", "médico", "medico", "dentista", "analítica", "analitica", "fisio", "paseo") to Category.Salud,
        listOf("reunión", "reunion", "llamada", "demo", "cliente", "producto", "entrevista", "propuesta", "equipo", "okr") to Category.Trabajo,
    )

    private fun guessCategory(text: String): Category? {
        val lower = text.lowercase()
        return categoryWords.firstOrNull { (words, _) -> words.any { lower.contains(it) } }?.second
    }

    private fun word(w: String) = Regex("(?<![\\p{L}\\d])$w(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now(), fallbackDate: LocalDate? = null, fallbackStart: Int? = null): ParsedEvent {
        var text = raw.trim()
        var matchedDay = false
        var matchedTime = false
        var offset = 0L
        var start = fallbackStart ?: 0
        val base = fallbackDate ?: now.toLocalDate()

        val relative = listOf(
            word("pasado\\s+mañana") to 2L, word("pasado\\s+manana") to 2L,
            word("mañana") to 1L, word("manana") to 1L, word("hoy") to 0L,
        )
        for ((re, value) in relative) {
            if (re.containsMatchIn(text)) {
                offset = value
                matchedDay = true
                text = re.replaceFirst(text, " ")
                break
            }
        }
        if (!matchedDay) {
            for ((w, dow) in weekdayWords) {
                val re = word(w)
                if (re.containsMatchIn(text)) {
                    offset = ((dow - base.dayOfWeek.value + 7) % 7).toLong()
                    matchedDay = true
                    text = re.replaceFirst(text, " ")
                    break
                }
            }
        }

        fun take(h: Int, m: Int) {
            if (h in 0..23 && m in 0..59) {
                start = h * 60 + m
                matchedTime = true
            }
        }

        Regex("\\b([01]?\\d|2[0-3])[:.]([0-5]\\d)\\b").find(text)?.let {
            take(it.groupValues[1].toInt(), it.groupValues[2].toInt())
            text = text.replaceFirst(it.value, " ")
        }
        if (!matchedTime) {
            Regex("\\ba\\s+las\\s+([01]?\\d|2[0-3])\\b(?:\\s+y\\s+(media|cuarto))?", RegexOption.IGNORE_CASE).find(text)?.let {
                val extra = when (it.groupValues[2].lowercase()) { "media" -> 30; "cuarto" -> 15; else -> 0 }
                take(it.groupValues[1].toInt(), extra)
                text = text.replaceFirst(it.value, " ")
            }
        }
        if (!matchedTime) {
            Regex("\\b([01]?\\d|2[0-3])\\s*h(?:oras?)?\\b", RegexOption.IGNORE_CASE).find(text)?.let {
                take(it.groupValues[1].toInt(), 0)
                text = text.replaceFirst(it.value, " ")
            }
        }
        if (!matchedTime) {
            Regex("\\b([1-9]|1[0-2])\\s*(?:de la\\s+)?(tarde|noche)\\b", RegexOption.IGNORE_CASE).find(text)?.let {
                take(it.groupValues[1].toInt() + 12, 0)
                text = text.replaceFirst(it.value, " ")
            }
        }
        if (!matchedTime && fallbackStart == null) {
            val next = ((now.hour * 60 + now.minute + 30 + 29) / 30) * 30
            if (next >= 20 * 60 + 30) {
                start = 9 * 60
                if (!matchedDay) {
                    offset = 1
                    matchedDay = true
                }
            } else {
                start = next
            }
        }

        val title = text.replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\s,·—-]+|[\\s,·—-]+$"), "")
            .replace(Regex("^(el|la|los|las|para)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        val finalTitle = if (title.isEmpty()) "Nuevo evento" else title.replaceFirstChar { it.uppercase() }

        return ParsedEvent(finalTitle, base.plusDays(offset), start, guessCategory(raw), matchedDay, matchedTime)
    }
}
