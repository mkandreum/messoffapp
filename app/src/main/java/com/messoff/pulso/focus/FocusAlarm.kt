package com.messoff.pulso.focus

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
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.haptics.Haptics

/** Avisa al terminar una sesión de enfoque aunque la app esté en segundo plano. */
object FocusAlarm {
    const val CHANNEL = "focus"
    private const val REQUEST = 4401

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.focus_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso al terminar una sesión de enfoque"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 16, 50, 16, 50, 40)
        }
        manager.createNotificationChannel(channel)
    }

    private fun pending(context: Context, minutes: Int = 0): PendingIntent {
        val intent = Intent(context, FocusAlarmReceiver::class.java).putExtra("minutes", minutes)
        return PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(context: Context, at: Long, minutes: Int) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, minutes))
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, minutes))
            }
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }
}

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra("minutes", 25)
        Haptics(context).play(Haptic.Celebrate)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, FocusAlarm.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC9431F.toInt())
            .setContentTitle("Enfoque completado")
            .setContentText("$minutes minutos de atención real. Eso es constancia.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(4401, notification) }
    }
}
