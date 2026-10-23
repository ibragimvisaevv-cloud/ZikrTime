package com.zikrtime.app

import com.zikrtime.app.prayer.AsrMadhab
import com.zikrtime.app.prayer.CalcMethod
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerCalculator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Сверка расчёта с независимой открытой библиотекой Adhan (batoulapps/adhan-java 1.2.1),
 * метод Всемирной исламской лиги, правило высоких широт «по углу».
 * Допуск — 2 минуты (разница в округлении и точности формул).
 */
class PrayerCalculatorTest {

    private data class Case(
        val city: String, val lat: Double, val lon: Double, val zone: String, val date: String,
        val madhab: AsrMadhab,
        val fajr: String, val sunrise: String, val dhuhr: String, val asr: String, val maghrib: String, val isha: String,
    )

    private val cases = listOf(
        Case("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-10-24", AsrMadhab.STANDARD, "05:14", "07:19", "12:14", "14:33", "17:08", "19:05"),
        Case("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-10-24", AsrMadhab.HANAFI, "05:14", "07:19", "12:14", "15:13", "17:08", "19:05"),
        Case("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-06-21", AsrMadhab.STANDARD, "01:49", "03:45", "12:31", "17:03", "21:18", "23:08"),
        Case("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-06-21", AsrMadhab.HANAFI, "01:49", "03:45", "12:31", "18:20", "21:18", "23:08"),
        Case("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-12-21", AsrMadhab.STANDARD, "06:36", "08:57", "12:28", "13:45", "15:58", "18:11"),
        Case("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-12-21", AsrMadhab.HANAFI, "06:36", "08:57", "12:28", "14:10", "15:58", "18:11"),
        Case("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-03-20", AsrMadhab.STANDARD, "04:25", "06:32", "12:37", "15:50", "18:43", "20:43"),
        Case("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-03-20", AsrMadhab.HANAFI, "04:25", "06:32", "12:37", "16:39", "18:43", "20:43"),
        Case("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-10-24", AsrMadhab.STANDARD, "04:46", "06:22", "11:41", "14:33", "17:00", "18:30"),
        Case("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-10-24", AsrMadhab.HANAFI, "04:46", "06:22", "11:41", "15:16", "17:00", "18:30"),
        Case("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-06-21", AsrMadhab.STANDARD, "01:56", "04:17", "11:59", "16:05", "19:41", "21:51"),
        Case("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-06-21", AsrMadhab.HANAFI, "01:56", "04:17", "11:59", "17:19", "19:41", "21:51"),
        Case("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-12-21", AsrMadhab.STANDARD, "05:43", "07:26", "11:55", "14:07", "16:24", "18:02"),
        Case("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-12-21", AsrMadhab.HANAFI, "05:43", "07:26", "11:55", "14:42", "16:24", "18:02"),
        Case("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-03-20", AsrMadhab.STANDARD, "04:25", "06:01", "12:05", "15:29", "18:09", "19:40"),
        Case("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-03-20", AsrMadhab.HANAFI, "04:25", "06:01", "12:05", "16:20", "18:09", "19:40"),
        Case("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-10-24", AsrMadhab.STANDARD, "05:05", "06:20", "12:05", "15:22", "17:49", "19:00"),
        Case("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-10-24", AsrMadhab.HANAFI, "05:05", "06:20", "12:05", "16:13", "17:49", "19:00"),
        Case("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-06-21", AsrMadhab.STANDARD, "04:14", "05:39", "12:22", "15:42", "19:06", "20:26"),
        Case("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-06-21", AsrMadhab.HANAFI, "04:14", "05:39", "12:22", "17:01", "19:06", "20:26"),
        Case("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-12-21", AsrMadhab.STANDARD, "05:34", "06:54", "12:19", "15:23", "17:44", "18:58"),
        Case("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-12-21", AsrMadhab.HANAFI, "05:34", "06:54", "12:19", "16:08", "17:44", "18:58"),
        Case("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-03-20", AsrMadhab.STANDARD, "05:11", "06:25", "12:28", "15:53", "18:32", "19:41"),
        Case("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-03-20", AsrMadhab.HANAFI, "05:11", "06:25", "12:28", "16:50", "18:32", "19:41"),
        Case("London", 51.5074, -0.1278, "Europe/London", "2026-10-24", AsrMadhab.STANDARD, "05:48", "07:40", "12:45", "15:17", "17:49", "19:34"),
        Case("London", 51.5074, -0.1278, "Europe/London", "2026-10-24", AsrMadhab.HANAFI, "05:48", "07:40", "12:45", "15:58", "17:49", "19:34"),
        Case("London", 51.5074, -0.1278, "Europe/London", "2026-06-21", AsrMadhab.STANDARD, "02:31", "04:43", "13:02", "17:25", "21:22", "23:27"),
        Case("London", 51.5074, -0.1278, "Europe/London", "2026-06-21", AsrMadhab.HANAFI, "02:31", "04:43", "13:02", "18:40", "21:22", "23:27"),
        Case("London", 51.5074, -0.1278, "Europe/London", "2026-12-21", AsrMadhab.STANDARD, "05:59", "08:04", "11:59", "13:38", "15:53", "17:51"),
        Case("London", 51.5074, -0.1278, "Europe/London", "2026-12-21", AsrMadhab.HANAFI, "05:59", "08:04", "11:59", "14:07", "15:53", "17:51"),
        Case("London", 51.5074, -0.1278, "Europe/London", "2026-03-20", AsrMadhab.STANDARD, "04:10", "06:03", "12:08", "15:27", "18:14", "20:01"),
        Case("London", 51.5074, -0.1278, "Europe/London", "2026-03-20", AsrMadhab.HANAFI, "04:10", "06:03", "12:08", "16:16", "18:14", "20:01"),
    )

