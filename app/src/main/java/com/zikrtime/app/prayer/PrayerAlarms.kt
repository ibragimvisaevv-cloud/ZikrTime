package com.zikrtime.app.prayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.alarm.FajrAlarm
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.screentime.GuardService
import com.zikrtime.app.ui.clockFormatter
import com.zikrtime.app.ui.prayerName
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Уведомления о наступлении времени намаза.
 * Ставим один будильник — на ближайший намаз. Когда он срабатывает, ставим следующий.
 */
object PrayerAlarms {

    const val CHANNEL_ID = "prayer_times"
    const val CHANNEL_ADHAN_ID = "prayer_adhan"
    private const val REQUEST_CODE = 1001
    private const val EXTRA_PRAYER = "prayer"
    private const val EXTRA_TIME = "time"

    fun createChannels(context: Context) {
        val res = Lang.wrap(context).resources
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, res.getString(R.string.channel_prayer), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = res.getString(R.string.channel_prayer_desc) },
        )
        // Отдельный канал на каждую запись азана (звук канала нельзя поменять после создания).
        for (voice in AdhanSound.voices) {
            val uri = AdhanSound.uri(context, voice.id) ?: continue
            nm.createNotificationChannel(
                NotificationChannel(adhanChannel(voice.id), res.getString(R.string.channel_prayer_adhan) + " · " + res.getString(R.string.adhan_voice, voice.number), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = res.getString(R.string.channel_prayer_desc)
                    setSound(
                        uri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                },
            )
        }
        FajrAlarm.createChannel(context)
        FridayReminders.createChannel(context)
        GuardService.createChannel(context)
    }

    /** Канал уведомлений для выбранной записи азана (первая запись — прежний канал, чтобы не терять настройки). */
    fun adhanChannel(voiceId: String): String = if (voiceId == "adhan") CHANNEL_ADHAN_ID else "${CHANNEL_ADHAN_ID}_$voiceId"

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun scheduleNext(context: Context, after: ZonedDateTime = ZonedDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java)
        // Будильник на время намаза нужен и для уведомления, и чтобы вовремя закрыть соцсети.
        val lockNeeded = Store.blockingEnabled && Store.prayerLockEnabled
        if (!Store.prayerNotifications && !lockNeeded) {
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
        // Наступил намаз: будим защиту соцсетей (её могла остановить экономия батареи) и сразу проверяем.
        if (Store.blockingEnabled && Store.prayerLockEnabled) {
            com.zikrtime.app.screentime.GuardService.sync(context, checkNow = true)
        }
        // Следующий будильник — строго после только что сработавшего намаза.
        scheduleNext(context, after = firedAt.plusSeconds(30))
        com.zikrtime.app.widget.Widgets.updateAll(context)
    }

    private fun showNotification(context: Context, prayer: Prayer, time: ZonedDateTime) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val local = Lang.wrap(context)
        val res = local.resources

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val clock = time.format(clockFormatter(local))
        val place = Cities.displayName(local, Store.location)
        val text = buildString {
            append(res.getString(R.string.notify_prayer_text, clock, place))
            if (Store.blockingEnabled && Store.prayerLockEnabled) {
                append(' ')
                append(res.getString(R.string.notify_prayer_lock, Store.prayerLockMinutes))
            }
        }
        val voice = Store.notificationVoice
        val channel = if (Store.notificationAdhan && AdhanSound.available(context, voice)) adhanChannel(voice) else CHANNEL_ID
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.notify_prayer_title, prayerName(res, prayer)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
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

/** После перезагрузки, обновления приложения или смены времени — всё переставляем заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        PrayerAlarms.scheduleNext(context)
        FajrAlarm.schedule(context)
        FridayReminders.schedule(context)
        GuardService.sync(context)
        com.zikrtime.app.widget.Widgets.updateAll(context)
    }
}
