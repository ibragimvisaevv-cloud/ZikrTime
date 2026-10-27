package com.zikrtime.app.quiz

import org.junit.Assert.*
import org.junit.Test

class LearningPolicyTest {
    private val start = 1_800_000_000_000L
    @Test fun successfulReviewsFollowIntervalsAndErrorResets() {
        var state: LearningState? = null
        var now = start
        listOf(1L,3L,7L,14L,30L,30L).forEachIndexed { i, days ->
            state = LearningPolicy.answer(state,1,true,now,"e$i")
            assertEquals(now+days*LearningPolicy.DAY,state!!.nextReviewAt)
            now=state!!.nextReviewAt
        }
        val reset=LearningPolicy.answer(state,1,false,now,"wrong")
        assertEquals(0,reset.stage);assertEquals(now+LearningPolicy.DAY,reset.nextReviewAt)
    }
    @Test fun explanationAndSameDayRetryCannotAdvanceLearning() {
        val shown=LearningPolicy.explanation(null,1,start)
        val first=LearningPolicy.answer(shown,1,true,start+1000,"1")
        assertEquals(0,first.stage)
        val repeated=LearningPolicy.answer(first,1,true,start+2000,"2")
        assertEquals(0,repeated.stage)
        assertEquals(first.nextReviewAt,repeated.nextReviewAt)
        assertEquals(repeated,LearningPolicy.answer(repeated,1,false,start+3000,"2"))
        assertEquals(1,LearningPolicy.answer(repeated,1,true,repeated.nextReviewAt,"3").stage)
    }
    @Test fun clockRollbackAndVersionChangeAreSafe() {
        val first=LearningPolicy.answer(null,1,true,start,"1")
        val rollback=LearningPolicy.answer(first,1,true,start-LearningPolicy.DAY,"2")
        assertEquals(first.nextReviewAt,rollback.nextReviewAt);assertEquals(1,rollback.stage)
        val revised=LearningPolicy.answer(first,2,false,start+100,"new")
        assertEquals(2,revised.contentVersion);assertEquals(0,revised.stage)
    }
    @Test fun queueExcludesRemovedRevisedFutureAndLimitsToFive() {
        val states=(1..8).associate { "q$it" to LearningState(1,false,start,0,start+it,0,"$it") }
        val versions=states.keys.associateWith { if(it=="q1") 2 else 1 }-"q2"
        assertEquals(listOf("q3","q4","q5","q6","q7"),LearningPolicy.dueIds(states,versions,start+10))
        assertTrue(LearningPolicy.dueIds(states,versions,start-1).isEmpty())
    }
}
