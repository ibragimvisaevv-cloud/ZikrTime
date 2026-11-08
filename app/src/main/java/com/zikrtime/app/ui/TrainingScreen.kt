@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.quiz.LearningStore
import com.zikrtime.app.quiz.QuestionBank
import com.zikrtime.app.quiz.QuizBank
import com.zikrtime.app.quiz.TopicLearning
import com.zikrtime.app.quiz.TopicLearningStatus

/** A compact entrance; the full topic breakdown has its own screen. */
@Composable
fun WeakTopicsOverview(bank: QuizBank) {
    val nav = LocalNav.current
    val topics = LearningStore.topics(bank)
    val answered = topics.sumOf { it.answered }
    val mistakes = topics.sumOf { it.mistakes }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.training_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(if(answered == 0) R.string.training_first_hint else R.string.training_overview_hint),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(answered > 0) Text(stringResource(R.string.training_overview_counts, answered, mistakes),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        TonalButton(stringResource(R.string.training_open), onClick = { nav.openTraining() },
            modifier = Modifier.testTag("training_overview_open").semantics { testTagsAsResourceId = true })
    }
}

@Composable
fun TrainingScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val bank = remember { QuestionBank.load(context) }
    val stored = LearningStore.session("training")
    var readingScale by rememberReadingScale()
    val back: () -> Unit = { if(nav.trainingActive) nav.trainingActive = false else nav.back(); Unit }
    BackHandler(onBack = back)
    ScreenScaffold(title = stringResource(R.string.training_title), onBack = back,
        scrollKey = "${LearningStore.selectedLevel?.id}-${nav.trainingActive}-${stored?.id}-${stored?.stage}-${stored?.index}",
        actions = { if(nav.trainingActive) ReadingTools(readingScale) { readingScale = it } }) {
        KnowledgeLevelGate(bank) { level ->
            KnowledgeLevelControl(bank)
            androidx.compose.runtime.key(level.id, nav.quizRound, nav.trainingActive) {
                if(nav.trainingActive) TrainingFlow(bank, nav.trainingCategory)
                else TrainingDashboard(bank)
            }
        }
    }
}

private data class TrainingRequest(val category: String?)

@Composable
private fun TrainingDashboard(bank: QuizBank) {
    val nav = LocalNav.current
    val topics = LearningStore.topics(bank)
    val answered = topics.sumOf { it.answered }
    val stored = LearningStore.session("training")
    val pending = stored?.let { LearningStore.activeSession(bank,"training",it.category) }
        ?.takeIf { it.stage != "RESULT" }
    var replacement by remember { mutableStateOf<TrainingRequest?>(null) }
    val start: (String?) -> Unit = { category ->
        if(pending != null && pending.category != category) replacement = TrainingRequest(category)
        else nav.startTraining(category)
    }
    Column(Modifier.fillMaxWidth().testTag("training_dashboard").semantics { testTagsAsResourceId = true },
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.training_personal_plan), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.training_method), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            pending?.let {
                PrimaryButton(stringResource(R.string.learning_resume),
                    onClick = { nav.startTraining(it.category) }, modifier = Modifier.testTag("training_resume"))
                Spacer(Modifier.height(8.dp))
            }
            if(answered == 0) {
                Column(Modifier.testTag("training_empty"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.training_no_data), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.training_first_hint), style = MaterialTheme.typography.bodyMedium)
                    TonalButton(stringResource(R.string.knowledge_start), onClick = { nav.startQuiz(null) })
                }
            } else {
                Text(stringResource(R.string.training_overview_counts, answered, topics.sumOf { it.mistakes }),
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PrimaryButton(stringResource(R.string.training_start), onClick = { start(null) },
                    modifier = Modifier.testTag("training_start_all"))
            }
            Text(stringResource(R.string.training_no_bonus), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.training_topics), style = MaterialTheme.typography.titleLarge)
        topics.forEach { topic -> TrainingTopicCard(bank, topic, start) }
        Text(stringResource(R.string.training_scope_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    replacement?.let { request ->
        HorizonDialog(onDismissRequest = { replacement = null },
            title = { Text(stringResource(R.string.training_replace_title)) },
            text = { Text(stringResource(R.string.training_replace_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    LearningStore.clearSession("training"); replacement = null; nav.startTraining(request.category)
                }, modifier = Modifier.testTag("training_replace_confirm").semantics { testTagsAsResourceId = true }) {
                    Text(stringResource(R.string.training_replace_confirm))
                }
            }, dismissButton = {
                TextButton(onClick = { replacement = null }, modifier = Modifier.testTag("training_replace_cancel").semantics { testTagsAsResourceId = true }) {
                    Text(stringResource(R.string.action_close))
                }
            })
    }
}

@Composable
private fun TrainingTopicCard(bank: QuizBank, topic: TopicLearning, onStart: (String?) -> Unit) {
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("training_topic_${topic.category}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = scheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(bank.categoryName(topic.category), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(when(topic.status) {
            TopicLearningStatus.NEW -> R.string.training_status_new
            TopicLearningStatus.LIMITED -> R.string.training_status_limited
            TopicLearningStatus.PRACTICE -> R.string.training_status_practice
            TopicLearningStatus.BUILDING -> R.string.training_status_building
            TopicLearningStatus.RETAINED -> R.string.training_status_retained
        }), style = MaterialTheme.typography.labelLarge,
            color = if(topic.mistakes > 0) scheme.error else scheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.training_coverage,topic.answered,topic.total),
            modifier = Modifier.testTag("training_coverage_${topic.category}"),
            style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { topic.coverage },
            modifier = Modifier.fillMaxWidth().height(3.dp), drawStopIndicator = {})
        Spacer(Modifier.height(8.dp))
        if(topic.answered > 0) {
            Text(stringResource(R.string.training_error_count,topic.mistakes),
                modifier = Modifier.testTag("training_errors_${topic.category}"),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.training_retained_count,topic.retained),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            TonalButton(stringResource(R.string.training_topic_action), onClick = { onStart(topic.category) },
                modifier = Modifier.testTag("training_start_${topic.category}"))
        } else {
            TextButton(onClick = { nav.startQuiz(topic.category) },
                modifier = Modifier.testTag("training_discover_${topic.category}")) {
                Text(stringResource(R.string.training_discover))
            }
        }
    }
}

