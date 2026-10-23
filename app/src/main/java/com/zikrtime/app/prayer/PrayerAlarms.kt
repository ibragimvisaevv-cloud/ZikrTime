package com.zikrtime.app.prayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Уведомления о наступлении времени намаза.
 * Ставим один будильник — на ближайший намаз. Когда он срабатывает, ставим следующий.
 */
object PrayerAlarms {

    const val CHANNEL_ID = "prayer_times"
    private const val REQUEST_CODE = 1001
    private const val EXTRA_PRAYER = "prayer"
    private const val EXTRA_TIME = "time"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_prayer),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_prayer_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun scheduleNext(context: Context, after: ZonedDateTime = ZonedDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (!Store.prayerNotifications) {
            am.cancel(pendingIntent(context, null, 0L))
            return
        }
        val (prayer, time) = PrayerSchedule.next(after)
        val trigger = time.toInstant().toEpochMilli()
        val pi = pendingIntent(context, prayer, trigger)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            // Без разрешения на точные будильники система может задержать уведомление на несколько минут.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun pendingIntent(context: Context, prayer: Prayer?, trigger: Long): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            prayer?.let { putExtra(EXTRA_PRAYER, it.name) }
            putExtra(EXTRA_TIME, trigger)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun onAlarm(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        val trigger = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        val firedAt = Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault())

        if (prayer != null && Store.prayerNotifications) {
            showNotification(context, prayer, firedAt)
        }
        // Следующий будильник — строго после только что сработавшего намаза.
        scheduleNext(context, after = firedAt.plusSeconds(30))
    }

    private fun showNotification(context: Context, prayer: Prayer, time: ZonedDateTime) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val clock = time.format(DateTimeFormatter.ofPattern("HH:mm"))
        val lockLine = if (Store.blockingEnabled && Store.prayerLockEnabled) {
            " Соцсети закрыты на ${Store.prayerLockMinutes} мин."
        } else {
            ""
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Время намаза: ${prayer.title}")
            .setContentText("$clock · ${Store.location.name}.$lockLine")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(prayer.ordinal + 100, notification)
        } catch (_: SecurityException) {
            // Разрешение на уведомления отозвано — просто пропускаем.
        }
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        PrayerAlarms.onAlarm(context, intent)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        PrayerAlarms.scheduleNext(context)
        // После перезагрузки снова запускаем запасной режим блокировки, если он нужен.
        com.zikrtime.app.screentime.GuardService.sync(context)
    }
}
