package com.zikrtime.app.prayer

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.YearMonth

/** A release-maintainer tool. Ordinary unit test runs make no requests. */
class RegionalCalendarExportTest {
    @Test fun exportPublishedApiRows() {
        val output = System.getenv("ZIKR_PRAYER_EXPORT_FILE")
        assumeTrue(!output.isNullOrBlank())
        val year = requireNotNull(System.getenv("ZIKR_PRAYER_EXPORT_YEAR")).toInt()
        val selected = System.getenv("ZIKR_PRAYER_EXPORT_CITIES")?.split(',')?.map { it.trim() }?.toSet()
            ?: RegionalFeeds.apiCityIds
        require(selected.isNotEmpty() && RegionalFeeds.apiCityIds.containsAll(selected))
        val lines = mutableListOf<String>()
        for (city in selected.sorted()) {
            val source = requireNotNull(RegionalFeeds.source(city))
            for (month in 1..12) {
                val dates = RegionalFeeds.download(source, YearMonth.of(year, month)).toSortedMap()
                for ((date, times) in dates) {
                    lines += "$city\t$date\t" + times.joinToString("\t")
                }
            }
        }
        require(lines.isNotEmpty()) { "No published dates found for $year" }
        val path = Paths.get(requireNotNull(output)).toAbsolutePath()
        Files.createDirectories(path.parent)
        val pending = Files.createTempFile(path.parent, "prayer-times-", ".tsv")
        try {
            Files.write(pending, (lines.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8))
            Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(pending)
        }
    }
}
