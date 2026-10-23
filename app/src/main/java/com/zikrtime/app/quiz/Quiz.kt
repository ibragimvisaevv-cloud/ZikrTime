package com.zikrtime.app.quiz

import android.content.Context
import org.json.JSONArray
import kotlin.random.Random

data class Question(
    val category: String,
    val text: String,
    val arabic: String?,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
) {
    val correctOption: String get() = options[answerIndex]

    /** Тот же вопрос с перемешанными вариантами ответа. */
    fun shuffled(random: Random = Random.Default): Question {
        val order = options.indices.shuffled(random)
        return copy(options = order.map { options[it] }, answerIndex = order.indexOf(answerIndex))
    }
}

/** Вопросы лежат в assets/questions.json — их можно дополнять без изменения кода. */
object QuestionBank {

    @Volatile
    private var cached: List<Question>? = null

    fun all(context: Context): List<Question> {
        cached?.let { return it }
        val json = context.assets.open("questions.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed = parse(json)
        cached = parsed
        return parsed
    }

    fun parse(json: String): List<Question> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val opts = o.getJSONArray("options")
            Question(
                category = o.getString("category"),
                text = o.getString("question"),
                arabic = o.optString("arabic").takeIf { it.isNotBlank() },
                options = (0 until opts.length()).map { opts.getString(it) },
                answerIndex = o.getInt("answer"),
                explanation = o.optString("explanation"),
            )
        }
    }
}

fun String.hasArabic(): Boolean = any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' || it in 'ﭐ'..'﻿' }
