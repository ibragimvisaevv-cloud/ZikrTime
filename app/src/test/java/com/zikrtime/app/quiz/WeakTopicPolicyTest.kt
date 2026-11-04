package com.zikrtime.app.quiz

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WeakTopicPolicyTest {
    private val now = 1_800_000_000_000L
    private fun q(id: String, category: String = "quran", level: Int = 2, version: Int = 1) =
        Question(id,category,"Question $id",null,listOf("A","B","C","D"),0,"Explanation",
            contentVersion=version,difficulty=level)
    private fun bank(vararg questions: Question) =
        QuizBank(listOf("quran" to "Quran","fiqh" to "Fiqh","prophets" to "Prophets"),questions.toList())
    private fun state(ok: Boolean = true, stage: Int = 0, at: Long = now - 100,
        due: Long = now + LearningPolicy.DAY, version: Int = 1) =
        LearningState(version,ok,at,stage,due)
    private fun session(id: String = "practice", category: String? = "quran", scope: String = "training") =
        LearningSession(id,scope,category,listOf("a","b"),mapOf("a" to 1,"b" to 1),"QUIZ",
            rewardDate="2026-11-04",level=2)

    @Test fun unseenAndExplanationOnlyMaterialNeverBecomeMistakesOrTraining() {
        val b=bank(q("a"),q("b"),q("c","fiqh"))
        val states=mapOf("a" to LearningPolicy.explanation(null,1,now))
        val topics=WeakTopicPolicy.topics(b,states,2,now)
        assertEquals(2,topics.size)
        assertTrue(topics.all { it.answered==0 && it.mistakes==0 && it.status==TopicLearningStatus.NEW })
        assertTrue(WeakTopicPolicy.trainingIds(b,states,2,now=now).isEmpty())
        assertTrue(WeakTopicPolicy.topics(b,states,3,now).isEmpty())
    }

    @Test fun removedRevisedAndOtherLevelHistoryDoesNotLeakIntoCoverage() {
        val b=bank(q("a",version=2),q("b"),q("easy",level=1))
        val states=mapOf("a" to state(false),"b" to state(true,2),
            "easy" to state(false),"removed" to state(false))
        val topic=WeakTopicPolicy.topics(b,states,2,now).single()
        assertEquals(2,topic.total);assertEquals(1,topic.answered)
        assertEquals(0,topic.mistakes);assertEquals(.5f,topic.coverage,0f)
        assertEquals(listOf("b"),WeakTopicPolicy.trainingIds(b,states,2,now=now))
        assertEquals(4,states.size)
    }

    @Test fun strictLevelAndCategoryFilterRunsBeforeFiveQuestionLimit() {
        val questions=(1..8).map { q("easy-$it",level=1) }+
            (1..8).map { q("fiqh-$it","fiqh") }+q("only")
        val b=bank(*questions.toTypedArray())
        val history=questions.associate { it.id to state(false) }
        assertEquals(listOf("only"),WeakTopicPolicy.trainingIds(b,history,2,"quran",now))
        assertTrue(WeakTopicPolicy.trainingIds(b,history,2,"missing",now).isEmpty())
        assertTrue(WeakTopicPolicy.trainingIds(b,history,99,now=now).isEmpty())
        assertEquals(17,history.size)
    }

    @Test fun errorsThenFragileThenDueThenRetainedAndOnlyFiveUniqueQuestions() {
        val b=bank(q("wrong-new"),q("wrong-old"),q("fragile"),q("due"),q("retained"),q("sixth"))
        val history=mapOf(
            "wrong-new" to state(false,at=now-10),
            "wrong-old" to state(false,at=now-20),
            "fragile" to state(true,1,at=now-30),
            "due" to state(true,3,at=now-40,due=now-1),
            "retained" to state(true,3,at=now-50),
            "sixth" to state(true,3,at=now-5))
        val ids=WeakTopicPolicy.trainingIds(b,history,2,now=now)
        assertEquals(listOf("wrong-old","wrong-new","fragile","due","retained"),ids)
        assertEquals(ids.size,ids.distinct().size)
    }

    @Test fun smallSampleIsExplicitAndUnseenQuestionsDoNotLowerRetention() {
        val b=bank(*(1..10).map { q("q$it") }.toTypedArray())
        val few=(1..4).associate { "q$it" to state(true,3) }
        assertEquals(TopicLearningStatus.LIMITED,WeakTopicPolicy.topics(b,few,2,now).single().status)
        val enough=few+("q5" to state(true,3))
        val profile=WeakTopicPolicy.topics(b,enough,2,now).single()
        assertEquals(TopicLearningStatus.RETAINED,profile.status)
        assertEquals(5,profile.retained);assertEquals(.5f,profile.coverage,0f)
        assertEquals(TopicLearningStatus.PRACTICE,
            WeakTopicPolicy.topics(b,enough+("q5" to state(false)),2,now).single().status)
    }

    @Test fun immediateCorrectionDoesNotClaimLastingRetention() {
        val b=bank(q("a"))
        val missed=LearningPolicy.explanation(LearningPolicy.answer(null,1,false,now,"wrong"),1,now)
        val corrected=LearningPolicy.answer(missed,1,true,now+1000,"retry")
        val topic=WeakTopicPolicy.topics(b,mapOf("a" to corrected),2,now+1000).single()
        assertEquals(0,topic.mistakes);assertEquals(1,topic.consolidating)
        assertEquals(0,topic.retained);assertEquals(TopicLearningStatus.BUILDING,topic.status)
        assertEquals(listOf("a"),WeakTopicPolicy.trainingIds(b,mapOf("a" to corrected),2,now=now+1000))
    }

    @Test fun clockRollbackDoesNotMakeFutureReviewsDueOrDiscardKnownHistory() {
        val b=bank(q("a"))
        val h=mapOf("a" to state(true,3,at=now+1000,due=now+2000))
        val topic=WeakTopicPolicy.topics(b,h,2,now).single()
        assertEquals(1,topic.answered);assertEquals(0,topic.due)
        assertEquals(listOf("a"),WeakTopicPolicy.trainingIds(b,h,2,now=now))
    }

    @Test fun weakTopicsLeadDashboardButEmptyTopicsKeepTheirOwnStatus() {
        val b=bank(q("quran"),q("fiqh","fiqh"),q("prophet","prophets"))
        val h=mapOf("quran" to state(true,3),"fiqh" to state(false))
        val topics=WeakTopicPolicy.topics(b,h,2,now)
        assertEquals(listOf("fiqh","quran","prophets"),topics.map { it.category })
        assertEquals(TopicLearningStatus.NEW,topics.last().status)
    }

    @Test fun trainingSessionKeepsOrderAndAnswerThroughRestartAndLanguageChange() {
        val b=bank(q("a"),q("b"),q("easy",level=1))
        val original=session().copy(selected=2,correctIds=setOf("b"))
        val restored=LearningSessions.decode(JSONObject(LearningSessions.encode(original).toString()),"training")!!
        assertEquals(original,LearningSessions.validated(restored,b,KnowledgeLevel.INTERMEDIATE))
        assertEquals(LearningSessions.displayOrder(original,4),LearningSessions.displayOrder(restored,4))
        assertNull(LearningSessions.validated(restored,b,KnowledgeLevel.BEGINNER))
        assertNull(LearningSessions.validated(restored,b,KnowledgeLevel.INTERMEDIATE,"fiqh"))
    }

    @Test fun staleCallbacksCannotResurrectOrReplaceAnotherRound() {
        val old=session()
        val target=old.copy(index=1)
        assertFalse(LearningSessions.acceptsUpdate(null,target,KnowledgeLevel.INTERMEDIATE))
        assertFalse(LearningSessions.acceptsUpdate(session(id="new"),target,KnowledgeLevel.INTERMEDIATE))
        assertFalse(LearningSessions.acceptsUpdate(session(category="fiqh"),target,KnowledgeLevel.INTERMEDIATE))
        assertFalse(LearningSessions.acceptsUpdate(old,target,KnowledgeLevel.ADVANCED))
        assertTrue(LearningSessions.acceptsUpdate(old,target,KnowledgeLevel.INTERMEDIATE,expected=old))
    }

    @Test fun anOldRenderedStepCannotEraseANewerAnswer() {
        val rendered=session()
        val answered=rendered.copy(selected=0,correctIds=setOf("a"))
        val staleNext=rendered.copy(index=1,selected=null)
        assertFalse(LearningSessions.acceptsUpdate(answered,staleNext,KnowledgeLevel.INTERMEDIATE,expected=rendered))
        assertTrue(LearningSessions.acceptsUpdate(answered,answered.copy(index=1,selected=null),
            KnowledgeLevel.INTERMEDIATE,expected=answered))
    }

    @Test fun nonRewardSessionReceiptNeverRestoresBonusMinutes() {
        val receipt=LearningSessions.encode(session().copy(stage="RESULT",addedMinutes=10))
        assertEquals(0,LearningSessions.decode(receipt,"training")!!.addedMinutes)
        assertEquals(0,LearningSessions.decode(receipt,"review")!!.addedMinutes)
        assertEquals(10,LearningSessions.decode(receipt,"normal")!!.addedMinutes)
    }
}
