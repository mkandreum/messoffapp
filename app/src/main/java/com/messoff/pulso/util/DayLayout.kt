package com.messoff.pulso.util

import com.messoff.pulso.data.CalEvent

data class Placement(val column: Int, val columns: Int)

/** Reparte los eventos solapados de un día en columnas, lado a lado. */
object DayLayout {
    /** Un evento muy corto ocupa al menos este tiempo en pantalla. */
    private const val MIN_VISUAL = 20

    fun place(events: List<CalEvent>): Map<String, Placement> {
        val out = HashMap<String, Placement>()
        val cluster = ArrayList<Pair<CalEvent, Int>>()
        val ends = ArrayList<Int>()
        var clusterEnd = -1

        fun flush() {
            cluster.forEach { (event, column) -> out[event.id] = Placement(column, ends.size) }
            cluster.clear()
            ends.clear()
        }

        events.sortedWith(compareBy({ it.start }, { -it.duration })).forEach { event ->
            if (event.start >= clusterEnd) {
                flush()
                clusterEnd = -1
            }
            val end = event.start + maxOf(event.duration, MIN_VISUAL)
            var column = ends.indexOfFirst { it <= event.start }
            if (column < 0) {
                column = ends.size
                ends += end
            } else {
                ends[column] = end
            }
            cluster += event to column
            clusterEnd = maxOf(clusterEnd, end)
        }
        flush()
        return out
    }
}
