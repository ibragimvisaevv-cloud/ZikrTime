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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
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
    private const val EXTRA_DUE = "expected_delivery"
    private const val EXTRA_SNOOZE = "snooze_delivery"
    private const val EXTRA_SUNRISE = "sunrise_time"
    private const val NEXT_ALARM = "next_alarm"
    private const val NEXT_TEST = "next_test"
    private const val LAST_FAJR = "last_fajr"

    private fun deliveryPrefs(context: Context) = context.getSharedPreferences("fajr_delivery", Context.MODE_PRIVATE)

    data class Signal(val kind: String, val plannedAt: Long, val firedAt: Long)

    private fun historyPrefs(context: Context) = context.getSharedPreferences("fajr_last_signal", Context.MODE_PRIVATE)

    fun lastSignal(context: Context): Signal? {
        val prefs = historyPrefs(context)
        val kind = prefs.getString("kind", null) ?: return null
        return Signal(kind, prefs.getLong("planned_at", 0L), prefs.getLong("fired_at", 0L))
    }

    internal fun recordSignal(context: Context, intent: Intent, test: Boolean) {
        val kind = when {
            test -> "test"
            intent.getBooleanExtra(EXTRA_SNOOZE, false) -> "snooze"
            else -> "fajr"
        }
        historyPrefs(context).edit()
            .putString("kind", kind)
            .putLong("planned_at", intent.getLongExtra(EXTRA_DUE, 0L))
            .putLong("fired_at", System.currentTimeMillis())
            .apply()
    }

    /** Когда прозвенит следующий будильник (без учёта «отложить»). */
    fun nextTrigger(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        if (!Store.alarmEnabled) return null
        return nextCityFajr(now, PrayerSchedule.zone(), Store.alarmDays, Store.alarmOffsetMinutes) {
            PrayerSchedule.forDate(it)[Prayer.FAJR]
        }
    }

    /** Время фаджра, к которому относится будильник (для подписи на экране будильника). */
    fun fajrFor(trigger: ZonedDateTime): ZonedDateTime = trigger.plusMinutes(Store.alarmOffsetMinutes.toLong())

    @Synchronized
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = deliveryPrefs(context)
        if (!Store.alarmEnabled) {
            prefs.edit().clear().apply()
            if (Store.alarmSnoozeAt != 0L) Store.alarmSnoozeAt = 0L
            am.cancel(firePendingIntent(context, RC_ALARM, false))
            am.cancel(firePendingIntent(context, RC_TEST, true))
            if (AlarmState.ringing) context.stopService(Intent(context, AlarmService::class.java))
            return
        }
        val now = ZonedDateTime.now()
        val next = nextTrigger(now)
        val snooze = Store.alarmSnoozeAt.takeIf {
            next != null && Store.alarmSnoozeAllowed && it > now.toInstant().toEpochMilli()
        }
        if (snooze == null && Store.alarmSnoozeAt != 0L) Store.alarmSnoozeAt = 0L
        if (next == null) {
            prefs.edit().remove(NEXT_ALARM).apply()
            am.cancel(firePendingIntent(context, RC_ALARM, false))
            return
        }
        val trigger = snooze ?: next.toInstant().toEpochMilli()
        val dailyFajr = fajrFor(next)
        val fajr = if (snooze == null) dailyFajr.toInstant().toEpochMilli() else prefs.getLong(LAST_FAJR, 0L)
        val sunrise = if (snooze == null) {
            val day = dailyFajr.withZoneSameInstant(PrayerSchedule.zone()).toLocalDate()
            val todaySunrise = PrayerSchedule.forDate(day)[Prayer.SUNRISE]
            // A published summer Fajr may be on the preceding civil date.
            val afterFajr = if (todaySunrise.isAfter(dailyFajr)) todaySunrise else PrayerSchedule.forDate(day.plusDays(1))[Prayer.SUNRISE]
            afterFajr.toInstant().toEpochMilli()
        } else 0L
        val pi = firePendingIntent(context, RC_ALARM, false, trigger, fajr, snooze != null, sunrise)
        setAlarm(context, am, trigger, pi)
        prefs.edit().putLong(NEXT_ALARM, trigger).apply()
    }

    /** Проверочный будильник через несколько секунд. */
    @Synchronized
    fun scheduleTest(context: Context, seconds: Int = 10) {
        if (!Store.alarmEnabled) return
        val am = context.getSystemService(AlarmManager::class.java)
        val trigger = System.currentTimeMillis() + seconds * 1000L
        setAlarm(context, am, trigger, firePendingIntent(context, RC_TEST, true, trigger))
        deliveryPrefs(context).edit().putLong(NEXT_TEST, trigger).apply()
    }

    private fun setAlarm(context: Context, am: AlarmManager, trigger: Long, pi: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "alarm"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (PrayerAlarms.canScheduleExact(context)) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), pi)
                return
            } catch (_: SecurityException) {
                // The exact alarm permission can be revoked between the check and the call.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
    }

    private fun firePendingIntent(
        context: Context, requestCode: Int, test: Boolean, due: Long = 0L, fajrMillis: Long = 0L,
        snooze: Boolean = false, sunrise: Long = 0L,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_TEST, test)
                .putExtra(EXTRA_DUE, due)
                .putExtra(EXTRA_SNOOZE, snooze)
                .putExtra(EXTRA_SUNRISE, sunrise)
                .putExtra(EXTRA_FAJR, fajrMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @Synchronized
    internal fun consume(context: Context, intent: Intent, test: Boolean): Boolean {
        val prefs = deliveryPrefs(context)
        val key = if (test) NEXT_TEST else NEXT_ALARM
        val due = intent.getLongExtra(EXTRA_DUE, 0L)
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val allowed = Store.alarmEnabled && (test || Store.alarmDays.isNotEmpty()) &&
            (!snooze || (Store.alarmSnoozeAllowed && Store.alarmSnoozeAt == due))
        val dailyFajr = if (!test && !snooze) intent.getLongExtra(EXTRA_FAJR, 0L) else 0L
        if (!validFajrDelivery(allowed, due, prefs.getLong(key, 0L), System.currentTimeMillis(),
                dailyFajr, intent.getLongExtra(EXTRA_SUNRISE, 0L))) return false
        prefs.edit().remove(key).putLong(LAST_FAJR, intent.getLongExtra(EXTRA_FAJR, 0L)).apply()
        if (!test) Store.alarmSnoozeAt = 0L
        return true
    }

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
        if (FajrAlarm.consume(context, intent, test) && !AlarmState.ringing) {
            try {
                AlarmService.start(context, test = test, fajrMillis = intent.getLongExtra(FajrAlarm.EXTRA_FAJR, 0L))
                FajrAlarm.recordSignal(context, intent, test)
            } catch (e: IllegalStateException) {
                android.util.Log.w("FajrAlarm", "Alarm service start was restricted", e)
            } catch (e: SecurityException) {
                android.util.Log.w("FajrAlarm", "Alarm service permission unavailable", e)
            }
        }
        // Сразу ставим следующий будильник (на завтра).
        if (!test) FajrAlarm.schedule(context)
    }
}

