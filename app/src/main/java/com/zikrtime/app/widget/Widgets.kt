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

    fun updateAll(context: Context) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            Store.init(context)
            ids(context, manager, NextPrayerWidget::class.java).forEach {
                manager.updateAppWidget(it, nextPrayer(context, size(manager, it, 160, 160)))
            }
            ids(context, manager, TodayWidget::class.java).forEach {
                manager.updateAppWidget(it, today(context, size(manager, it, 320, 160)))
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
        val count = Store.tasbihCount
        val target = Store.tasbihTarget
        views.setTextViewText(R.id.w_count, (if (target > 0) count % target else count).toString())
        views.setTextViewText(R.id.w_name, res.getString(zikrNames[Store.tasbihPhrase.coerceIn(zikrNames.indices)]))
        runCatching {
            views.setImageViewBitmap(
                R.id.w_bg,
                WidgetArt.sky(
                    context, size.first, size.second,
                    intArrayOf(0xFF1A7D63.toInt(), 0xFF0F5C4A.toInt(), 0xFF04231C.toInt()),
                    0x40FFE7A8, 0f, WidgetArt.Body.NONE, 41,
                ),
            )
            views.setImageViewBitmap(R.id.w_beads, WidgetArt.beads(context, minOf(size.first, size.second) - 12, count, target))
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
        val times = PrayerSchedule.forDate(now.toLocalDate())
        val period = skyPeriod(now, times)
        val day = !now.isBefore(times[Prayer.SUNRISE]) && now.isBefore(times[Prayer.MAGHRIB])
        val colors = skyColors(period).map { it.toArgb() }.toIntArray()
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
        return WidgetArt.sky(context, size.first, size.second, colors, glow, stars, body ?: if (day) WidgetArt.Body.SUN else WidgetArt.Body.MOON, seed)
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
        // Небо и кольца на виджетах меняются плавно — обновляем хотя бы раз в 20 минут.
        val at = listOf(nextPrayer, midnight, now.plusMinutes(20)).min()
        am.setAndAllowWhileIdle(AlarmManager.RTC, at.toInstant().toEpochMilli() + 2_000, pi)
    }

    /** Для проверки на эмуляторе: виджет как на рабочем столе, на заданное время. */
    fun preview(context: Context, kind: Int, now: ZonedDateTime, size: Pair<Int, Int>): RemoteViews = when (kind) {
        0 -> nextPrayer(context, size, now)
        1 -> today(context, size, now)
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

    private fun nextPrayer(context: Context, size: Pair<Int, Int>, now: ZonedDateTime = ZonedDateTime.now()): RemoteViews {
        val local = Lang.wrap(context)
        val res = local.resources
        val (prayer, at) = PrayerSchedule.next(now)
        val views = RemoteViews(context.packageName, R.layout.widget_next)
        views.setTextViewText(R.id.w_label, res.getString(R.string.home_next_prayer))
        runCatching {
            views.setImageViewBitmap(R.id.w_bg, skyBitmap(context, size, now, 11))
            views.setViewVisibility(R.id.w_star, android.view.View.GONE)
            val previous = PrayerSchedule.recentStarts(now).map { it.time }.filter { !it.isAfter(now.toInstant()) }.maxOrNull()
            val progress = previous?.let {
                val total = java.time.Duration.between(it, at.toInstant()).seconds.toFloat()
                val passed = java.time.Duration.between(it, now.toInstant()).seconds.toFloat()
                if (total > 0) passed / total else 0f
            } ?: 0f
            val soon = java.time.Duration.between(now, at).toMinutes() < 15
            views.setImageViewBitmap(
                R.id.w_ring,
                WidgetArt.ring(
                    context, 48, progress,
                    intArrayOf(WidgetArt.GOLD_DARK, WidgetArt.GOLD_LIGHT, WidgetArt.GOLD),
                    prayerIconRes(prayer), WidgetArt.GOLD_LIGHT, glow = soon,
                ),
            )
        }.onFailure { views.setImageViewResource(R.id.w_ring, prayerIconRes(prayer)) }
        views.setTextViewText(R.id.w_name, prayerName(res, prayer))
        views.setTextViewText(R.id.w_time, at.format(clockFormatter(local)))
        // Обратный отсчёт тикает сам, без обновлений виджета.
        val left = at.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
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

    private fun today(context: Context, size: Pair<Int, Int>, now: ZonedDateTime = ZonedDateTime.now()): RemoteViews {
        val local = Lang.wrap(context)
        val res = local.resources
        val date = now.toLocalDate()
        val times = PrayerSchedule.forDate(date)
        val next = PrayerSchedule.next(now).first
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.w_title, Cities.displayName(local, Store.location))
        val pattern = DateTimeFormatter.ofPattern("EEEE, d MMMM", Lang.locale(local))
        views.setTextViewText(R.id.w_subtitle, date.format(pattern).replaceFirstChar { it.uppercase(Lang.locale(local)) })
        val fmt = clockFormatter(local)
        val prayed = Store.prayedOn(date)
        Prayer.entries.forEachIndexed { i, p ->
            // Нажатие по намазу — отметить прочитанным; отмеченный показывает ✓ вместо иконки
            if (p.isPrayer) views.setOnClickPendingIntent(columns[i].first, toggleAction(context, i))
            val (col, name, time) = columns[i]
            views.setTextViewText(name, prayerName(res, p))
            views.setTextViewText(time, times[p].format(fmt))
            views.setImageViewResource(icons[i], if (p in prayed) R.drawable.ic_check else prayerIconRes(p))
            views.setInt(col, "setBackgroundResource", if (p == next) R.drawable.widget_pill_gold else 0)
            when {
                // Следующий намаз — на золотой плашке тёмным текстом
                p == next -> {
                    views.setTextColor(name, 0xCC2B1E00.toInt())
                    views.setTextColor(time, 0xFF2B1E00.toInt())
                    views.setInt(icons[i], "setColorFilter", 0xFF2B1E00.toInt())
                }
                // Прошедшие — приглушённо
                !times[p].isAfter(now) -> {
                    views.setTextColor(name, 0x80FFF6E0.toInt())
                    views.setTextColor(time, 0x99FFF6E0.toInt())
                    views.setInt(icons[i], "setColorFilter", 0x99E2B659.toInt())
                }
            }
        }
        // Живой отсчёт до следующего намаза
        val (nextPrayer, nextAt) = PrayerSchedule.next(now)
        val left = nextAt.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
        views.setChronometer(R.id.w_countdown, SystemClock.elapsedRealtime() + left, res.getString(R.string.widget_next_in, prayerName(res, nextPrayer), "%s"), true)
        views.setChronometerCountDown(R.id.w_countdown, true)
        val soon = left < 15 * 60_000L
        views.setInt(R.id.w_countdown, "setBackgroundResource", if (soon) R.drawable.widget_pill_gold else R.drawable.widget_chip)
        views.setTextColor(R.id.w_countdown, if (soon) 0xFF2B1E00.toInt() else 0xFFFFF6E0.toInt())
        // Подсказка, пока ничего не отмечено
        if (prayed.isEmpty() && Prayer.entries.any { it.isPrayer && !times[it].isAfter(now) }) {
            views.setTextViewText(R.id.w_subtitle, res.getString(R.string.widget_tap_to_mark))
        }
        runCatching {
            views.setImageViewBitmap(R.id.w_bg, skyBitmap(context, size, now, 23, WidgetArt.Body.NONE))
            views.setViewVisibility(R.id.w_star, android.view.View.GONE)
            if (size.second >= 140) {
                val midnight = date.atStartOfDay(now.zone)
                fun frac(t: ZonedDateTime) = (java.time.Duration.between(midnight, t).seconds / 86_400f).coerceIn(0f, 0.999f)
                views.setViewVisibility(R.id.w_arc, android.view.View.VISIBLE)
                views.setImageViewBitmap(
                    R.id.w_arc,
                    WidgetArt.dayArc(context, size.first - 28, 40, Prayer.entries.map { frac(times[it]) }, frac(now), Prayer.entries.indexOf(next)),
                )
            } else {
                views.setViewVisibility(R.id.w_arc, android.view.View.GONE)
            }
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private fun screenTime(context: Context, size: Pair<Int, Int>): RemoteViews {
        val local = Lang.wrap(context)
        val res = local.resources
        val views = RemoteViews(context.packageName, R.layout.widget_screen)
        views.setTextViewText(R.id.w_label, res.getString(R.string.widget_screen_name))
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
            // Фон «фокус»: ночная синева, переходящая в изумруд; кольцо меняет цвет, когда времени мало.
            val bg = WidgetArt.sky(
                context, size.first, size.second,
                intArrayOf(0xFF1A1C4A.toInt(), 0xFF173A5E.toInt(), 0xFF0B4A3D.toInt()),
                0x553DDC97, 0.7f, WidgetArt.Body.MOON, 37,
            )
            views.setImageViewBitmap(R.id.w_bg, bg)
            views.setViewVisibility(R.id.w_star, android.view.View.GONE)
            val sweep = when {
                used == null -> intArrayOf(0x66FFF6E0, 0x66FFF6E0)
                fraction > 0.5f -> intArrayOf(0xFF2BB673.toInt(), 0xFF9BE15D.toInt(), 0xFF3DDC97.toInt())
                fraction > 0.2f -> intArrayOf(WidgetArt.GOLD_DARK, WidgetArt.GOLD_LIGHT, WidgetArt.GOLD)
                else -> intArrayOf(0xFFE5484D.toInt(), 0xFFFFA36C.toInt(), 0xFFFF6B6B.toInt())
            }
            views.setImageViewBitmap(
                R.id.w_ring,
                WidgetArt.ring(context, 60, if (used == null) 0f else fraction, sweep, R.drawable.ic_nav_focus, WidgetArt.GOLD_LIGHT, glow = used != null && fraction <= 0.2f),
            )
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, "focus"))
        return views
    }
}

/** Общая часть: система просит обновить виджет — обновляем все. */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.updateAll(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) =
        Widgets.updateAll(context)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) Widgets.updateAll(context)
    }
}

class NextPrayerWidget : BaseWidget()
/** Намазы на сегодня: нажатие по намазу — отметить прочитанным (или снять отметку). */
class TodayWidget : BaseWidget() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return super.onReceive(context, intent)
        Store.init(context)
        val prayer = Prayer.entries.getOrNull(intent.getIntExtra(EXTRA_INDEX, -1)) ?: return
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val times = PrayerSchedule.forDate(today)
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
