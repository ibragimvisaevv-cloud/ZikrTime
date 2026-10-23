package com.zikrtime.app.screentime

import com.zikrtime.app.prayer.Prayer
import java.time.Instant
import java.time.LocalDate

/** Событие системы о том, что окно приложения стало активным (resumed) или ушло в фон (paused). */
data class UsageEventLite(
    val packageName: String,
    val className: String,
    val resumed: Boolean,
    val timestamp: Long,
)

/** Чистая логика без Android — её легко проверять тестами. */
object UsageMath {

    /**
     * Сколько миллисекунд выбранные приложения были на экране начиная с [from].
     * События должны идти по времени. Если окно ещё открыто — считаем до [now].
     */
    fun foregroundMillis(events: List<UsageEventLite>, from: Long, now: Long): Long {
        val openSince = HashMap<String, Long>() // ключ: пакет/класс окна
        var total = 0L
        for (e in events) {
            val key = e.packageName + "/" + e.className
            if (e.resumed) {
                openSince.putIfAbsent(key, e.timestamp)
            } else {
                val start = openSince.remove(key) ?: continue
                total += overlap(start, e.timestamp, from, now)
            }
        }
        for (start in openSince.values) total += overlap(start, now, from, now)
        return total
    }

    /** Приложения, окно которых открыто прямо сейчас: пакет → когда окно стало активным. */
    fun openPackages(events: List<UsageEventLite>): Map<String, Long> {
        val open = LinkedHashMap<String, UsageEventLite>() // ключ окна -> событие открытия
        for (e in events) {
            val key = e.packageName + "/" + e.className
            if (e.resumed) open[key] = e else open.remove(key)
        }
        val result = HashMap<String, Long>()
        for (e in open.values) result[e.packageName] = maxOf(result[e.packageName] ?: 0L, e.timestamp)
        return result
    }

    private fun overlap(start: Long, end: Long, from: Long, to: Long): Long {
        val s = maxOf(start, from)
        val e = minOf(end, to)
        return if (e > s) e - s else 0L
    }
}

/** Начало намаза: [date] — день, для которого он рассчитан (иша может прийтись на следующие сутки). */
data class PrayerStart(val date: LocalDate, val prayer: Prayer, val time: Instant)

sealed class BlockReason {
    data object None : BlockReason()
    data class LimitReached(val usedMinutes: Int, val allowedMinutes: Int) : BlockReason()
    data class PrayerTime(val prayer: Prayer, val date: LocalDate, val endsAt: Instant) : BlockReason()
}

object BlockPolicy {

    /**
     * Решает, нужно ли закрыть соцсеть прямо сейчас.
     * Время намаза важнее лимита: в эти минуты соцсеть закрыта, даже если лимит не исчерпан.
     * [prayerStarts] — начала намазов, для которых блокировка ещё не снята кнопкой «Я совершил намаз».
     */
    fun decide(
        now: Instant,
        prayerStarts: List<PrayerStart>,
        prayerLockEnabled: Boolean,
        prayerLockMinutes: Int,
        usedMillis: Long?,
        allowedMinutes: Int,
    ): BlockReason {
        if (prayerLockEnabled && prayerLockMinutes > 0) {
            for (start in prayerStarts) {
                val end = start.time.plusSeconds(prayerLockMinutes * 60L)
                val active = !now.isBefore(start.time) && now.isBefore(end)
                if (active) return BlockReason.PrayerTime(start.prayer, start.date, end)
            }
        }
        if (usedMillis != null && usedMillis >= allowedMinutes * 60_000L) {
            return BlockReason.LimitReached((usedMillis / 60_000L).toInt(), allowedMinutes)
        }
        return BlockReason.None
    }
}
