package com.zikrtime.app.quiz

import android.content.Context
import com.zikrtime.app.i18n.Lang
import org.json.JSONObject
import kotlin.random.Random

data class Question(
    val id: String,
    val category: String,
    val text: String,
    val arabic: String?,
    val options: List<String>,
    val answerIndex: Int,
    /** Короткая справка по теме — показывается после ответа. */
    val info: String,
) {
    val correctOption: String get() = options[answerIndex]

    /** Тот же вопрос с перемешанными вариантами ответа. */
    fun shuffled(random: Random = Random.Default): Question {
        val order = options.indices.shuffled(random)
        return copy(options = order.map { options[it] }, answerIndex = order.indexOf(answerIndex))
    }
}

class QuizBank(val categories: List<Pair<String, String>>, val questions: List<Question>) {
    fun categoryName(id: String): String = categories.firstOrNull { it.first == id }?.second ?: id
}

/**
 * Вопросы лежат в assets/quiz/<язык>.json (общие с приложением для iPhone).
 * Их можно дополнять без изменения кода — см. tools/quiz.
 */
object QuestionBank {

    private val cache = HashMap<String, QuizBank>()

    @Synchronized
    fun load(context: Context): QuizBank {
        val lang = Lang.content(context)
        cache[lang]?.let { return it }
        val assets = context.assets
        val name = listOf(lang, "en", "ru").first { runCatching { assets.open("quiz/$it.json").close() }.isSuccess }
        val json = assets.open("quiz/$name.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(json).also { cache[lang] = it }
    }

    fun parse(json: String): QuizBank {
        val root = JSONObject(json)
        val cats = root.getJSONArray("categories").let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { c -> c.getString("id") to c.getString("name") } }
        }
        val qs = root.getJSONArray("questions")
        val questions = (0 until qs.length()).map { i ->
            val o = qs.getJSONObject(i)
            val opts = o.getJSONArray("options")
            Question(
                id = o.getString("id"),
                category = o.getString("cat"),
                text = o.getString("q"),
                arabic = o.optString("arabic").takeIf { it.isNotBlank() },
                options = (0 until opts.length()).map { opts.getString(it) },
                answerIndex = o.optInt("answer", 0),
                info = o.optString("info"),
            )
        }
        return QuizBank(cats, questions)
    }

    /** Порядок вопросов для раунда: сначала те, что давно не встречались. */
    fun roundOrder(bank: QuizBank, category: String?, recent: List<String>, random: Random = Random.Default): List<Question> {
        val pool = bank.questions.filter { category == null || it.category == category }.ifEmpty { bank.questions }
        val recentSet = recent.toSet()
        val fresh = pool.filter { it.id !in recentSet }.shuffled(random)
        val seen = pool.filter { it.id in recentSet }.shuffled(random)
        return fresh + seen
    }
}

fun String.hasArabic(): Boolean = any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' || it in 'ﭐ'..'﻿' }