/** Drop a disabled, superseded, duplicate, or substantially delayed broadcast before starting loud audio. */
internal fun validFajrDelivery(
    allowed: Boolean, due: Long, expected: Long, now: Long,
    dailyFajr: Long = 0L, sunrise: Long = 0L,
): Boolean {
    if (!allowed || due <= 0L || due != expected || now < due) return false
    // A phone without exact alarm permission may deliver the real wake-up late. Preserve
    // that alarm until 30 min after Fajr, always stopping before sunrise. Tests and snoozes
    // are short-lived and must not come back later in the morning.
    if (dailyFajr >= due && sunrise > dailyFajr) return now < sunrise && now <= dailyFajr + 30 * 60_000L
    return now - due <= 5 * 60_000L
}

internal fun nextCityFajr(
    now: ZonedDateTime,
    cityZone: ZoneId,
    days: Set<DayOfWeek>,
    minutesBefore: Int,
    fajr: (LocalDate) -> ZonedDateTime,
): ZonedDateTime? {
    if (days.isEmpty()) return null
    val cityDay = now.withZoneSameInstant(cityZone).toLocalDate()
    for (offset in 0L..8L) {
        val day = cityDay.plusDays(offset)
        if (day.dayOfWeek !in days) continue
        val trigger = fajr(day).minusMinutes(minutesBefore.toLong())
        if (trigger.isAfter(now)) return trigger
    }
    return null
}
