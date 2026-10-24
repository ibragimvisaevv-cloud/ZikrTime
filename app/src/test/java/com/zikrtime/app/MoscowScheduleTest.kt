package com.zikrtime.app

import com.zikrtime.app.prayer.AsrMadhab
import com.zikrtime.app.prayer.CalcMethod
import com.zikrtime.app.prayer.DumrfLive
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Расписание Московской Соборной мечети (ДУМ РФ). */
class MoscowScheduleTest {

    /** Официальное время на dumrf.ru 24.10.2026: 05:15, 07:14, 12:19, 14:34, 17:12, 18:50. */
    @Test
    fun formulaMatchesOfficialTimetable() {
        val t = PrayerCalculator.calculate(
            LocalDate.of(2026, 10, 24), 55.7558, 37.6173, CalcMethod.MOSCOW_CATHEDRAL, AsrMadhab.STANDARD, ZoneId.of("Europe/Moscow"),
        )
        val expected = mapOf(
            Prayer.FAJR to "05:15", Prayer.SUNRISE to "07:14", Prayer.DHUHR to "12:19",
            Prayer.ASR to "14:34", Prayer.MAGHRIB to "17:12", Prayer.ISHA to "18:50",
        )
        for ((p, time) in expected) assertEquals(p.name, LocalTime.parse(time), t[p].toLocalTime())
    }

    @Test
    fun parsesDumrfHomePage() {
        val html = """
            <div class="namaz"><h3>Время намазов на сегодня 13 Джумада-ль-Уля (Москва):</h3>
            <ul><li><span>Фаджр</span> <b>05:15</b></li><li><span>Шурук</span> <b>07:14</b></li>
            <li><span>Зухр</span> <b>12:19</b></li><li><span>Аср</span> <b>14:34</b></li>
            <li><span>Магриб</span>&nbsp;<b>17:12</b></li><li><span>Иша</span> <b>18:50</b></li></ul></div>
        """.trimIndent()
        val parsed = DumrfLive.parse(html)
        assertNotNull(parsed)
        assertEquals(LocalTime.of(5, 15), parsed!![Prayer.FAJR])
        assertEquals(LocalTime.of(7, 14), parsed[Prayer.SUNRISE])
        assertEquals(LocalTime.of(18, 50), parsed[Prayer.ISHA])
    }

    @Test
    fun rejectsPageWithoutSchedule() {
        assertNull(DumrfLive.parse("<html><body>Новости</body></html>"))
        assertNull(DumrfLive.parse("Время намазов: Фаджр 18:00 Шурук 07:14 Зухр 12:19 Аср 14:34 Магриб 17:12 Иша 18:50"))
    }
}
