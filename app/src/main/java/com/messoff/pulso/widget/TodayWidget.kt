package com.messoff.pulso.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.messoff.pulso.MainActivity
import com.messoff.pulso.R
import com.messoff.pulso.data.CalEvent
import com.messoff.pulso.data.LocalStore
import com.messoff.pulso.data.Session
import com.messoff.pulso.util.Dates
import com.messoff.pulso.util.Recurrence
import java.time.LocalDate

/** Widget de inicio: lo que queda de hoy o, si no queda nada, lo de mañana. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        private val ROWS = listOf(
            Triple(R.id.widget_row1, R.id.widget_time1, R.id.widget_title1),
            Triple(R.id.widget_row2, R.id.widget_time2, R.id.widget_title2),
            Triple(R.id.widget_row3, R.id.widget_time3, R.id.widget_title3),
        )

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, build(context))
        }

        private fun open(context: Context, request: Int, configure: Intent.() -> Unit): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply(configure)
            return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun upcoming(context: Context): Pair<String, List<CalEvent>> {
            val snapshot = LocalStore(context).loadSnapshot()
            val hidden = Session(context).hiddenCalendars
            val today = LocalDate.now()
            val all = Recurrence.expand(
                snapshot?.events.orEmpty().filterNot { (it.teamId ?: "me") in hidden }, today, today.plusDays(1),
            )
            val nowMinutes = Dates.nowMinutes()
            val left = all.filter { it.date == Dates.iso(today) && it.end > nowMinutes }.sortedBy { it.start }
            if (left.isNotEmpty()) return "Hoy · ${Dates.dateMedium(today)}" to left
            val tomorrow = today.plusDays(1)
            return "Mañana · ${Dates.dateMedium(tomorrow)}" to all.filter { it.date == Dates.iso(tomorrow) }.sortedBy { it.start }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val (heading, events) = upcoming(context)
            views.setTextViewText(R.id.widget_heading, heading)
            views.setOnClickPendingIntent(R.id.widget_root, open(context, 0) {})
            views.setOnClickPendingIntent(R.id.widget_add, open(context, 1) { putExtra(MainActivity.EXTRA_NEW, true) })
            views.setViewVisibility(R.id.widget_empty, if (events.isEmpty()) View.VISIBLE else View.GONE)
            ROWS.forEachIndexed { index, (row, time, title) ->
                val event = events.getOrNull(index)
                if (event == null) {
                    views.setViewVisibility(row, View.GONE)
                } else {
                    views.setViewVisibility(row, View.VISIBLE)
                    views.setTextViewText(time, Dates.time(event.start))
                    views.setTextViewText(title, event.title)
                    views.setOnClickPendingIntent(row, open(context, 10 + index) {
                        action = "com.messoff.OPEN.${event.id}"
                        putExtra(MainActivity.EXTRA_EVENT, event.id)
                    })
                }
            }
            val more = events.size - ROWS.size
            views.setViewVisibility(R.id.widget_more, if (more > 0) View.VISIBLE else View.GONE)
            if (more > 0) views.setTextViewText(R.id.widget_more, "+$more más")
            return views
        }
    }
}
