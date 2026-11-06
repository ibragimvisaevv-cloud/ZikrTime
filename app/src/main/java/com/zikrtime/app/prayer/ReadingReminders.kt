package com.zikrtime.app.prayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import java.time.Instant
import java.time.ZonedDateTime

object ReadingReminders {
    const val CHANNEL = "quiet_reading_v1"
    private const val FIRE = "com.zikrtime.app.READING_REMINDER"
    private const val BASE = 10940

    fun title(kind: ReadingKind) = if (kind == ReadingKind.BEDTIME) R.string.reading_bedtime else R.string.reading_friday
    fun passage(kind: ReadingKind) = if (kind == ReadingKind.BEDTIME) R.string.reading_kursi else R.string.reading_kahf

    fun channel(context: Context): NotificationChannel {
        val manager = context.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, Lang.wrap(context).getString(R.string.reading_title), NotificationManager.IMPORTANCE_LOW)
        ch.setSound(null, null)
        ch.enableVibration(false)
        manager.createNotificationChannel(ch)
        return manager.getNotificationChannel(CHANNEL)
    }

    fun reschedule(context: Context) {
        // Replacing an inexact alarm on resume can swallow a reminder Android has delayed.
        // Recover only a previously scheduled occurrence still inside its delivery window.
        for (kind in ReadingKind.entries) {
            val due = Store.getRaw("reading_due_" + kind.name)?.toLongOrNull()
            if (due != null) deliverIfDue(context, kind, due)
            schedule(context, kind)
        }
    }

    fun schedule(context: Context, kind: ReadingKind) {
        val am = context.getSystemService(AlarmManager::class.java)
        val enabled = Store.readingEnabled(kind)
        val at = if (enabled) ReadingPlan.next(kind, Store.readingMinute(kind), ZonedDateTime.now()).toInstant().toEpochMilli() else 0L
        val dueKey = "reading_due_" + kind.name
        val saved = if (enabled) at.toString() else ""
        if (Store.getRaw(dueKey).orEmpty() != saved) Store.putRaw(dueKey, saved)
        val intent = Intent(context, ReadingReceiver::class.java).setAction(FIRE)
            .putExtra("kind", kind.name).putExtra("at", at)
        val pending = PendingIntent.getBroadcast(context, BASE + kind.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (enabled) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else {
            am.cancel(pending)
            NotificationManagerCompat.from(context).cancel(BASE + kind.ordinal)
        }
    }

    internal fun receive(context: Context, intent: Intent) {
        if (intent.action != FIRE) {
            if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) reschedule(context)
            return
        }
        val kind = ReadingKind.entries.firstOrNull { it.name == intent.getStringExtra("kind") } ?: return
        deliverIfDue(context, kind, intent.getLongExtra("at", 0L))
        schedule(context, kind)
    }

    private fun deliverIfDue(context: Context, kind: ReadingKind, millis: Long) {
        if (!Store.readingEnabled(kind) || millis <= 0L) return
        val date = ReadingPlan.dueDate(kind, Store.readingMinute(kind), Instant.ofEpochMilli(millis), ZonedDateTime.now()) ?: return
        val key = "reading_last_" + kind.name
        val history = Store.getRaw(key)
        if (!ReadingPlan.delivered(history, date) && show(context, kind)) {
            Store.putRaw(key, ReadingPlan.recordDelivery(history, date))
        }
    }

    private fun show(context: Context, kind: ReadingKind): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || channel(context).importance == NotificationManager.IMPORTANCE_NONE) return false
        val res = Lang.wrap(context).resources
        val open = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, kind.route)
            .putExtra("reading_delivery", System.currentTimeMillis() * 2 + kind.ordinal)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val target = PendingIntent.getActivity(context, BASE + kind.ordinal, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(title(kind)))
            .setContentText(res.getString(passage(kind)))
            .setContentIntent(target).setAutoCancel(true).setSilent(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try {
            manager.notify(BASE + kind.ordinal, notification)
            true
        } catch (_: SecurityException) { false }
    }
}

class ReadingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        ReadingReminders.receive(context, intent)
    }
}