@Composable
private fun TrainingFlow(bank: QuizBank, category: String?) {
    val nav = LocalNav.current
    remember(bank, category, nav.quizRound, LearningStore.selectedLevel) {
        LearningStore.begin(bank,category,scope = "training")
    }
    val session = LearningStore.activeSession(bank,"training",category)
    when {
        session == null -> {
            Column(Modifier.testTag("training_empty").semantics { testTagsAsResourceId = true },
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.training_no_data), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.training_first_hint), style = MaterialTheme.typography.bodyLarge)
                TonalButton(stringResource(R.string.training_back_topics), onClick = { nav.trainingActive = false })
            }
        }
        session.stage == "RESULT" -> {
            Column(Modifier.testTag("training_result").semantics { testTagsAsResourceId = true },
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ZCard {
                    Text(stringResource(R.string.training_complete), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.training_result_score,session.correctIds.size,session.questionIds.size),
                        style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(if(session.correctIds.size == session.questionIds.size)
                        R.string.training_result_success else R.string.training_result_repeat),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.training_no_bonus), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val missed = session.questionIds.filter { it !in session.correctIds }
                    .mapNotNull { id -> bank.questions.firstOrNull { it.id == id } }
                if(missed.isNotEmpty()) {
                    Text(stringResource(R.string.training_review_errors), style = MaterialTheme.typography.titleLarge)
                    missed.forEach { q ->
                        ZCard {
                            Text(q.text, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.quiz_correct_answer,q.correctOption),
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Text(q.info, style = MaterialTheme.typography.bodyMedium)
                            SourceDetails(q.source)
                        }
                    }
                }
                PrimaryButton(stringResource(R.string.training_again), onClick = {
                    LearningStore.clearSession("training"); nav.startTraining(category)
                }, modifier = Modifier.testTag("training_again"))
                GhostButton(stringResource(R.string.training_back_topics), onClick = {
                    LearningStore.clearSession("training"); nav.trainingActive = false
                }, modifier = Modifier.testTag("training_done"))
            }
        }
        else -> {
            Text(stringResource(R.string.training_round_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            QuizRound(bank = bank, category = category, sessionScope = "training", reviewMode = true,
                onFinished = { LearningStore.completeCurrent("training", expectedSessionId = session.id) })
        }
    }
}
