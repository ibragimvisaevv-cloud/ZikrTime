package com.zikrtime.app.quiz

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LearningSessionTest {
    private fun sample()=LearningSession("round-fixed","normal","quran",listOf("a","b","c"),
        mapOf("a" to 1,"b" to 2,"c" to 1),"QUIZ",cardIndex=2,index=1,selected=2,
        correctIds=setOf("a"),rewardDate="2026-10-27")

    @Test fun processRestartRestoresActualQuestionAnswerAndPermutation() {
        val s=sample()
        val restored=LearningSessions.decode(JSONObject(LearningSessions.encode(s).toString()),"normal")!!
        assertEquals(s,restored)
        assertEquals("b",restored.questionIds[restored.index])
        assertEquals(2,restored.selected)
        assertEquals(LearningSessions.displayOrder(s,4),LearningSessions.displayOrder(restored,4))
        assertEquals(listOf(0,1,2,3),LearningSessions.displayOrder(restored,4).sorted())
    }
    @Test fun studyMaterialAndCompletionAreNotResetByRecreation() {
        val cards=sample().copy(stage="CARDS",cardIndex=2,selected=null)
        assertEquals(cards,LearningSessions.decode(LearningSessions.encode(cards),"normal"))
        val done=sample().copy(stage="RESULT",addedMinutes=10)
        assertEquals(done,LearningSessions.decode(LearningSessions.encode(done),"normal"))
    }
    @Test fun revisedQuestionCannotInheritAnswerAndRemovedIdsAreSkipped() {
        val changed=LearningSessions.validated(sample(),mapOf("b" to 3,"c" to 1))!!
        assertEquals(listOf("c"),changed.questionIds)
        assertEquals(0,changed.index)
        assertNull(changed.selected)
        assertTrue(changed.correctIds.isEmpty())
        assertNull(LearningSessions.validated(sample(),emptyMap()))
    }
    @Test fun localeOnlyChangePreservesIdsAndAnsweredState() {
        val s=sample()
        assertEquals(s,LearningSessions.validated(s,mapOf("a" to 1,"b" to 2,"c" to 1)))
        assertNull(LearningSessions.decode(JSONObject("{}"),"normal"))
    }
}
