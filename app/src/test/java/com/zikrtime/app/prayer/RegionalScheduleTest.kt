package com.zikrtime.app.prayer

import com.zikrtime.app.ui.CityZones
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class RegionalScheduleTest {
    private val assets = File("src/main/assets")
    private val november = LocalDate.of(2026, 11, 6)

    @Test fun publishedDatesMatchIndependentCityReferences() {
        val expected = mapOf(
            "grozny" to "05:23 06:35 12:30 14:43 16:46 18:15",
            "kazan" to "04:52 07:00 12:00 14:02 15:54 17:40",
            "ufa" to "06:38 08:10 13:30 15:39 17:31 19:14",
        )
        val seen = mutableSetOf<Pair<String, LocalDate>>()
        val matched = mutableSetOf<String>()
        val files = requireNotNull(File(assets, "prayer").listFiles { f -> f.extension == "tsv" })
        files.flatMap { it.readLines() }.forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val cells = line.split('\t')
            val city = cells[0]
            val date = LocalDate.parse(cells[1])
            val times = cells.drop(2).map(LocalTime::parse)
            assertNotNull("Unknown publisher for $city", RegionalFeeds.source(city))
            assertTrue("Duplicate $city $date", seen.add(city to date))
            assertTrue("Invalid $city $date", RegionalTimes.valid(times))
            if (date == november && city in expected) {
                assertEquals(expected[city], times.joinToString(" "))
                matched.add(city)
            }
        }
        assertEquals(expected.keys, matched)
    }

    @Test fun fallbackProducesSixOrderedInstantsInEveryCatalogCity() {
        val cities = JSONObject(File(assets, "cities.json").readText()).getJSONArray("cities")
        for (i in 0 until cities.length()) {
            val c = cities.getJSONObject(i)
            val id = c.getString("id")
            val zone = requireNotNull(CityZones.known(id)) { "Missing time zone for $id" }
            for (date in listOf(november, LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21))) {
                val times = PrayerCalculator.calculate(date, c.getDouble("lat"), c.getDouble("lon"),
                    CalcMethod.RUSSIA, AsrMadhab.STANDARD, CityZones.civil(zone, date))
                val ordered = Prayer.entries.map { times[it] }
                assertEquals("$id $date", date, times[Prayer.DHUHR].toLocalDate())
                assertTrue("$id $date", ordered.zipWithNext().all { (a, b) -> a.isBefore(b) })
            }
        }
    }

    @Test fun publishedWallTimeUsesCityZoneAndCarriesLateIshaIntoTomorrow() {
        val local = ZoneId.of("Asia/Yekaterinburg")
        val values = "02:22 04:45 13:30 18:45 22:50 00:20".split(" ").map(LocalTime::parse)
        val date = LocalDate.of(2026, 6, 21)
        val day = OnlineTimes.Day(local, Prayer.entries.zip(values).toMap())
        val utc = PrayerSchedule.fromOnline(date, day, ZoneOffset.UTC)
        assertEquals(date.atTime(8, 30).toInstant(ZoneOffset.UTC), utc[Prayer.DHUHR].toInstant())
        assertEquals(date.plusDays(1), utc[Prayer.ISHA].withZoneSameInstant(local).toLocalDate())
        assertTrue(RegionalTimes.valid(values))
        val precedingFajr = values.toMutableList().apply { this[0] = LocalTime.of(23, 54) }
        assertTrue(RegionalTimes.valid(precedingFajr))
        val midnight = PrayerSchedule.fromOnline(date, OnlineTimes.Day(local, Prayer.entries.zip(precedingFajr).toMap()), local)
        assertEquals(date.minusDays(1), midnight[Prayer.FAJR].toLocalDate())
        assertEquals(date.plusDays(1), midnight[Prayer.ISHA].toLocalDate())
        assertFalse(RegionalTimes.valid(values.toMutableList().apply { this[3] = LocalTime.NOON }))
        assertFalse(RegionalTimes.sameLocality(43.35, 46.10, 43.3178, 45.6949))
        assertTrue(RegionalTimes.sameLocality(43.32, 45.70, 43.3178, 45.6949))
    }

    @Test fun polarDatesAreExplicitEstimates() {
        for (date in listOf(LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21))) {
            val times = PrayerCalculator.calculate(date, 69.65, 18.95, CalcMethod.MWL, AsrMadhab.STANDARD,
                ZoneId.of("Europe/Oslo"))
            assertTrue(times.polarEstimate)
            assertTrue(Prayer.obligatory.zipWithNext().all { (a, b) -> times[a].isBefore(times[b]) })
        }
    }
}
