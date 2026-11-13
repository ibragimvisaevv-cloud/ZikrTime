package com.zikrtime.app.prayer

import com.zikrtime.app.data.Store

object PrayerSignals {
    var brief: Boolean
        get() = Store.getRaw("prayer_brief_tone") == "1"
        set(value) = Store.putRaw("prayer_brief_tone", if (value) "1" else "0")

    fun inWindow(scheduled: Long, now: Long): Boolean =
        scheduled > 0 && scheduled <= now && now - scheduled <= 5 * 60_000L

    fun wasPosted(prayer: Prayer, scheduled: Long): Boolean =
        Store.getRaw("prayer_delivered_" + prayer.name) == scheduled.toString()

    data class Event(val kind: String, val detail: String, val scheduled: Long, val delivered: Long, val mode: String)

    fun posted(kind: String, detail: String, scheduled: Long, delivered: Long = System.currentTimeMillis(), mode: String = "") {
        if (kind == "prayer") Store.putRaw("prayer_delivered_" + detail, scheduled.toString())
        Store.putRaw("last_prayer_signal_v1", listOf(kind, detail, scheduled, delivered, mode).joinToString("|"))
    }

    fun last(): Event? {
        val p = Store.getRaw("last_prayer_signal_v1")?.split('|') ?: return null
        if (p.size != 5) return null
        val scheduled = p[2].toLongOrNull() ?: return null
        val delivered = p[3].toLongOrNull() ?: return null
        return Event(p[0], p[1], scheduled, delivered, p[4])
    }
}
