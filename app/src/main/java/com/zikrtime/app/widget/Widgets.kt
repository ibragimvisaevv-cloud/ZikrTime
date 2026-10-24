package com.zikrtime.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.screentime.ScreenTime
import com.zikrtime.app.ui.clockFormatter
import com.zikrtime.app.ui.durationText
import com.zikrtime.app.ui.prayerIconRes
import com.zikrtime.app.ui.prayerName
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Виджеты на рабочий стол: следующий намаз, все намазы дня, остаток экранного времени. */
object Widgets {
    private const val UPDATE_REQUEST = 4001

    fun updateAll(context: Context) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            Store.init(context)
            ids(context, manager, NextPrayerWidget::class.java).forEach { manager.updateAppWidget(it, nextPrayer(context)) }
            ids(context, manager, TodayWidget::class.java).forEach { manager.updateAppWidget(it, today(context)) }
            updateScreenTime(context, manager)
            scheduleNextUpdate(context)
        }
    }

    fun updateScreenTime(context: Context, manager: AppWidgetManager = AppWidgetManager.getInstance(context)) {
        val ids = ids(context, manager, ScreenTimeWidget::class.java)
        if (ids.isEmpty()) return
        val views = screenTime(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun ids(context: Context, manager: AppWidgetManager, cls: Class<*>): IntArray =
        manager.getAppWidgetIds(ComponentName(context, cls))

    private fun hasAny(context: Context): Boolean {
        val m = AppWidgetManager.getInstance(context)
        return listOf(NextPrayerWidget::class.java, TodayWidget::class.java, ScreenTimeWidget::class.java)
            .any { ids(context, m, it).isNotEmpty() }
    }

    /** Обновить виджеты в момент следующего намаза (и в полночь — новый день). */
    private fun scheduleNextUpdate(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, UPDATE_REQUEST,
            Intent(context, NextPrayerWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!hasAny(context)) {
            am.cancel(pi)
            return
        }
        val now = ZonedDateTime.now()
        val nextPrayer = PrayerSchedule.next(now).second
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val at = if (nextPrayer.isBefore(midnight)) nextPrayer else midnight
        am.setAndAllowWhileIdle(AlarmManager.RTC, at.toInstant().toEpochMilli() + 2_000, pi)
    }

    private fun openApp(context: Context, open: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, open.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { open?.let { putExtra(MainActivity.EXTRA_OPEN, it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextPrayer(context: Context): RemoteViews {
        val local = Lang.wrap(context)
        val res = local.resources
        val now = ZonedDateTime.now()
        val (prayer, at) = PrayerSchedule.next(now)
        val views = RemoteViews(context.packageName, R.layout.widget_next)
        views.setTextViewText(R.id.w_label, res.getString(R.string.home_next_prayer))
        views.setImageViewResource(R.id.w_icon, prayerIconRes(prayer))
        views.setTextViewText(R.id.w_name, prayerName(res, prayer))
        views.setTextViewText(R.id.w_time, at.format(clockFormatter(local)))
        // Обратный отсчёт тикает сам, без обновлений виджета.
        val left = at.toInstant().toEpochMilli() - System.currentTimeMillis()
        val format = res.getString(R.string.home_in_time, "%s")
        views.setChronometer(R.id.w_countdown, SystemClock.elapsedRealtime() + left, format, true)
        views.setChronometerCountDown(R.id.w_countdown, true)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private val columns = listOf(
        Triple(R.id.w_col0, R.id.w_name0, R.id.w_time0),
        Triple(R.id.w_col1, R.id.w_name1, R.id.w_time1),
        Triple(R.id.w_col2, R.id.w_name2, R.id.w_time2),
        Triple(R.id.w_col3, R.id.w_name3, R.id.w_time3),
        Triple(R.id.w_col4, R.id.w_name4, R.id.w_time4),
        Triple(R.id.w_col5, R.id.w_name5, R.id.w_time5),
    )
    private val icons = listOf(R.id.w_icon0, R.id.w_icon1, R.id.w_icon2, R.id.w_icon3, R.id.w_icon4, R.id.w_icon5)

    private fun today(context: Context): RemoteViews {
        val local = Lang.wrap(context)
        val res = local.resources
        val now = ZonedDateTime.now()
        val date = LocalDate.now()
        val times = PrayerSchedule.forDate(date)
        val next = PrayerSchedule.next(now).first
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.w_title, Cities.displayName(local, Store.location))
        val pattern = DateTimeFormatter.ofPattern("EEEE, d MMMM", Lang.locale(local))
        views.setTextViewText(R.id.w_subtitle, date.format(pattern).replaceFirstChar { it.uppercase(Lang.locale(local)) })
        val fmt = clockFormatter(local)
        Prayer.entries.forEachIndexed { i, p ->
            val (col, name, time) = columns[i]
            views.setTextViewText(name, prayerName(res, p))
            views.setTextViewText(time, times[p].format(fmt))
            views.setImageViewResource(icons[i], prayerIconRes(p))
            views.setInt(col, "setBackgroundResource", if (p == next) R.drawable.widget_pill else 0)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private fun screenTime(context: Context): RemoteViews {
        val local = Lang.wrap(context)
        val res = local.resources
        val views = RemoteViews(context.packageName, R.layout.widget_screen)
        views.setTextViewText(R.id.w_label, res.getString(R.string.widget_screen_name))
        val used = if (Store.blockingEnabled) ScreenTime.usedTodayMillis(context) else null
        if (used == null) {
            views.setTextViewText(R.id.w_value, "—")
            views.setTextViewText(R.id.w_hint, res.getString(R.string.widget_screen_off))
            views.setProgressBar(R.id.w_progress, 1000, 0, false)
        } else {
            val allowed = Store.allowedMinutes().toLong()
            val usedMin = used / 60_000
            val left = (allowed - usedMin).coerceAtLeast(0)
            views.setTextViewText(R.id.w_value, durationText(res, left))
            views.setTextViewText(R.id.w_hint, res.getString(R.string.widget_screen_left, durationText(res, allowed)))
            val progress = if (allowed > 0) (left * 1000 / allowed).toInt() else 0
            views.setProgressBar(R.id.w_progress, 1000, progress, false)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, "focus"))
        return views
    }
}

/** Общая часть: система просит обновить виджет — обновляем все. */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.updateAll(context)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) Widgets.updateAll(context)
    }
}

class NextPrayerWidget : BaseWidget()
class TodayWidget : BaseWidget()
class ScreenTimeWidget : BaseWidget()
