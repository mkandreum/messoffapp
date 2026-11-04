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

    private val monthWords = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
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

    private fun atDay(day: Int, month: Int, year: Int?, base: LocalDate): LocalDate? {
        val date = runCatching { LocalDate.of(year ?: base.year, month, day) }.getOrNull() ?: return null
        // Sin año, una fecha ya pasada es la del año que viene.
        return if (year == null && date < base) date.plusYears(1) else date
    }

    /** Pasa a 24 horas según el momento del día: "9 de la noche" son las 21:00. */
    private fun withPeriod(hour: Int, period: String): Int = when (period) {
        "tarde" -> if (hour in 1..11) hour + 12 else hour
        "noche" -> when (hour) {
            12 -> 0
            in 7..11 -> hour + 12
            else -> hour
        }
        else -> if (hour == 12) 0 else hour
    }

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now(), fallbackDate: LocalDate? = null, fallbackStart: Int? = null): ParsedEvent {
        var text = raw.trim()
        var matchedDay = false
        var matchedTime = false
        var offset = 0L
        var start = fallbackStart ?: 0
        val base = fallbackDate ?: now.toLocalDate()
        var absolute: LocalDate? = null
        var sameWeekday = false

        fun take(h: Int, m: Int) {
            if (h in 0..23 && m in 0..59) {
                start = h * 60 + m
                matchedTime = true
            }
        }

        // Primero la hora con su momento: si no, "de la mañana" se leería como otro día.
        word("(?:a\\s+las?\\s+)?([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?(?:\\s+y\\s+(media|cuarto))?\\s+de\\s+la\\s+(mañana|manana|tarde|noche)").find(text)?.let {
            val extra = when (it.groupValues[3].lowercase()) { "media" -> 30; "cuarto" -> 15; else -> 0 }
            take(withPeriod(it.groupValues[1].toInt(), it.groupValues[4].lowercase()), it.groupValues[2].ifEmpty { "0" }.toInt() + extra)
            text = text.replaceFirst(it.value, " ")
        }
        var periodHour: Int? = null
        word("por\\s+la\\s+(mañana|manana|tarde|noche)").find(text)?.let {
            periodHour = when (it.groupValues[1].lowercase()) { "tarde" -> 16; "noche" -> 21; else -> 9 }
            text = text.replaceFirst(it.value, " ")
        }

        // Fechas concretas: "12 de noviembre", "12/11", "12/11/2026".
        word("(?:el\\s+)?(\\d{1,2})\\s+de\\s+(${monthWords.joinToString("|")})(?:\\s+(?:de\\s+)?(\\d{4}))?").find(text)?.let {
            val month = monthWords.indexOf(it.groupValues[2].lowercase()) + 1
            atDay(it.groupValues[1].toInt(), month, it.groupValues[3].toIntOrNull(), base)?.let { date ->
                absolute = date
                matchedDay = true
                text = text.replaceFirst(it.value, " ")
            }
        }
        if (absolute == null) {
            word("(?:el\\s+)?(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?").find(text)?.let {
                atDay(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toIntOrNull(), base)?.let { date ->
                    absolute = date
                    matchedDay = true
                    text = text.replaceFirst(it.value, " ")
                }
            }
        }

        if (!matchedDay) {
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
        }
        if (!matchedDay) {
            for ((w, dow) in weekdayWords) {
                val hit = word("(?:(próximo|proximo|este|el)\\s+)?$w").find(text) ?: continue
                offset = ((dow - base.dayOfWeek.value + 7) % 7).toLong()
                if (offset == 0L && hit.groupValues[1].lowercase().startsWith("pr")) offset = 7
                sameWeekday = offset == 0L
                matchedDay = true
                text = text.replaceFirst(hit.value, " ")
                break
            }
        }

        if (!matchedTime) {
            Regex("\\b([01]?\\d|2[0-3])[:.]([0-5]\\d)\\b").find(text)?.let {
                take(it.groupValues[1].toInt(), it.groupValues[2].toInt())
                text = text.replaceFirst(it.value, " ")
            }
        }
        if (!matchedTime) {
            Regex("\\ba\\s+las?\\s+([01]?\\d|2[0-3])\\b(?:\\s+y\\s+(media|cuarto))?", RegexOption.IGNORE_CASE).find(text)?.let {
                val extra = when (it.groupValues[2].lowercase()) { "media" -> 30; "cuarto" -> 15; else -> 0 }
                // "A las 5" en una agenda son casi siempre las 17:00.
                val hour = it.groupValues[1].toInt().let { h -> if (h in 1..6) h + 12 else h }
                take(hour, extra)
                text = text.replaceFirst(it.value, " ")
            }
        }
        if (!matchedTime) {
            Regex("\\b([01]?\\d|2[0-3])\\s*h(?:oras?)?\\b", RegexOption.IGNORE_CASE).find(text)?.let {
                take(it.groupValues[1].toInt(), 0)
                text = text.replaceFirst(it.value, " ")
            }
        }
        periodHour?.let { if (!matchedTime) take(it, 0) }
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

        // "Miércoles 09:00" escrito un miércoles a mediodía es el de la semana que viene.
        if (sameWeekday && matchedTime && base == now.toLocalDate() && start <= now.hour * 60 + now.minute) offset = 7

        val title = text.replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\s,·—-]+|[\\s,·—-]+$"), "")
            .replace(Regex("^(el|la|los|las|para)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        val finalTitle = if (title.isEmpty()) "Nuevo evento" else title.replaceFirstChar { it.uppercase() }

        return ParsedEvent(finalTitle, absolute ?: base.plusDays(offset), start, guessCategory(raw), matchedDay, matchedTime)
    }
}
