package com.zikrtime.app.ui

import java.time.*

/** Offline IANA zones for bundled cities; coordinate lookup generated with timezonefinder. */
object CityZones {
    private val zones = mapOf(
        "moscow" to "Europe/Moscow",
        "saint_petersburg" to "Europe/Moscow",
        "grozny" to "Europe/Moscow",
        "gudermes" to "Europe/Moscow",
        "makhachkala" to "Europe/Moscow",
        "khasavyurt" to "Europe/Moscow",
        "derbent" to "Europe/Moscow",
        "nazran" to "Europe/Moscow",
        "nalchik" to "Europe/Moscow",
        "vladikavkaz" to "Europe/Moscow",
        "cherkessk" to "Europe/Moscow",
        "maykop" to "Europe/Moscow",
        "kazan" to "Europe/Moscow",
        "ufa" to "Asia/Yekaterinburg",
        "astrakhan" to "Europe/Astrakhan",
        "nizhny_novgorod" to "Europe/Moscow",
        "samara" to "Europe/Samara",
        "yekaterinburg" to "Asia/Yekaterinburg",
        "novosibirsk" to "Asia/Novosibirsk",
        "tashkent" to "Asia/Tashkent",
        "samarkand" to "Asia/Samarkand",
        "bukhara" to "Asia/Samarkand",
        "namangan" to "Asia/Tashkent",
        "andijan" to "Asia/Tashkent",
        "fergana" to "Asia/Tashkent",
        "nukus" to "Asia/Samarkand",
        "qarshi" to "Asia/Samarkand",
        "urgench" to "Asia/Samarkand",
        "dushanbe" to "Asia/Dushanbe",
        "khujand" to "Asia/Dushanbe",
        "kulob" to "Asia/Dushanbe",
        "bokhtar" to "Asia/Dushanbe",
        "istaravshan" to "Asia/Dushanbe",
        "khorog" to "Asia/Dushanbe",
        "almaty" to "Asia/Almaty",
        "astana" to "Asia/Almaty",
        "shymkent" to "Asia/Almaty",
        "aktobe" to "Asia/Aqtobe",
        "karaganda" to "Asia/Almaty",
        "taraz" to "Asia/Almaty",
        "turkistan" to "Asia/Almaty",
        "atyrau" to "Asia/Atyrau",
        "bishkek" to "Asia/Bishkek",
        "osh" to "Asia/Bishkek",
        "jalal_abad" to "Asia/Bishkek",
        "karakol" to "Asia/Bishkek",
        "baku" to "Asia/Baku",
        "ganja" to "Asia/Baku",
        "sumgait" to "Asia/Baku",
        "istanbul" to "Europe/Istanbul",
        "ankara" to "Europe/Istanbul",
        "bursa" to "Europe/Istanbul",
        "mecca" to "Asia/Riyadh",
        "medina" to "Asia/Riyadh",
        "cairo" to "Africa/Cairo",
        "dubai" to "Asia/Dubai",
        "london" to "Europe/London",
        "berlin" to "Europe/Berlin",
        "amsterdam" to "Europe/Amsterdam",
        "paris" to "Europe/Paris",
        "new_york" to "America/New_York",
    )
    fun known(id: String?): ZoneId? = zones[id]?.let(ZoneId::of) ?: com.zikrtime.app.data.Cities.regionZone(id)
        ?: id?.let { com.zikrtime.app.prayer.RegionalFeeds.source(it)?.zone }

    /** Old Android tzdata predates Kazakhstan's switch on 1 March 2024.
     * Respect updated IANA rules whenever the database knows that transition. */
    internal fun civil(zone: ZoneId, date: LocalDate): ZoneId {
        if (zone.id !in setOf("Asia/Almaty", "Asia/Qostanay") || date < LocalDate.of(2024, 3, 1)) return zone
        val expected = ZoneOffset.ofHours(5)
        return if (zone.rules.getOffset(Instant.parse("2024-07-01T00:00:00Z")) == expected) zone else expected
    }

    fun forCity(id: String?, fallback: ZoneId? = null): ZoneId =
        known(id)?.let { civil(it, LocalDate.now(it)) } ?: fallback ?: com.zikrtime.app.prayer.PrayerSchedule.zone()
}
