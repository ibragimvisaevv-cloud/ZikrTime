package com.zikrtime.app.prayer

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Направление на Каабу по дуге большого круга. */
object Qibla {
    const val KAABA_LAT = 21.422487
    const val KAABA_LON = 39.826206

    /** Азимут на Каабу в градусах от истинного севера по часовой стрелке (0…360). */
    fun bearing(lat: Double, lon: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LON - lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Расстояние до Каабы в километрах. */
    fun distanceKm(lat: Double, lon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(KAABA_LAT - lat)
        val dLon = Math.toRadians(KAABA_LON - lon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(KAABA_LAT)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }
}