    private fun minutesApart(a: LocalTime, b: LocalTime): Long {
        val d = Math.abs(Duration.between(a, b).toMinutes())
        return minOf(d, 24 * 60 - d)
    }

    @Test
    fun matchesReferenceLibrary() {
        for (c in cases) {
            val t = PrayerCalculator.calculate(LocalDate.parse(c.date), c.lat, c.lon, CalcMethod.MWL, c.madhab, ZoneId.of(c.zone))
            val expected = mapOf(
                Prayer.FAJR to c.fajr, Prayer.SUNRISE to c.sunrise, Prayer.DHUHR to c.dhuhr,
                Prayer.ASR to c.asr, Prayer.MAGHRIB to c.maghrib, Prayer.ISHA to c.isha,
            )
            for ((p, e) in expected) {
                val actual = t[p].toLocalTime()
                assertTrue("${c.city} ${c.date} ${p.name}: ожидалось $e, получилось $actual", minutesApart(LocalTime.parse(e), actual) <= 2)
            }
        }
    }

    @Test
    fun prayersAreInOrderAllYear() {
        val places = listOf(55.7558 to 37.6173, 43.3178 to 45.6949, 41.2995 to 69.2401, 38.5598 to 68.7870, 51.1694 to 71.4491, 21.3891 to 39.8579)
        for ((lat, lon) in places) for (doy in 1..365 step 5) for (m in CalcMethod.entries) for (mad in AsrMadhab.entries) {
            val date = LocalDate.ofYearDay(2026, doy)
            val t = PrayerCalculator.calculate(date, lat, lon, m, mad, ZoneId.of("UTC"))
            val seq = Prayer.entries.map { t[it] }
            assertTrue("порядок нарушен: $lat $lon $date $m", seq.zipWithNext().all { (a, b) -> a.isBefore(b) })
        }
    }

    @Test
    fun russiaMethodMatchesAladhan() {
        // aladhan.com, метод 14 (ДУМ РФ), Москва, 24.10.2026, ханафитский аср:
        // 05:29, 07:18, 12:14, 15:15, 17:08, 18:51
        val t = PrayerCalculator.calculate(LocalDate.of(2026, 10, 24), 55.7558, 37.6173, CalcMethod.RUSSIA, AsrMadhab.HANAFI, ZoneId.of("Europe/Moscow"))
        val expected = listOf("05:29", "07:18", "12:14", "15:15", "17:08", "18:51")
        Prayer.entries.forEachIndexed { i, p ->
            val actual = t[p].toLocalTime()
            assertTrue("${p.name}: $actual vs ${expected[i]}", minutesApart(LocalTime.parse(expected[i]), actual) <= 2)
        }
    }
}
