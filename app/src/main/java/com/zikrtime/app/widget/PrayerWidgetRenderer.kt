package com.zikrtime.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.ui.CityZones
import com.zikrtime.app.ui.clockFormatter
import com.zikrtime.app.ui.prayerName
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField

internal object PrayerWidgetRenderer {
    fun update(c: Context, manager: AppWidgetManager, id: Int, fallback: Pair<Int, Int>) {
        val options = manager.getAppWidgetOptions(id)
        val now = ZonedDateTime.now(CityZones.forCity(Store.location.id))
        @Suppress("DEPRECATION")
        val sizes = if (Build.VERSION.SDK_INT >= 31) options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) else null
        val views = if (Build.VERSION.SDK_INT >= 31 && !sizes.isNullOrEmpty()) {
            RemoteViews(sizes.distinct().take(8).associateWith { render(c, id, it.width.toInt() to it.height.toInt(), now) })
        } else {
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, fallback.first)
            val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, fallback.second)
            val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, maxH)
            RemoteViews(render(c, id, maxW to minH, now), render(c, id, minW to maxH, now))
        }
        manager.updateAppWidget(id, views)
    }

    fun render(c: Context, id: Int, size: Pair<Int, Int>, now: ZonedDateTime, darkOverride: Boolean? = null, datesOverride: Boolean? = null): RemoteViews {
        val local = Lang.wrap(c)
        val res = local.resources
        val dark = darkOverride ?: WidgetPreferences.dark(c, id)
        val showDates = datesOverride ?: WidgetPreferences.dates(c,id)
        val ink = if (dark) 0xFFF8F1E3.toInt() else 0xFF173E34.toInt()
        val secondary = if (dark) 0xFFC3CFC1.toInt() else 0xFF526157.toInt()
        val gold = if (dark) 0xFFE3C58C.toInt() else 0xFF77572C.toInt()
        val type = PrayerWidgetModel.layout(size.first, size.second, c.resources.configuration.fontScale)
        val hasData = Store.onboarded && Store.location.latitude.isFinite() && Store.location.longitude.isFinite()
        val layout = if (!hasData) R.layout.widget_prayer_empty else when (type) {
            PrayerWidgetModel.Layout.COMPACT -> R.layout.widget_prayer_compact
            PrayerWidgetModel.Layout.WIDE -> R.layout.widget_prayer_wide
            PrayerWidgetModel.Layout.SQUARE -> R.layout.widget_prayer_square
            PrayerWidgetModel.Layout.DAY -> R.layout.widget_prayer_day
        }
        val v = RemoteViews(c.packageName, layout)
        v.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_surface_dark else R.drawable.widget_surface_light)
        // Architecture is a separate non-text layer; fully opaque surfaces protect against busy wallpapers.
        v.setImageViewBitmap(R.id.w_bg, WidgetArt.horizon(c, size.first, size.second, dark, type == PrayerWidgetModel.Layout.SQUARE))
        v.setOnClickPendingIntent(R.id.widget_root, open(c, if (hasData) "prayer" else "city"))
        if (!hasData) {
            v.setTextColor(R.id.w_empty_title, ink); v.setTextColor(R.id.w_empty_action, gold)
            v.setTextViewText(R.id.w_empty_action, res.getString(R.string.widget_setup_city))
            return v
        }
        val (next, at) = PrayerSchedule.next(now)
        val name = prayerName(res, next)
        v.setTextViewText(R.id.w_name, name)
        v.setTextColor(R.id.w_name, ink)
        v.setTextViewText(R.id.w_time, at.format(clockFormatter(local)))
        v.setTextColor(R.id.w_time, ink)
        v.setTextColor(R.id.w_countdown, gold)
        val exact = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        if (exact) {
            v.setChronometer(R.id.w_countdown, PrayerWidgetModel.countdownBase(SystemClock.elapsedRealtime(), now.toInstant().toEpochMilli(), at.toInstant().toEpochMilli()),
                if (type == PrayerWidgetModel.Layout.WIDE) "%s" else res.getString(R.string.home_in_time, "%s"), true)
            v.setChronometerCountDown(R.id.w_countdown, true)
        } else {
            // A RemoteViews Chronometer cannot install an onTick listener to stop at zero.
            // Without exact events use a truthful absolute time and a refresh action, never a negative live timer.
            v.setChronometer(R.id.w_countdown, SystemClock.elapsedRealtime(), null, false)
            v.setTextViewText(R.id.w_countdown, res.getString(R.string.widget_refresh))
            v.setOnClickPendingIntent(R.id.w_countdown, PendingIntent.getBroadcast(c, 9901,
                Intent(c, NextPrayerWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        val locale = Lang.locale(local)
        val date = now.toLocalDate()
        val gregorian = date.format(DateTimeFormatter.ofPattern(if (type == PrayerWidgetModel.Layout.DAY) "EEE, d MMM" else "d MMM · EEE", locale))
        val hijri = runCatching {
            val h = HijrahDate.from(date)
            "${h.get(ChronoField.DAY_OF_MONTH)} ${res.getStringArray(R.array.hijri_months)[h.get(ChronoField.MONTH_OF_YEAR)-1]}"
        }.getOrDefault("")
        if (type == PrayerWidgetModel.Layout.WIDE || type == PrayerWidgetModel.Layout.DAY) {
            v.setTextViewText(R.id.w_city, Cities.displayName(local, Store.location)); v.setTextColor(R.id.w_city, secondary)
        }
        if (type == PrayerWidgetModel.Layout.WIDE) { v.setTextColor(R.id.w_until, secondary); v.setTextViewText(R.id.w_until, res.getString(R.string.widget_countdown_label)) }
        if (type == PrayerWidgetModel.Layout.SQUARE || type == PrayerWidgetModel.Layout.DAY) {
            v.setTextViewText(R.id.w_date, if (type == PrayerWidgetModel.Layout.DAY && showDates) "$gregorian · $hijri" else gregorian)
            v.setTextColor(R.id.w_date, secondary)
        }
        if (type == PrayerWidgetModel.Layout.SQUARE) {
            v.setTextViewText(R.id.w_label, res.getString(R.string.home_next_prayer)); v.setTextColor(R.id.w_label, secondary)
            v.setTextViewText(R.id.w_hijri, hijri); v.setTextColor(R.id.w_hijri, secondary)
            v.setViewVisibility(R.id.w_hijri, if (showDates) View.VISIBLE else View.GONE)
        }
        if (type == PrayerWidgetModel.Layout.DAY) {
            val times = PrayerSchedule.forDate(date, now.zone)
            val prayed = Store.prayedOn(date)
            v.removeAllViews(R.id.w_schedule)
            Prayer.entries.chunked(3).forEach { prayers ->
                val row = RemoteViews(c.packageName, R.layout.widget_prayer_row)
                prayers.forEach { prayer ->
                    val cell = RemoteViews(c.packageName, R.layout.widget_prayer_cell)
                    val active = prayer == next && times[prayer].toInstant() == at.toInstant()
                    val mark = if (prayer in prayed) "✓ " else if (!prayer.isPrayer) "☀ " else if (active) "› " else ""
                    cell.setTextViewText(R.id.w_cell_name, mark + prayerName(res, prayer))
                    cell.setTextViewText(R.id.w_cell_time, times[prayer].format(clockFormatter(local)))
                    cell.setTextColor(R.id.w_cell_name, if (active) gold else secondary)
                    cell.setTextColor(R.id.w_cell_time, if (active) gold else ink)
                    val available = prayer.isPrayer && !times[prayer].isAfter(now)
                    val description = prayerName(res,prayer) + " " + times[prayer].format(clockFormatter(local)) +
                        if (available) ", " + res.getString(R.string.widget_tap_to_mark) else ""
                    cell.setContentDescription(R.id.w_cell, description)
                    if (available) cell.setOnClickPendingIntent(R.id.w_cell, PendingIntent.getBroadcast(c, 200 + prayer.ordinal,
                        Intent(c, TodayWidget::class.java).setAction(TodayWidget.ACTION_TOGGLE).putExtra(TodayWidget.EXTRA_INDEX, prayer.ordinal)
                            .putExtra("date", date.toString()), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    else cell.setOnClickPendingIntent(R.id.w_cell, open(c, "prayer"))
                    row.addView(R.id.w_row, cell)
                }
                v.addView(R.id.w_schedule, row)
            }
        }
        return v
    }

    private fun open(c: Context, route: String) = PendingIntent.getActivity(c, route.hashCode(), Intent(c, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_OPEN, route), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
