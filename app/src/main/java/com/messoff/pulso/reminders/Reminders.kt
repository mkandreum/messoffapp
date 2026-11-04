package com.messoff.pulso.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.messoff.pulso.MainActivity
import com.messoff.pulso.R
import com.messoff.pulso.data.CalEvent
import com.messoff.pulso.data.LocalStore
import com.messoff.pulso.data.Session
import com.messoff.pulso.util.Dates
import com.messoff.pulso.util.Recurrence
import com.messoff.pulso.widget.TodayWidget
import java.time.LocalDate

/** Avisos de eventos: alarmas locales, así que funcionan sin red y sin la app abierta. */
object Reminders {
    const val CHANNEL = "events"
    private const val HORIZON_DAYS = 14L
    private const val PREFS = "reminders"
    private const val KEY = "scheduled"

    val OPTIONS: List<Pair<Int?, String>> = listOf(
        null to "Sin aviso",
        0 to "Al empezar",
        5 to "5 min",
        10 to "10 min",
        30 to "30 min",
        60 to "1 h",
        1440 to "1 día",
    )

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.events_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso antes de que empiece un evento"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 18, 90, 28)
        }
        manager.createNotificationChannel(channel)
    }

    private fun pending(context: Context, id: String, title: String = "", body: String = ""): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.messoff.REMINDER.$id")
            .putExtra("id", id)
            .putExtra("title", title)
            .putExtra("body", body)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Pone las alarmas de los próximos días y quita las que ya no valen. */
    fun sync(context: Context, events: List<CalEvent>) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val canExact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        val upcoming = Recurrence.expand(events.filter { it.remind != null }, today.minusDays(1), today.plusDays(HORIZON_DAYS))
            .map { it to Dates.epochMillis(it.date, it.start) - it.remind!! * 60_000L }
            .filter { (_, at) -> at > now }
            .sortedBy { it.second }
            .take(200)

        val previous = prefs.getStringSet(KEY, emptySet()).orEmpty()
        val ids = upcoming.map { it.first.id }.toSet()
        (previous - ids).forEach { alarm.cancel(pending(context, it)) }
        upcoming.forEach { (event, at) ->
            val time = "${Dates.relativeDay(Dates.parse(event.date))} · ${Dates.range(event.start, event.duration)}"
            val body = if (event.location.isBlank()) time else "$time · ${event.location}"
            val intent = pending(context, event.id, event.title, body)
            runCatching {
                if (canExact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        }
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    fun clear(context: Context) = sync(context, emptyList())

    /** Vuelve a poner las alarmas con la copia local, sin red ni interfaz. */
    fun syncFromStore(context: Context) {
        val snapshot = LocalStore(context).loadSnapshot() ?: return
        val hidden = Session(context).hiddenCalendars
        sync(context, snapshot.events.filterNot { (it.teamId ?: "me") in hidden })
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        // La siguiente alarma de la serie puede quedar fuera del horizonte: se repone aquí.
        Reminders.syncFromStore(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction("com.messoff.OPEN.$id")
                .putExtra(MainActivity.EXTRA_EVENT, id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC9431F.toInt())
            .setContentTitle(intent.getStringExtra("title").orEmpty())
            .setContentText(intent.getStringExtra("body").orEmpty())
            .setAutoCancel(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
    }
}

/** Las alarmas no sobreviven a un reinicio, a una actualización ni a un cambio de zona horaria. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.syncFromStore(context)
        TodayWidget.refresh(context)
    }
}
