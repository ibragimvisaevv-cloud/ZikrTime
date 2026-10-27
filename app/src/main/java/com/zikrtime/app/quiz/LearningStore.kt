package com.zikrtime.app.quiz

import com.zikrtime.app.data.Store
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Additive v1 namespace inside the existing Store; legacy progress and settings are untouched. */
object LearningStore {
    private const val KEY = "learning_state_v1"
    private fun root(): JSONObject = runCatching { JSONObject(Store.getRaw(KEY) ?: "{}") }.getOrDefault(JSONObject())
    private fun save(root: JSONObject) { root.put("schema", 1); Store.putRaw(KEY, root.toString()) }
    private fun states(root: JSONObject): Map<String, LearningState> {
        val data = root.optJSONObject("questions") ?: return emptyMap()
        return data.keys().asSequence().mapNotNull { id -> data.optJSONObject(id)?.let { q ->
            id to LearningState(q.optInt("v",1), if(q.has("ok")) q.optBoolean("ok") else null,
                q.optLong("at"),q.optInt("stage"),q.optLong("due"),q.optLong("shown"),q.optString("event"))
        }}.toMap()
    }
    private fun writeState(root: JSONObject, id: String, s: LearningState) {
        val data = root.optJSONObject("questions") ?: JSONObject().also { root.put("questions",it) }
        val q=JSONObject().put("v",s.contentVersion).put("at",s.lastAnswerAt).put("stage",s.stage)
            .put("due",s.nextReviewAt).put("shown",s.explanationAt).put("event",s.lastEventId)
        s.lastCorrect?.let { q.put("ok",it) };data.put(id,q)
    }
    fun dueIds(bank: QuizBank, now: Long = System.currentTimeMillis()): List<String> =
        LearningPolicy.dueIds(states(root()),bank.questions.associate { it.id to it.contentVersion },now)
    fun dueCount(bank: QuizBank): Int = dueIds(bank).size
    fun historyCount(): Int = states(root()).size
    fun resetLearning() { save(JSONObject()) }

    fun session(scope: String): LearningSession? = root().optJSONObject("sessions")?.optJSONObject(scope)?.let {
        LearningSessions.decode(it, scope)
    }
    private fun writeSession(root: JSONObject, s: LearningSession) {
        val sessions=root.optJSONObject("sessions") ?: JSONObject().also { root.put("sessions",it) }
        sessions.put(s.scope,LearningSessions.encode(s));if(s.scope in setOf("normal","review")) root.put("lastScope",s.scope)
    }
    fun update(s: LearningSession) { val r=root();writeSession(r,s);save(r) }
    fun clearSession(scope: String) { val r=root();r.optJSONObject("sessions")?.remove(scope);save(r) }

    private fun validated(s: LearningSession, bank: QuizBank): LearningSession? =
        LearningSessions.validated(s, bank.questions.associate { it.id to it.contentVersion })
    fun pendingSession(bank: QuizBank): LearningSession? {
        val scopes=listOf(root().optString("lastScope","normal"),"normal","review").distinct().filter { it in setOf("normal","review") }
        return scopes.firstNotNullOfOrNull { scope -> session(scope)?.takeIf { it.stage!="RESULT" }?.let { validated(it,bank) } }
    }
    fun begin(bank: QuizBank, category: String?, scope: String = "normal", cards: Boolean = false): LearningSession? {
        session(scope)?.takeIf { it.stage!="RESULT" && it.category==category }?.let { old ->
            validated(old,bank)?.let { update(it);return it }
        }
        val ids=if(scope=="review")dueIds(bank) else QuestionBank.roundOrder(bank,category,Store.recentQuestions).map { it.id }
        if(ids.isEmpty()) { clearSession(scope);return null }
        val s=LearningSession(UUID.randomUUID().toString(),scope,category,ids,
            bank.questions.associate { it.id to it.contentVersion },
            if(scope=="normal") if(cards)"CARDS" else "ASK" else "QUIZ",rewardDate=LocalDate.now().toString())
        update(s);return s
    }
    fun showExplanation(q: Question, now: Long = System.currentTimeMillis()) {
        val r=root();writeState(r,q.id,LearningPolicy.explanation(states(r)[q.id],q.contentVersion,now));save(r)
    }
    fun answer(scope: String, q: Question, canonicalOption: Int, now: Long = System.currentTimeMillis()): LearningSession? {
        val s=session(scope) ?: return null
        if(s.selected!=null || s.stage!="QUIZ" || s.questionIds.getOrNull(s.index)!=q.id || canonicalOption !in q.options.indices)return s
        val ok=canonicalOption==q.answerIndex
        val event="${s.id}:${s.index}"
        val r=root();val previous=states(r)[q.id]
        var state=LearningPolicy.answer(previous,q.contentVersion,ok,now,event)
        // Feedback is now visible: immediate retry cannot advance the review interval.
        state=LearningPolicy.explanation(state,q.contentVersion,now)
        writeState(r,q.id,state)
        val next=s.copy(selected=canonicalOption,correctIds=if(ok)s.correctIds+q.id else s.correctIds)
        writeSession(r,next);save(r)
        Store.recordAnswer(q.id,q.category,ok)
        if(scope=="review")Store.recordReviewedQuestion(event,q.id)
        return next
    }
    fun completeCurrent(scope: String): Int {
        val s=session(scope) ?: return 0
        if(s.stage=="RESULT")return s.addedMinutes
        if(s.stage!="QUIZ" || s.selected==null)return 0
        val added=if(scope=="review")0 else Store.quizBonusForSession(s.id).takeIf { it>0 }
            ?: Store.awardQuizBonusOnce(s.id,s.correctIds,LocalDate.parse(s.rewardDate))
        update(s.copy(stage="RESULT",addedMinutes=added))
        Store.recordLearningCompleted(s.id,s.category,scope=="review")
        return added
    }
    fun displayOrder(s: LearningSession, q: Question): List<Int> = LearningSessions.displayOrder(s, q.options.size)
}
