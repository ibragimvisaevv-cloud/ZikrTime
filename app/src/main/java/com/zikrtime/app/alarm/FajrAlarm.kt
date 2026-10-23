package com.zikrtime.app.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerAlarms
import com.zikrtime.app.prayer.PrayerSchedule
import java.time.ZonedDateTime

/**
 * Будильник на фаджр: за N минут до времени утреннего намаза (или ровно в это время).
 * Ставится через setAlarmClock — это самый надёжный будильник в Android: срабатывает
 * в спящем режиме и показывает значок будильника в строке состояния.
 */
object FajrAlarm {

    const val CHANNEL_ID = "fajr_alarm"
    private const val RC_ALARM = 2001
    private const val RC_TEST = 2002
    const val ACTION_FIRE = "com.zikrtime.app.FAJR_ALARM"
    const val EXTRA_TEST = "test"
    const val EXTRA_FAJR = "fajr_time"

    /** Когда прозвенит следующий будильник (без учёта «отложить»). */
    fun nextTrigger(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        if (!Store.alarmEnabled) return null
        val days = Store.alarmDays
        if (days.isEmpty()) return null
        for (offset in 0L..8L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in days) continue
            val fajr = PrayerSchedule.forDate(date, now.zone)[Prayer.FAJR]
            val trigger = fajr.minusMinutes(Store.alarmOffsetMinutes.toLong())
            if (trigger.isAfter(now)) return trigger
        }
        return null
    }

    /** Время фаджра, к которому относится будильник (для подписи на экране будильника). */
    fun fajrFor(trigger: ZonedDateTime): ZonedDateTime = trigger.plusMinutes(Store.alarmOffsetMinutes.toLong())

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        val snooze = Store.alarmSnoozeAt.takeIf { it > System.currentTimeMillis() }
        val next = nextTrigger(now)
        val trigger = snooze ?: next?.toInstant()?.toEpochMilli()
        val pi = firePendingIntent(context, RC_ALARM, test = false, fajrMillis = next?.let { fajrFor(it).toInstant().toEpochMilli() } ?: 0L)
        if (trigger == null) {
            am.cancel(pi)
            return
        }
        setAlarm(context, am, trigger, pi)
    }

    /** Проверочный будильник через несколько секунд. */
    fun scheduleTest(context: Context, seconds: Int = 10) {
        val am = context.getSystemService(AlarmManager::class.java)
        val trigger = System.currentTimeMillis() + seconds * 1000L
        setAlarm(context, am, trigger, firePendingIntent(context, RC_TEST, test = true, fajrMillis = 0L))
    }

    private fun setAlarm(context: Context, am: AlarmManager, trigger: Long, pi: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "alarm"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (PrayerAlarms.canScheduleExact(context)) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun firePendingIntent(context: Context, requestCode: Int, test: Boolean, fajrMillis: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_TEST, test)
                .putExtra(EXTRA_FAJR, fajrMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun createChannel(context: Context) {
        val res = Lang.wrap(context).resources
        val channel = NotificationChannel(CHANNEL_ID, res.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
            description = res.getString(R.string.channel_alarm_desc)
            setSound(null, null) // звук играет сама служба будильника
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Можно ли показывать будильник на весь экран (Android 14+ спрашивает разрешение). */
    fun canFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        if (intent.action != FajrAlarm.ACTION_FIRE) return
        val test = intent.getBooleanExtra(FajrAlarm.EXTRA_TEST, false)
        if (!test) Store.alarmSnoozeAt = 0L
        AlarmService.start(context, test = test, fajrMillis = intent.getLongExtra(FajrAlarm.EXTRA_FAJR, 0L))
        // Сразу ставим следующий будильник (на завтра).
        if (!test) FajrAlarm.schedule(context)
    }
}
