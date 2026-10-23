package com.zikrtime.app

import com.zikrtime.app.prayer.Qibla
import com.zikrtime.app.quiz.QuestionBank
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Проверка содержимого: вопросы на всех языках, список городов, расчёт киблы. */
class ContentTest {

    private val assets = File("src/main/assets")

    @Test
    fun quizFilesAreConsistent() {
        val files = File(assets, "quiz").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertTrue("нет файлов с вопросами", files.isNotEmpty())
        val master = QuestionBank.parse(File(assets, "quiz/ru.json").readText())
        for (f in files) {
            val bank = QuestionBank.parse(f.readText())
            assertEquals("${f.name}: число вопросов", master.questions.size, bank.questions.size)
            bank.questions.zip(master.questions).forEach { (q, m) ->
                assertEquals("${f.name}: id", m.id, q.id)
                assertEquals("${f.name}/${q.id}: тема", m.category, q.category)
                assertEquals("${f.name}/${q.id}: арабский текст", m.arabic, q.arabic)
                assertEquals("${f.name}/${q.id}: 4 варианта", 4, q.options.toSet().size)
                assertTrue("${f.name}/${q.id}: нет справки", q.info.length >= 20)
                // Перемешивание не теряет правильный ответ
                val s = q.shuffled()
                assertEquals(q.correctOption, s.correctOption)
            }
            assertEquals("${f.name}: темы", master.categories.map { it.first }, bank.categories.map { it.first })
        }
    }

    @Test
    fun citiesHaveAllLanguages() {
        val root = JSONObject(File(assets, "cities.json").readText())
        val langs = listOf("ru", "en", "uz", "tg", "kk", "ky", "az", "tr")
        val cities = root.getJSONArray("cities")
        for (i in 0 until cities.length()) {
            val c = cities.getJSONObject(i)
            val names = c.getJSONObject("names")
            langs.forEach { assertTrue("${c.getString("id")}: нет названия $it", names.optString(it).isNotBlank()) }
            assertTrue(c.getDouble("lat") in -90.0..90.0 && c.getDouble("lon") in -180.0..180.0)
        }
    }

    @Test
    fun qiblaBearing() {
        // Известные значения: Нью-Йорк ≈ 58.5°, Лондон ≈ 119°
        assertEquals(58.5, Qibla.bearing(40.7128, -74.0060), 0.5)
        assertEquals(119.0, Qibla.bearing(51.5074, -0.1278), 0.5)
        // Из Мекки расстояние почти нулевое
        assertTrue(Qibla.distanceKm(21.4225, 39.8262) < 1.0)
        // Москва — Мекка около 3 820 км, кибла ≈ 176°
        assertEquals(3822.0, Qibla.distanceKm(55.7558, 37.6173), 30.0)
        assertEquals(176.4, Qibla.bearing(55.7558, 37.6173), 0.5)
    }
}
