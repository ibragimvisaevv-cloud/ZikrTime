package com.zikrtime.app.data

import com.zikrtime.app.prayer.Prayer
import java.util.Locale

internal object PrayerAdjustments {
    const val migrated = "adj2_migrated"

    fun key(city: City, prayer: Prayer): String {
        val place = city.id?.let { "city_$it" }
            ?: String.format(Locale.ROOT, "gps_%.4f_%.4f", city.latitude, city.longitude)
        return "adj2_${place}_${prayer.name}"
    }

    /** Old global adjustments belong only to the place selected at upgrade time. */
    fun migrate(existing: Map<String, *>, city: City): Map<String, Int> {
        if (existing[migrated] == true) return emptyMap()
        return buildMap {
            for (prayer in Prayer.entries) {
                val old = existing["adj_" + prayer.name] as? Int ?: continue
                val target = key(city, prayer)
                if (target !in existing) put(target, old.coerceIn(-30, 30))
            }
        }
    }
}
