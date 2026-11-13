package com.zikrtime.app.quiz

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class KnowledgeLevelTest {
    private fun question(id: String, level: Int, category: String = "quran", answer: Int = 2) =
        Question(id,category,"Question $id",null,listOf("A","B","C","D"),answer,"Explanation",difficulty=level)
    private val bank = QuizBank(listOf("quran" to "Quran","fiqh" to "Fiqh"),listOf(
        question("easy",1),question("middle",2),question("advanced",3),
        question("advanced-other",3,"fiqh"),question("advanced-seen",3)))
    private fun session(level: Int? = 3, ids: List<String> = listOf("advanced","advanced-seen")) =
        LearningSession("stable-round","normal","quran",ids,ids.associateWith { 1 },"QUIZ",
            selected=2,correctIds=setOf(ids.first()),rewardDate="2026-11-02",level=level)

    @Test fun everyRoundStaysInsideSelectedLevelAndCategoryIncludingSeenQuestions() {
        val ordered = QuestionBank.roundOrder(bank,"quran",listOf("advanced-seen"),Random(8),level=3)
        assertEquals(listOf("advanced","advanced-seen"),ordered.map { it.id })
        assertTrue(ordered.all { it.difficulty==3 && it.category=="quran" })
        assertEquals(setOf("advanced","advanced-other","advanced-seen"),
            QuestionBank.roundOrder(bank,null,emptyList(),Random(2),level=3).map { it.id }.toSet())
        assertEquals(listOf("middle"),QuestionBank.roundOrder(bank,null,emptyList(),level=2).map { it.id })
    }

    @Test fun emptyCategoryOrLevelNeverFallsBackToEasyOrOtherTopics() {
        assertTrue(QuestionBank.roundOrder(bank,"fiqh",emptyList(),level=2).isEmpty())
        assertTrue(QuestionBank.roundOrder(bank,"missing",emptyList(),level=3).isEmpty())
        assertTrue(QuestionBank.roundOrder(bank,null,emptyList(),level=99).isEmpty())
    }

    @Test fun legacyAndDifferentLevelSessionsRequireFreshRoundEvenWhenIdsStillExist() {
        val legacy = LearningSessions.decode(LearningSessions.encode(session(null)),"normal")!!
        assertNull(legacy.level)
        assertNull(LearningSessions.validated(legacy,bank,KnowledgeLevel.ADVANCED))
        assertNull(LearningSessions.validated(session(1),bank,KnowledgeLevel.ADVANCED))
        assertNull(LearningSessions.validated(session(),bank,KnowledgeLevel.INTERMEDIATE))
        assertNull(LearningSessions.validated(session(),bank,KnowledgeLevel.ADVANCED,"fiqh"))
    }

    @Test fun restoredSessionCannotRetainCrossLevelAnswersOrOtherCategoryQuestions() {
        val mixed = session(ids=listOf("easy","advanced-other","advanced")).copy(correctIds=setOf("easy","advanced-other"))
        val restored = LearningSessions.validated(mixed,bank,KnowledgeLevel.ADVANCED)!!
        assertEquals(listOf("advanced"),restored.questionIds)
        assertTrue(restored.correctIds.isEmpty())
        assertNull(restored.selected)
        assertEquals(0,restored.index)
    }

    @Test fun savedLevelAndCanonicalAnswerSurviveRestartLocaleAndOptionPermutation() {
        val saved = session()
        val restored = LearningSessions.decode(JSONObject(LearningSessions.encode(saved).toString()),"normal")!!
        assertEquals(saved,LearningSessions.validated(restored,bank,KnowledgeLevel.ADVANCED))
        val order = LearningSessions.displayOrder(restored,4)
        val displayedSelection = order.indexOf(requireNotNull(restored.selected))
        assertEquals(2,order[displayedSelection])
        assertEquals(order,LearningSessions.displayOrder(saved,4))
        val translated = QuizBank(bank.categories,bank.questions.map { it.copy(text="Translated",options=listOf("А","Б","В","Г")) })
        assertEquals(saved,LearningSessions.validated(restored,translated,KnowledgeLevel.ADVANCED))
        val shuffled = bank.questions.first { it.id=="advanced" }.shuffled(Random(22))
        assertEquals("C",shuffled.options[shuffled.answerIndex])
        assertEquals(3,shuffled.difficulty)
    }

    @Test fun reviewQueueFiltersBeforeItsLimitAndRetainsOtherLevelHistory() {
        val questions = (1..6).map { question("easy-$it",1) } + question("hard",3)
        val b = QuizBank(bank.categories,questions)
        val history = questions.associate { it.id to LearningState(1,lastCorrect=false,lastAnswerAt=1,nextReviewAt=2) }
        val eligible = b.questionsFor(null,3).associate { it.id to it.contentVersion }
        assertEquals(listOf("hard"),LearningPolicy.dueIds(history,eligible,100))
        assertEquals(7,history.size)
    }

    @Test fun oldBankDefaultsToBeginnerAndMalformedDifficultyIsRejected() {
        fun json(difficulty: String = "") = """{"categories":[],"questions":[{"id":"a","cat":"quran","q":"Q","options":["A","B","C","D"],"answer":2$difficulty}]}"""
        assertEquals(1,QuestionBank.parse(json()).questions.single().difficulty)
        assertEquals(3,QuestionBank.parse(json(",\"difficulty\":3")).questions.single().difficulty)
        assertTrue(runCatching { QuestionBank.parse(json(",\"difficulty\":4")) }.isFailure)
        assertNull(KnowledgeLevel.fromId(0))
        assertNull(KnowledgeLevel.fromId(null))
        assertNull(LearningSessions.decode(LearningSessions.encode(session()).put("level",9),"normal"))
    }
}
