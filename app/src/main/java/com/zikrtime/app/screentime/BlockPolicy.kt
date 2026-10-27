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
    val screenOn: Boolean? = null,
    val unlocked: Boolean? = null,
    val reset: Boolean = false,
)

/** Чистая логика без Android — её легко проверять тестами. */
object UsageMath {

    private data class Result(val used: Long,val open: Map<String,Long>)

    /** Union of resumed windows, gated by both interactive screen and unlocked keyguard. */
    private fun measure(events: List<UsageEventLite>, from: Long, now: Long, count: Boolean): Result {
        val open=LinkedHashMap<String,UsageEventLite>()
        var interactive=true
        var unlocked=true
        var activeSince: Long?=null
        var total=0L
        for(e in events.sortedBy { it.timestamp }) {
            if(e.timestamp>now) break
            if(e.reset) open.clear()
            e.screenOn?.let { interactive=it }
            e.unlocked?.let { unlocked=it }
            if(e.screenOn==null && e.unlocked==null && !e.reset) {
                val key=e.packageName+"/"+e.className
                if(e.resumed) open[key]=e else open.remove(key)
            }
            val active=open.isNotEmpty() && interactive && unlocked
            if(active && activeSince==null) activeSince=e.timestamp
            if(!active && activeSince!=null) {
                if(count) total+=(minOf(e.timestamp,now)-maxOf(activeSince,from)).coerceAtLeast(0)
                activeSince=null
            }
        }
        if(count) activeSince?.let { total+=(now-maxOf(it,from)).coerceAtLeast(0) }
        val packages=if(interactive && unlocked) open.values.groupBy { it.packageName }
            .mapValues { (_,windows)->windows.maxOf { it.timestamp } } else emptyMap()
        return Result(total,packages)
    }
    fun foregroundMillis(events: List<UsageEventLite>,from: Long,now: Long): Long =
        if(now<=from) 0 else measure(events,from,now,true).used
    fun openPackages(events: List<UsageEventLite>): Map<String,Long> =
        measure(events,0,Long.MAX_VALUE,false).open

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
