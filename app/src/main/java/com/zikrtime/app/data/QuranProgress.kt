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
