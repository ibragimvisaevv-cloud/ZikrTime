package com.zikrtime.app.prayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Пятничные напоминания. Тексты — смыслы аятов и достоверных хадисов с точными ссылками
 * (номера по общепринятой нумерации сборников). Перед выпуском их проверяет имам.
 *
 *  1. Сура «Аль-Кахф» — аль-Хаким, аль-Байхаки; аль-Албани, «Сахих аль-Джами'», 6470.
 *  2. Салават в пятницу — Абу Дауд, 1047.
 *  3. Час принятия дуа — аль-Бухари, 935; Муслим, 852.
 *  4. Искать его после асра — Абу Дауд, 1048.
 *  5. Гусль перед джума — аль-Бухари, 877; Муслим, 844.
 *  6. Коран, 62:9.
 *  7. Лучший из дней — Муслим, 854.
 */
object FridayReminders {
    const val CHANNEL_ID = "friday"
    private const val REQUEST = 3001
    private const val EXTRA_SLOT = "slot"

    /** Время и содержание: утро (Аль-Кахф и салават), перед джума, последний час перед магрибом. */
    enum class Slot(val title: Int, val items: List<Int>) {
        MORNING(R.string.friday_morning_title, listOf(1, 2)),
        JUMUAH(R.string.friday_jumuah_title, listOf(6, 5)),
        LAST_HOUR(R.string.friday_hour_title, listOf(3, 4)),
    }

    /** Все тексты по порядку (для карточки на главной в пятницу). */
    val itemNumbers = (1..7).toList()

    fun text(res: Resources, n: Int): String = res.getString(textIds[n - 1])
    fun source(res: Resources, n: Int): String = res.getString(sourceIds[n - 1])

    private val textIds = listOf(
        R.string.friday_1_text, R.string.friday_2_text, R.string.friday_3_text, R.string.friday_4_text,
        R.string.friday_5_text, R.string.friday_6_text, R.string.friday_7_text,
    )
    private val sourceIds = listOf(
        R.string.friday_1_source, R.string.friday_2_source, R.string.friday_3_source, R.string.friday_4_source,
        R.string.friday_5_source, R.string.friday_6_source, R.string.friday_7_source,
    )

    fun createChannel(context: Context) {
        val res = Lang.wrap(context).resources
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, res.getString(R.string.channel_friday), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Время каждого напоминания в указанную пятницу. */
    private fun timeOf(slot: Slot, friday: LocalDate): ZonedDateTime {
        val times = PrayerSchedule.forDate(friday)
        return when (slot) {
            Slot.MORNING -> {
                val nine = friday.atTime(LocalTime.of(9, 0)).atZone(times[Prayer.SUNRISE].zone)
                val afterSunrise = times[Prayer.SUNRISE].plusMinutes(30)
                if (nine.isAfter(afterSunrise)) nine else afterSunrise
            }
            Slot.JUMUAH -> times[Prayer.DHUHR].minusMinutes(60)
            Slot.LAST_HOUR -> times[Prayer.MAGHRIB].minusMinutes(60)
        }
    }

    /** Ближайшее напоминание после момента [after]. */
    fun next(after: ZonedDateTime = ZonedDateTime.now()): Pair<Slot, ZonedDateTime> {
        var friday = after.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        repeat(2) {
            for (slot in Slot.entries) {
                val t = timeOf(slot, friday)
                if (t.isAfter(after)) return slot to t
            }
            friday = friday.plusWeeks(1)
        }
        return Slot.MORNING to timeOf(Slot.MORNING, friday)
    }

    fun schedule(context: Context, after: ZonedDateTime = ZonedDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java)
        val cancel = pendingIntent(context, null)
        if (!Store.fridayReminders) {
            am.cancel(cancel)
            return
        }
        val (slot, time) = next(after)
        // Точность до минуты не нужна — система может сдвинуть напоминание на несколько минут.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.toInstant().toEpochMilli(), pendingIntent(context, slot))
    }

    private fun pendingIntent(context: Context, slot: Slot?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, FridayReceiver::class.java).apply { slot?.let { putExtra(EXTRA_SLOT, it.name) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun onAlarm(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(EXTRA_SLOT)?.let { runCatching { Slot.valueOf(it) }.getOrNull() }
        if (slot != null && Store.fridayReminders) show(context, slot)
        schedule(context, ZonedDateTime.now().plusMinutes(1))
    }

    private fun show(context: Context, slot: Slot) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val res = Lang.wrap(context).resources
        val body = slot.items.joinToString("\n\n") { "${text(res, it)} — ${source(res, it)}" }
        val open = PendingIntent.getActivity(
            context, REQUEST + 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(slot.title))
            .setContentText(text(res, slot.items.first()))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(REQUEST + slot.ordinal, notification)
        } catch (_: SecurityException) {
            // Notification permission may be revoked after areNotificationsEnabled().
        }
    }
}

class FridayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        FridayReminders.onAlarm(context, intent)
    }
}
