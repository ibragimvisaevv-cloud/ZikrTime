package com.zikrtime.app.ui

import java.time.Instant

enum class NaturalPeriod { DAWN, DAY, SUNSET, NIGHT }

/** Instant comparisons avoid device-zone and midnight ambiguity. */
fun naturalPeriod(now: Instant, fajr: Instant, sunrise: Instant, maghrib: Instant, isha: Instant): NaturalPeriod = when {
    now < fajr -> NaturalPeriod.NIGHT
    now < sunrise -> NaturalPeriod.DAWN
    now < maghrib.minusSeconds(45 * 60) -> NaturalPeriod.DAY
    now < isha -> NaturalPeriod.SUNSET
    else -> NaturalPeriod.NIGHT
}

/** Monotonic travel is independent of the selected goal and its modulo counter. */
class BeadTravel(initialCount: Int, initialPhrase: Int) {
    private var count = initialCount
    private var phrase = initialPhrase
    var steps = 0L; private set
    fun update(newCount: Int, newPhrase: Int): Boolean {
        val reset = newPhrase != phrase || newCount < count
        steps = if (reset) 0 else steps + (newCount - count).coerceAtLeast(0)
        count = newCount
        phrase = newPhrase
        return reset
    }
}
