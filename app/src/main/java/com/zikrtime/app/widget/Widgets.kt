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
import com.zikrtime.app.ui.SkyPeriod
import com.zikrtime.app.ui.skyColors
import com.zikrtime.app.ui.skyPeriod
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Виджеты на рабочий стол: следующий намаз, все намазы дня, остаток экранного времени. */
object Widgets {
    private const val UPDATE_REQUEST = 4001

    private fun dark(context: Context): Boolean = when (Store.appearance) {
        "light" -> false
        "dark" -> true
        else -> context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun ink(context: Context) = if (dark(context)) 0xFFF0F4E9.toInt() else 0xFF102D26.toInt()
    private fun muted(context: Context) = if (dark(context)) 0xFFB8CBBE.toInt() else 0xFF4C6258.toInt()
    private fun accent(context: Context) = if (dark(context)) WidgetArt.GOLD_LIGHT else 0xFF124C40.toInt()


    fun updateAll(context: Context) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            Store.init(context)
            listOf(NextPrayerWidget::class.java to (160 to 180), CompactPrayerWidget::class.java to (160 to 84), TimelineWidget::class.java to (330 to 84), TodayWidget::class.java to (330 to 190)).forEach { (cls, fallback) ->
                ids(context, manager, cls).forEach { id -> runCatching { PrayerWidgetRenderer.update(context, manager, id, fallback) }.onFailure { android.util.Log.e("ZikrWidgets", "Update failed: $id", it) } }
            }
            updateScreenTime(context, manager)
            updateTasbih(context, manager)
            scheduleNextUpdate(context)
        }
    }

    fun updateScreenTime(context: Context, manager: AppWidgetManager = AppWidgetManager.getInstance(context)) {
        val ids = ids(context, manager, ScreenTimeWidget::class.java)
        ids.forEach { manager.updateAppWidget(it, screenTime(context, size(manager, it, 160, 160))) }
    }

    /** Виджет тасбиха: счёт, зикр, чётки. Вызывается и из приложения после каждого нажатия. */
    fun updateTasbih(context: Context, manager: AppWidgetManager = AppWidgetManager.getInstance(context)) {
        val ids = ids(context, manager, TasbihWidget::class.java)
        ids.forEach { runCatching { manager.updateAppWidget(it, tasbih(context, size(manager, it, 160, 160))) } }
    }

    private val zikrNames = listOf(
        R.string.zikr_1_name, R.string.zikr_2_name, R.string.zikr_3_name,
        R.string.zikr_4_name, R.string.zikr_5_name, R.string.zikr_6_name,
    )

    private fun tasbih(context: Context, size: Pair<Int, Int>): RemoteViews {
        val res = Lang.wrap(context).resources
        val views = RemoteViews(context.packageName, R.layout.widget_tasbih)
        views.setTextColor(R.id.w_count, ink(context))
        views.setTextColor(R.id.w_name, muted(context))
        views.setTextColor(R.id.w_reset, ink(context))
        val count = Store.tasbihCount
        val target = Store.tasbihTarget
        views.setTextViewText(R.id.w_count, (if (target > 0) count % target else count).toString())
        views.setTextViewText(R.id.w_name, res.getString(zikrNames[Store.tasbihPhrase.coerceIn(zikrNames.indices)]))
        views.setContentDescription(R.id.widget_root, res.getString(zikrNames[Store.tasbihPhrase.coerceIn(zikrNames.indices)]) + " " + count)
        views.setContentDescription(R.id.w_reset, res.getString(R.string.action_reset))
        if (context.resources.configuration.fontScale > 1.3f && size.first < 200) views.setViewVisibility(R.id.w_name, android.view.View.GONE)
        runCatching {
            views.setImageViewBitmap(
                R.id.w_bg,
                WidgetArt.horizon(context, size.first, size.second, dark(context), false),
            )
            views.setImageViewBitmap(R.id.w_beads, WidgetArt.beads(context, minOf(size.first, size.second) - 12, count, target, light = !dark(context)))
        }
        views.setOnClickPendingIntent(R.id.widget_root, tasbihAction(context, TasbihWidget.ACTION_ADD))
        views.setOnClickPendingIntent(R.id.w_reset, tasbihAction(context, TasbihWidget.ACTION_RESET))
        return views
    }

    private fun toggleAction(context: Context, index: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, 200 + index,
            Intent(context, TodayWidget::class.java).setAction(TodayWidget.ACTION_TOGGLE).putExtra(TodayWidget.EXTRA_INDEX, index),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun tasbihAction(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, TasbihWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Размер виджета в dp (для картинки фона). */
    private fun size(manager: AppWidgetManager, id: Int, defW: Int, defH: Int): Pair<Int, Int> {
        val o = manager.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: defW
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: defH
        return w.coerceIn(80, 560) to h.coerceIn(80, 560)
    }

    /** Небо для фона виджета — как в приложении: по времени суток. */
    private fun skyBitmap(context: Context, size: Pair<Int, Int>, now: ZonedDateTime, seed: Int, body: WidgetArt.Body? = null): android.graphics.Bitmap {
        val times = PrayerSchedule.forDate(now.toLocalDate(), now.zone)
        val period = skyPeriod(now, times)
        val day = !now.isBefore(times[Prayer.SUNRISE]) && now.isBefore(times[Prayer.MAGHRIB])
        val colors = if (dark(context)) when (period) {
            SkyPeriod.NIGHT -> intArrayOf(0xFF102B28.toInt(), 0xFF1C3E36.toInt())
            SkyPeriod.DAWN, SkyPeriod.SUNSET -> intArrayOf(0xFF263D32.toInt(), 0xFF46553B.toInt())
            else -> intArrayOf(0xFF123B2E.toInt(), 0xFF255342.toInt())
        } else when (period) {
            SkyPeriod.NIGHT -> intArrayOf(0xFFE5ECE0.toInt(), 0xFFF2F4E9.toInt())
            SkyPeriod.DAWN, SkyPeriod.SUNSET -> intArrayOf(0xFFF3EDDA.toInt(), 0xFFE5DBBE.toInt())
            else -> intArrayOf(0xFFF6F7ED.toInt(), 0xFFDCE8D9.toInt())
        }
        val glow = when (period) {
            SkyPeriod.NIGHT -> 0x403A7BD5
            SkyPeriod.DAWN -> 0x80FFA477.toInt()
            SkyPeriod.MORNING -> 0x55FFE2A0
            SkyPeriod.DAY -> 0x40FFF1C0
            SkyPeriod.GOLDEN -> 0x80FFC266.toInt()
            SkyPeriod.SUNSET -> 0x99FF8A4C.toInt()
        }
        val stars = when (period) {
            SkyPeriod.NIGHT -> 1f
            SkyPeriod.DAWN, SkyPeriod.SUNSET -> 0.5f
            else -> 0f
        }
        return WidgetArt.sky(context, size.first, size.second, colors, glow, stars, body ?: if (day) WidgetArt.Body.SUN else WidgetArt.Body.MOON, seed, ink = ink(context))
    }

    private fun ids(context: Context, manager: AppWidgetManager, cls: Class<*>): IntArray =
        manager.getAppWidgetIds(ComponentName(context, cls))

    private fun hasAny(context: Context): Boolean {
        val m = AppWidgetManager.getInstance(context)
        return listOf(NextPrayerWidget::class.java, CompactPrayerWidget::class.java, TimelineWidget::class.java, TodayWidget::class.java, ScreenTimeWidget::class.java, TasbihWidget::class.java)
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
        val now = ZonedDateTime.now(com.zikrtime.app.ui.CityZones.forCity(Store.location.id))
        val nextPrayer = PrayerSchedule.next(now).second
        val at = PrayerWidgetModel.nextRefresh(now, nextPrayer)
        // A single event alarm updates name, time and timer atomically. Chronometer ticks in the host.
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)

    }

    /** Для проверки на эмуляторе: виджет как на рабочем столе, на заданное время. */
    fun preview(context: Context, kind: Int, now: ZonedDateTime, size: Pair<Int, Int>): RemoteViews = when (kind) {
        0, 4, 5 -> PrayerWidgetRenderer.render(context, -1, size, now)
        1 -> PrayerWidgetRenderer.render(context, -1, size, now)
        3 -> tasbih(context, size)
        else -> screenTime(context, size)
    }

    /** Где сейчас день относительно шести колонок намазов (0…1, центры колонок — (i + 0,5) / 6). */
    private fun dayPosition(now: ZonedDateTime, times: com.zikrtime.app.prayer.PrayerTimes): Float {
        val list = Prayer.entries.map { times[it] }
        val i = list.indexOfLast { !it.isAfter(now) }
        fun f(a: ZonedDateTime, b: ZonedDateTime): Float {
            val total = java.time.Duration.between(a, b).seconds.toFloat()
            return if (total <= 0f) 0f else (java.time.Duration.between(a, now).seconds / total).coerceIn(0f, 1f)
        }
        val pos = when (i) {
            -1 -> 0.5f * f(now.toLocalDate().atStartOfDay(now.zone), list[0])
            list.lastIndex -> list.lastIndex + 0.5f + 0.5f * f(list.last(), now.toLocalDate().plusDays(1).atStartOfDay(now.zone))
            else -> i + 0.5f + f(list[i], list[i + 1])
        }
        return pos / list.size
    }

    private fun openApp(context: Context, open: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, open.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { open?.let { putExtra(MainActivity.EXTRA_OPEN, it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun screenTime(context: Context, size: Pair<Int, Int>): RemoteViews {
        val local = Lang.wrap(context)
        val res = local.resources
        val views = RemoteViews(context.packageName, R.layout.widget_screen)
        views.setTextViewText(R.id.w_label, res.getString(R.string.widget_screen_name))
        views.setTextColor(R.id.w_label, muted(context))
        views.setTextColor(R.id.w_value, ink(context))
        views.setTextColor(R.id.w_hint, muted(context))
        val used = if (Store.blockingEnabled) ScreenTime.usedTodayMillis(context) else null
        var fraction = 0f
        if (used == null) {
            views.setTextViewText(R.id.w_value, "—")
            views.setTextViewText(R.id.w_hint, res.getString(R.string.widget_screen_off))
        } else {
            val allowed = Store.allowedMinutes().toLong()
            val usedMin = used / 60_000
            val left = (allowed - usedMin).coerceAtLeast(0)
            views.setTextViewText(R.id.w_value, durationText(res, left))
            views.setTextViewText(R.id.w_hint, res.getString(R.string.widget_screen_left, durationText(res, allowed)))
            fraction = if (allowed > 0) left.toFloat() / allowed else 0f
        }
        runCatching {
            // Sage glass with a quiet horizon; the ring indicates remaining screen time.
            val bg = WidgetArt.horizon(context, size.first, size.second, dark(context), false)
            views.setImageViewBitmap(R.id.w_bg, bg)
            views.setViewVisibility(R.id.w_star, android.view.View.GONE)
            val sweep = when {
                used == null -> intArrayOf(0x66F7F2EA, 0x66F7F2EA)
                fraction > 0.5f -> intArrayOf(0xFFA9D5B9.toInt(), 0xFFE2C58F.toInt())
                fraction > 0.2f -> intArrayOf(WidgetArt.GOLD_DARK, WidgetArt.GOLD_LIGHT, WidgetArt.GOLD)
                else -> intArrayOf(0xFFE5484D.toInt(), 0xFFFFA36C.toInt(), 0xFFFF6B6B.toInt())
            }
            views.setImageViewBitmap(
                R.id.w_ring,
                WidgetArt.ring(context, 60, if (used == null) 0f else fraction, sweep, R.drawable.ic_nav_focus, accent(context), glow = used != null && fraction <= 0.2f),
            )
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, "focus"))
        return views
    }
}

/** Общая часть: система просит обновить виджет — обновляем все. */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetPreferences.remove(context, it) }; Widgets.updateAll(context) }
    override fun onDisabled(context: Context) = Widgets.updateAll(context)
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (old, new) -> WidgetPreferences.restore(context, old, new) }
        Widgets.updateAll(context)
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.updateAll(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) =
        Widgets.updateAll(context)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE && !intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)) Widgets.updateAll(context)
        if (intent.action in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) Widgets.updateAll(context)
    }
}

