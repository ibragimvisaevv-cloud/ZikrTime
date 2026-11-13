package com.zikrtime.app.prayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.PrayerJournal
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.prayerName
import java.time.ZonedDateTime

/** One quiet, inexact alarm. No polling, network request, audio or background service. */
object PrayerCheckReminders {
    const val CHANNEL = "quiet_prayer_check_v1"
    const val ROUTE = "prayer_journal"
    const val EXTRA_DAY = "journal_day"
    const val EXTRA_PRAYER = "journal_prayer"
    private const val FIRE = "com.zikrtime.app.PRAYER_CHECK_REMINDER"
    private const val REQUEST = 10960
    private const val ENABLED = "prayer_check_enabled"
    private const val DELAY = "prayer_check_delay"
    private const val DUE = "prayer_check_due"
    private const val SHOWN = "prayer_check_shown"
    private const val HISTORY = "prayer_check_history"
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    var enabled: Boolean
        get() = Store.getRaw(ENABLED) == "true"
        set(value) = Store.putRaw(ENABLED, value.toString())

    var delayMinutes: Int
        get() = Store.getRaw(DELAY)?.toIntOrNull()?.takeIf { it in PrayerCheckPlan.delays } ?: 30
        set(value) = Store.putRaw(DELAY, (value.takeIf { it in PrayerCheckPlan.delays } ?: 30).toString())

    private val active get() = enabled && Store.onboarded && Store.personalProgressEnabled

    fun channel(context: Context): NotificationChannel {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, Lang.wrap(context).getString(R.string.prayer_check_title),
            NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) }
        manager.createNotificationChannel(channel)
        return manager.getNotificationChannel(CHANNEL)
    }

    /** Observe actual settings/journal writes so every entry point cancels obsolete reminders. */
    fun install(context: Context) {
        if (listener != null) return
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val update = Runnable { reschedule(app) }
        val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf(ENABLED, DELAY, "personal_progress_enabled", "onboarded", "city", "city_id", "lat", "lon",
                    "method", "madhab", "online_times", "online_updated_at", "language", "dumrf_dated_day_v2") ||
                listOf(PrayerJournal.PREFIX, "adj_", "regional_", "rt2_", "otimes2_", "ot_zone_", "manual_ot_zone_").any { key?.startsWith(it) == true }) {
                handler.removeCallbacks(update)
                handler.postDelayed(update, 150)
            }
        }
        listener = changes
        app.getSharedPreferences("zikrtime", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changes)
        handler.post(update)
    }

    private fun pending(context: Context, raw: String = ""): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, PrayerCheckReceiver::class.java).setAction(FIRE).putExtra("occurrence", raw),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun reschedule(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val allowed = active && manager.areNotificationsEnabled() && channel(context).importance != NotificationManager.IMPORTANCE_NONE
        if (!allowed) {
            alarm.cancel(pending(context))
            manager.cancel(REQUEST)
            save(DUE, "")
            save(SHOWN, "")
            return
        }
        val now = ZonedDateTime.now(PrayerSchedule.zone())
        val shown = PrayerCheckPlan.decode(Store.getRaw(SHOWN))
        if (shown != null && (now.toInstant() >= shown.expires ||
                !PrayerCheckPlan.unmarked(shown, Store::prayerMarks) || shown != expected(shown))) {
            manager.cancel(REQUEST)
            save(SHOWN, "")
        }
        PrayerCheckPlan.decode(Store.getRaw(DUE))?.let { deliver(context, it, now) }
        val next = PrayerCheckPlan.next(now, delayMinutes, Store.getRaw(HISTORY), Store::prayerMarks) {
            PrayerSchedule.forDate(it, now.zone)
        }
        val raw = next?.encode().orEmpty()
        save(DUE, raw)
        if (next == null) alarm.cancel(pending(context))
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.due.toEpochMilli(), pending(context, raw))
    }

    private fun expected(item: PrayerCheckOccurrence) = PrayerCheckPlan.occurrence(item.day, item.prayer, delayMinutes) {
        PrayerSchedule.forDate(it)
    }

    private fun deliver(context: Context, item: PrayerCheckOccurrence, now: ZonedDateTime) {
        if (!PrayerCheckPlan.canDeliver(item, expected(item), now.toInstant(), active,
                Store.prayerMarks(item.day)[item.prayer], Store.getRaw(HISTORY))) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || channel(context).importance == NotificationManager.IMPORTANCE_NONE) return
        val localized = Lang.wrap(context)
        val open = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, ROUTE)
            .putExtra(EXTRA_DAY, item.day.toString()).putExtra(EXTRA_PRAYER, item.prayer.name)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val target = PendingIntent.getActivity(context, REQUEST, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = localized.getString(R.string.prayer_check_notification, prayerName(localized.resources, item.prayer))
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.prayer_check_title))
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(target).setAutoCancel(true).setSilent(true).setOnlyAlertOnce(true)
            .setTimeoutAfter(item.expires.toEpochMilli() - now.toInstant().toEpochMilli())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, localized.getString(R.string.prayer_check_open), target)
            .build()
        try {
            manager.notify(REQUEST, notification)
            save(HISTORY, PrayerCheckPlan.recordDelivery(Store.getRaw(HISTORY), item))
            save(SHOWN, item.encode())
        } catch (_: SecurityException) { /* The notification permission can be revoked between checks. */ }
    }

    private fun save(key: String, value: String) {
        if (Store.getRaw(key).orEmpty() != value) Store.putRaw(key, value)
    }

    internal fun receive(context: Context, intent: Intent) {
        if (intent.action == FIRE) {
            val raw = intent.getStringExtra("occurrence")
            if (raw == Store.getRaw(DUE)) PrayerCheckPlan.decode(raw)?.let {
                deliver(context, it, ZonedDateTime.now(PrayerSchedule.zone()))
            }
        }
        reschedule(context)
    }
}

class PrayerCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        PrayerCheckReminders.receive(context, intent)
    }
}
