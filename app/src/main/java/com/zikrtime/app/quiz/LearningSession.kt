package com.zikrtime.app.quiz

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class LearningSession(
    val id: String,
    val scope: String,
    val category: String?,
    val questionIds: List<String>,
    val versions: Map<String, Int>,
    val stage: String,
    val cardIndex: Int = 0,
    val index: Int = 0,
    /** Index in the unshuffled bank, independent of language and displayed option order. */
    val selected: Int? = null,
    val correctIds: Set<String> = emptySet(),
    val rewardDate: String,
    val addedMinutes: Int = 0,
)

/** Storage codec is independent of Android and localized text. */
object LearningSessions {
    fun decode(o: JSONObject, scope: String): LearningSession? = runCatching {
        val ids=o.getJSONArray("ids").strings()
        require(ids.isNotEmpty() && ids.distinct().size==ids.size)
        val versions=o.optJSONObject("versions") ?: JSONObject()
        java.time.LocalDate.parse(o.getString("date"))
        val stage=o.optString("stage","ASK")
        require(stage in setOf("ASK","CARDS","QUIZ","RESULT"))
        val index=o.optInt("index").coerceIn(ids.indices)
        LearningSession(o.getString("id"),scope,o.optString("category").ifBlank { null },ids,
            ids.associateWith { versions.optInt(it,1) },stage,o.optInt("card").coerceIn(0,minOf(4,ids.lastIndex)),index,
            if(o.has("selected"))o.getInt("selected").takeIf { it in 0..3 } else null,
            o.optJSONArray("correct")?.strings()?.toSet()?.intersect(ids.toSet()) ?: emptySet(),
            o.getString("date"),o.optInt("added"))
    }.getOrNull()

    fun encode(s: LearningSession): JSONObject = JSONObject().put("id",s.id).put("category",s.category ?: "")
        .put("ids",JSONArray(s.questionIds)).put("versions",JSONObject(s.versions.filterKeys { it in s.questionIds }))
        .put("stage",s.stage).put("card",s.cardIndex).put("index",s.index)
        .put("correct",JSONArray(s.correctIds.toList())).put("date",s.rewardDate).put("added",s.addedMinutes)
        .apply { s.selected?.let { put("selected",it) } }

    fun validated(s: LearningSession, versions: Map<String, Int>): LearningSession? {
        // A changed meaning invalidates that question only; no results transfer by position.
        val valid=s.questionIds.filter { versions[it]==s.versions[it] }
        if(valid.isEmpty())return null
        val current=s.questionIds.getOrNull(s.index)
        val newIndex=s.questionIds.take(s.index).count { it in valid }
        if(s.stage=="QUIZ" && newIndex>=valid.size)return null
        val currentCard=s.questionIds.getOrNull(s.cardIndex)
        val newCard=valid.indexOf(currentCard).takeIf { it>=0 }
            ?: s.questionIds.take(s.cardIndex).count { it in valid }.coerceAtMost(valid.lastIndex)
        return s.copy(questionIds=valid,versions=s.versions.filterKeys { it in valid },
            index=newIndex.coerceAtMost(valid.lastIndex),cardIndex=newCard.coerceAtMost(4),
            selected=s.selected.takeIf { current in valid },correctIds=s.correctIds.intersect(valid.toSet()))
    }

    fun displayOrder(s: LearningSession, optionCount: Int): List<Int> =
        (0 until optionCount).shuffled(Random(s.id.hashCode() xor s.index))
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