class NextPrayerWidget : BaseWidget()
class CompactPrayerWidget : BaseWidget()
class TimelineWidget : BaseWidget()
/** Намазы на сегодня: нажатие по намазу — отметить прочитанным (или снять отметку). */
class TodayWidget : BaseWidget() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return super.onReceive(context, intent)
        Store.init(context)
        val prayer = Prayer.entries.getOrNull(intent.getIntExtra(EXTRA_INDEX, -1)) ?: return
        val now = ZonedDateTime.now(com.zikrtime.app.ui.CityZones.forCity(Store.location.id))
        val today = now.toLocalDate()
        if (intent.hasExtra("date") && intent.getStringExtra("date") != today.toString()) { Widgets.updateAll(context); return }
        val times = PrayerSchedule.forDate(today, now.zone)
        if (!prayer.isPrayer || times[prayer].isAfter(now)) return
        Store.setPrayed(today, prayer, prayer !in Store.prayedOn(today))
        com.zikrtime.app.ui.Haptics.tick(context)
        Widgets.updateAll(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.zikrtime.app.PRAYED_TOGGLE"
        const val EXTRA_INDEX = "index"
    }
}
class ScreenTimeWidget : BaseWidget()

/** Тасбих на рабочем столе: нажатие — +1 (с лёгкой вибрацией), ↺ — сброс. */
class TasbihWidget : BaseWidget() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ADD -> {
                Store.init(context)
                Store.addTasbih()
                val target = Store.tasbihTarget
                val c = Store.tasbihCount
                when {
                    target > 0 && c % target == 0 -> com.zikrtime.app.ui.Haptics.done(context)
                    c % 33 == 0 -> com.zikrtime.app.ui.Haptics.lap(context)
                    else -> com.zikrtime.app.ui.Haptics.tick(context)
                }
                Widgets.updateTasbih(context)
            }
            ACTION_RESET -> {
                Store.init(context)
                Store.tasbihCount = 0
                Widgets.updateTasbih(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_ADD = "com.zikrtime.app.TASBIH_ADD"
        const val ACTION_RESET = "com.zikrtime.app.TASBIH_RESET"
    }
}
