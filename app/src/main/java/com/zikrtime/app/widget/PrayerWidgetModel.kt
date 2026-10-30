package com.zikrtime.app.widget

import java.time.ZonedDateTime

/** Pure layout/time policy shared by the renderer and boundary tests. */
internal object PrayerWidgetModel {
    enum class Layout { COMPACT, WIDE, SQUARE, DAY }
    fun layout(width: Int, height: Int, fontScale: Float): Layout {
        val scale = fontScale.coerceAtLeast(1f)
        return when {
            width >= 280 && height >= (174 * scale).toInt() -> Layout.DAY
            width >= (280 * scale).toInt() && height < (148 * scale).toInt() -> Layout.WIDE
            height >= (148 * scale).toInt() -> Layout.SQUARE
            else -> Layout.COMPACT
        }
    }
    fun nextRefresh(now: ZonedDateTime, next: ZonedDateTime): Long =
        minOf(next.toInstant(), now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant()).toEpochMilli()
    fun countdownBase(elapsed: Long, nowMillis: Long, targetMillis: Long): Long = elapsed + (targetMillis - nowMillis).coerceAtLeast(0)
}
