package com.zikrtime.app.data

import android.content.Context

/** Reading preferences are independent of the immutable Quran text. */
class QuranProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("quran_reading", Context.MODE_PRIVATE)

    data class Location(val surah: Int, val ayah: Int) {
        val key: String get() = "$surah:$ayah"
    }

    var wholeSurah: Boolean
        get() = prefs.getBoolean("whole_surah", false)
        set(value) { prefs.edit().putBoolean("whole_surah", value).apply() }

    var timer: QuranReadingTimer
        get() = QuranReadingTimer(prefs.getLong("timer_remaining", 0).coerceAtLeast(0),
            prefs.getBoolean("timer_paused", false), prefs.getBoolean("timer_finished", false))
        set(value) {
            prefs.edit().putLong("timer_remaining", value.remaining).putBoolean("timer_paused", value.paused)
                .putBoolean("timer_finished", value.finished).apply()
        }

    var dailyGoalMinutes: Int
        get() = prefs.getInt("daily_goal_minutes", 10).coerceIn(1, 180)
        set(value) { prefs.edit().putInt("daily_goal_minutes", value.coerceIn(1, 180)).apply() }

    var keepAwake: Boolean
        get() = prefs.getBoolean("keep_awake", true)
        set(value) { prefs.edit().putBoolean("keep_awake", value).apply() }

    fun stop(book: QuranBook): Location? = parse(prefs.getString("stop", null), book)
    fun note(location: Location): String = prefs.getString("note_" + location.key, "").orEmpty()

    fun saveStop(location: Location, note: String) {
        val editor = prefs.edit().putString("stop", location.key).putString("last", location.key)
        if (note.isBlank()) editor.remove("note_" + location.key)
        else editor.putString("note_" + location.key, note.take(2000))
        editor.apply()
    }

    fun notes(book: QuranBook): List<Location> = prefs.all.keys.filter { it.startsWith("note_") }
        .mapNotNull { parse(it.removePrefix("note_"), book) }.sortedWith(compareBy({ it.surah }, { it.ayah }))

    fun last(book: QuranBook): Location? = parse(prefs.getString("last", null), book)

    fun save(location: Location) {
        prefs.edit().putString("last", location.key).apply()
    }

    fun bookmarks(book: QuranBook): Set<Location> =
        prefs.getStringSet("bookmarks", emptySet()).orEmpty().mapNotNull { parse(it, book) }.toSet()

    fun toggle(location: Location, book: QuranBook): Set<Location> {
        val next = bookmarks(book).toMutableSet()
        if (!next.add(location)) next.remove(location)
        prefs.edit().putStringSet("bookmarks", next.map { it.key }.toSet()).apply()
        return next
    }

    companion object {
        fun parse(raw: String?, book: QuranBook): Location? {
            val parts = raw?.split(':') ?: return null
            if (parts.size != 2) return null
            val surah = parts[0].toIntOrNull() ?: return null
            val ayah = parts[1].toIntOrNull() ?: return null
            val metadata = book.surahs.firstOrNull { it.id == surah } ?: return null
            return if (ayah in 1..metadata.ayahCount) Location(surah, ayah) else null
        }
    }
}
