package com.zikrtime.app.data

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianRegionalCitiesTest {
    private fun entries(): List<Cities.Entry> {
        val path = listOf(File("src/main/assets/cities_ru_regions.tsv"), File("app/src/main/assets/cities_ru_regions.tsv"))
            .first { it.isFile }
        return path.bufferedReader().useLines(Cities::parseRegions)
    }

    @Test fun allRegionalCentresHaveValidCoordinatesZoneAndNames() {
        val cities = entries()
        assertEquals(66, cities.size)
        assertEquals(cities.size, cities.map { it.id }.distinct().size)
        for (city in cities) {
            assertTrue(city.id, city.lat in -90.0..90.0 && city.lon in -180.0..180.0)
            assertTrue(city.id, city.zone != null && city.names.getValue("ru").isNotBlank() && city.names.getValue("en").isNotBlank())
            assertEquals(city.id, city.toCity("ru").id)
        }
        val names = cities.associate { it.id to it.names.getValue("ru") }
        assertEquals("Благовещенск", names["blagoveshchensk"])
        assertTrue("blagoveshchensk_rb" !in names)
    }

    @Test fun cityTimeSpansAllTheWayFromKaliningradToChukotka() {
        val zones = entries().associate { it.id to it.zone!! }
        val instant = Instant.parse("2026-11-06T14:00:00Z")
        val offsets = mapOf("kaliningrad" to 2, "kirov" to 3, "saratov" to 4, "tyumen" to 5,
            "omsk" to 6, "tomsk" to 7, "irkutsk" to 8, "yakutsk" to 9, "vladivostok" to 10,
            "magadan" to 11, "petropavlovsk_kamchatsky" to 12, "anadyr" to 12)
        for ((city, hours) in offsets) {
            assertEquals(city, ZoneOffset.ofHours(hours), zones.getValue(city).rules.getOffset(instant))
        }
        assertEquals(LocalDate.of(2026, 11, 6), instant.atZone(zones.getValue("kaliningrad")).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 7), instant.atZone(zones.getValue("anadyr")).toLocalDate())
    }
}
