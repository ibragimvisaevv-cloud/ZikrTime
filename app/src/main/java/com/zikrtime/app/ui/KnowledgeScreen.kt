@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.quiz.QuestionBank
import com.zikrtime.app.quiz.LearningStore
import com.zikrtime.app.quiz.LearningSession
import com.zikrtime.app.quiz.QuizBank
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

fun categoryIcon(id: String): Int = when (id) {
    "quran" -> R.drawable.ic_nav_knowledge
    "prophets" -> R.drawable.ic_prayer_isha
    "seerah" -> R.drawable.ic_nav_prayer
    "fiqh" -> R.drawable.ic_kaaba
    "arabic" -> R.drawable.ic_language
    "ayah" -> R.drawable.ic_tasbih
    else -> R.drawable.ic_nav_knowledge
}

@Composable
fun KnowledgeScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val bank = remember { QuestionBank.load(context) }
    val bonus = Store.bonusMinutes()
    var clearLearning by remember { mutableStateOf(false) }

    val zone = CityZones.forCity(Store.location.id, java.time.ZoneId.systemDefault())
    val morning = java.time.ZonedDateTime.now(zone).hour < 15
    HorizonSection(title = stringResource(R.string.knowledge_title),
        subtitle = sectionCopy(SectionClarity.KnowledgeIntro),
        image = if (morning) R.drawable.azkar_morning else R.drawable.azkar_evening,
        heroHeight = 180.dp,
        hero = {
            DestinationRow(stringResource(if (morning) R.string.azkar_morning else R.string.azkar_evening),
                if (morning) R.drawable.ic_prayer_fajr else R.drawable.ic_prayer_isha) {
                nav.openAzkar(if (morning) "morning" else "evening")
            }
        }) {
        if (com.zikrtime.app.data.AzkarStore.hasPending) {
            TextButton(onClick = { nav.resumeAzkar() }) { Text(stringResource(R.string.my_day_resume_azkar)) }
        }
        LearningStore.pendingSession(bank)?.let {
            PrimaryButton(stringResource(R.string.learning_resume), onClick = { nav.resumeLearning(context) },
                modifier = Modifier.testTag("learning_resume").semantics { testTagsAsResourceId = true })
        }
        SectionLabel(uiCopy(UiCopy0823.ReadAndRemember))
        DestinationRow(stringResource(if (morning) R.string.azkar_evening else R.string.azkar_morning),
            if (morning) R.drawable.ic_prayer_isha else R.drawable.ic_prayer_fajr) {
            nav.openAzkar(if (morning) "evening" else "morning")
        }
        Column(Modifier.testTag("quran_open").semantics { testTagsAsResourceId = true }) {
            DestinationRow(stringResource(R.string.quran_title), R.drawable.ic_nav_knowledge,
                sectionCopy(SectionClarity.QuranHint)) { nav.open(Screen.QURAN) }
        }
        SectionLabel(uiCopy(UiCopy0823.PracticeKnowledge))
        KnowledgeLevelGate(bank) { level ->
            KnowledgeLevelControl(bank)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiCopy(UiCopy0823.StudyIntro), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                PrimaryButton(sectionCopy(SectionClarity.Questions), onClick = { nav.startQuiz(null) },
                    modifier = Modifier.testTag("learning_start").semantics { testTagsAsResourceId = true })
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.knowledge_round_title, Store.CORRECT_ANSWERS_PER_ROUND, Store.BONUS_MINUTES_PER_ROUND),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.learning_bonus_rule, Store.MAX_BONUS_MINUTES_PER_DAY),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(sectionCopy(SectionClarity.BonusMeaning), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { bonus / Store.MAX_BONUS_MINUTES_PER_DAY.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp), strokeCap = StrokeCap.Round, drawStopIndicator = {})
                Text(stringResource(R.string.knowledge_bonus_today, bonus, Store.MAX_BONUS_MINUTES_PER_DAY),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
            SectionLabel(stringResource(R.string.knowledge_topics))
            Text(uiCopy(UiCopy0823.TopicHint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            bank.categories.filter { (id, _) -> bank.questionsFor(id,level.id).isNotEmpty() }
                .forEach { (id, name) ->
                DestinationRow(if (id == "quran") stringResource(R.string.quran_quiz) else name,
                    categoryIcon(id), stringResource(R.string.learning_level_count, bank.questionsFor(id, level.id).size)) {
                    nav.startQuiz(id)
                }
            }
            WeakTopicsOverview(bank)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text(sectionCopy(SectionClarity.Review), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.learning_review_hint), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            TonalButton(sectionCopy(SectionClarity.Review), onClick = { nav.startReview() },
                modifier = Modifier.testTag("learning_review_start").semantics { testTagsAsResourceId = true })
            TextButton(onClick = { clearLearning = true },
                modifier = Modifier.testTag("learning_clear").semantics { testTagsAsResourceId = true }) {
                Text(stringResource(R.string.learning_clear))
            }
        }
        if (Store.personalProgressEnabled) {
            val summary = Store.personalProgressSummary()
            DestinationRow(stringResource(R.string.progress_title), R.drawable.ic_nav_knowledge,
                stringResource(R.string.progress_home_summary, summary.sessions, summary.activeDays)) { nav.open(Screen.PROGRESS) }
        }
        Text(
            stringResource(R.string.knowledge_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    // Learning history is independent of personal statistics; clearing is always explicit.
    if (clearLearning) HorizonDialog(
        onDismissRequest = { clearLearning = false },
        title = { Text(stringResource(R.string.learning_clear)) },
        text = { Text(stringResource(R.string.learning_clear_confirmation)) },
        confirmButton = {
            TextButton(onClick = { LearningStore.resetLearning(); clearLearning = false },
                modifier = Modifier.testTag("learning_clear_confirm").semantics { testTagsAsResourceId = true }) {
                Text(stringResource(R.string.action_reset))
            }
        },
        dismissButton = {
            TextButton(onClick = { clearLearning = false },
                modifier = Modifier.testTag("learning_clear_cancel").semantics { testTagsAsResourceId = true }) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

/** Экран раунда викторины. */
@Composable
fun QuizScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val bank = remember { QuestionBank.load(context) }
    val category = nav.quizCategory
    val title = category?.let { bank.categoryName(it) } ?: stringResource(R.string.knowledge_title)
    var readingScale by rememberReadingScale()
    val session = LearningStore.session("normal")
    val sessionKey = "${LearningStore.selectedLevel?.id}-${session?.id}-${session?.stage}-${session?.index}-${session?.cardIndex}"
    ScreenScaffold(title = title, onBack = {
        if(LearningStore.session("normal")?.stage == "RESULT") LearningStore.clearSession("normal")
        nav.back()
    }, showHeading = false, scrollKey = sessionKey,
        actions = { ReadingTools(readingScale) { readingScale = it } }) {
        KnowledgeLevelGate(bank) { level ->
            KnowledgeLevelControl(bank)
            androidx.compose.runtime.key(nav.quizRound, level.id) {
                QuizFlow(
                    bank = bank,
                    category = category,
                    onDone = { LearningStore.clearSession("normal"); nav.back() },
                    onAgain = { nav.quizRound++ },
                )
            }
        }
    }
}

/** Persist the actual material, question order, answer and stage, not only the route. */
@Composable
private fun QuizFlow(bank: QuizBank, category: String?, onDone: () -> Unit, onAgain: () -> Unit) {
    val nav = LocalNav.current
    remember(bank, category, nav.quizRound) { LearningStore.begin(bank, category, cards = nav.quizStartWithCards) }
    val session = LearningStore.activeSession(bank,"normal",category)
    if (session == null) {
        StatePanel(stringResource(R.string.learning_level_empty), stringResource(R.string.learning_level_empty_hint))
        return
    }
    val cards = session.questionIds.take(PREP_CARDS).mapNotNull { id -> bank.questions.firstOrNull { it.id == id } }
    when (session.stage) {
        "ASK" -> PrepOffer(
            onRead = { LearningStore.update(session.copy(stage = "CARDS"), expected = session) },
            onSkip = { LearningStore.update(session.copy(stage = "QUIZ"), expected = session) },
        )
        "CARDS" -> StudyCards(bank, cards, session,
            onReady = { LearningStore.update(session.copy(stage = "QUIZ"), expected = session) })
        "QUIZ" -> QuizRound(bank = bank, category = category, sessionScope = "normal",
            onFinished = { LearningStore.completeCurrent("normal", expectedSessionId = session.id) })
        "RESULT" -> RoundResult(addedMinutes = session.addedMinutes, onAgain = {
            LearningStore.clearSession("normal"); onAgain()
        }, onDone = onDone)
    }
}

@Composable
fun ReviewScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val bank = remember { QuestionBank.load(context) }
    val session = LearningStore.session("review")
    ScreenScaffold(title = stringResource(R.string.learning_review), onBack = { nav.back() },
        scrollKey = "${LearningStore.selectedLevel?.id}-${session?.id}-${session?.stage}-${session?.index}") {
        KnowledgeLevelGate(bank) { level ->
            KnowledgeLevelControl(bank)
            androidx.compose.runtime.key(level.id) { ReviewFlow(bank) }
        }
    }
}

@Composable
private fun ReviewFlow(bank: QuizBank) {
    val nav = LocalNav.current
    remember(bank) { LearningStore.begin(bank, null, scope = "review") }
    val session = LearningStore.activeSession(bank,"review",null)
    when {
        session == null -> StatePanel(stringResource(R.string.learning_none_due), stringResource(R.string.learning_none_due_hint), action = stringResource(R.string.knowledge_start), onAction = { nav.startQuiz(null) })
        session.stage == "RESULT" -> {
            Text(stringResource(R.string.learning_complete), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.learning_review_done), style = MaterialTheme.typography.bodyLarge)
            PrimaryButton(stringResource(R.string.action_done), onClick = { LearningStore.clearSession("review"); nav.back() })
        }
        else -> QuizRound(bank = bank, category = null, sessionScope = "review", reviewMode = true,
            onFinished = { LearningStore.completeCurrent("review", expectedSessionId = session.id) })
    }
}

private const val PREP_CARDS = 5

/** «Хотите подготовиться?» — две кнопки. */
@Composable
private fun PrepOffer(onRead: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        IconBadge(painterResource(R.drawable.ic_nav_knowledge), size = 44.dp)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.prep_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.prep_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        PrimaryButton(stringResource(R.string.prep_read), onClick = onRead, modifier = Modifier.testTag("learning_read").semantics { testTagsAsResourceId = true })
        Spacer(Modifier.height(10.dp))
        GhostButton(stringResource(R.string.prep_skip), onClick = onSkip, modifier = Modifier.testTag("learning_skip").semantics { testTagsAsResourceId = true })
    }
}

/** Учебные карточки: по одной справке на карточку, листаются кнопкой «Дальше». */
@Composable
private fun StudyCards(bank: QuizBank, cards: List<com.zikrtime.app.quiz.Question>, session: LearningSession, onReady: () -> Unit) {
    val readingScale by rememberReadingScale()
    val motion = motionEnabled()
    val index = session.cardIndex.coerceAtMost((cards.size - 1).coerceAtLeast(0))
    if (cards.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onReady() }
        return
    }
    androidx.compose.runtime.LaunchedEffect(session.id, index) { LearningStore.showExplanation(cards[index]) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        cards.indices.forEach { i ->
            LinearProgressIndicator(
                progress = { if (i <= index) 1f else 0f },
                modifier = Modifier.weight(1f).height(5.dp),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
        }
    }
    Text(
        stringResource(R.string.prep_card_progress, index + 1, cards.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    androidx.compose.animation.AnimatedContent(
        targetState = index,
        transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if (motion) 160 else 0)) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (motion) 90 else 0))
        },
        label = "card",
    ) { i ->
        val q = cards[i]
        ZCard(padding = PaddingValues(22.dp)) {
            CategoryChip(bank.categoryName(q.category))
            Spacer(Modifier.height(14.dp))
            q.arabic?.let { arabic ->
                Text(
                    arabic,
                    style = com.zikrtime.app.ui.theme.ArabicStyle.copy(fontSize = (30 * readingScale).sp, textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(q.info, style = MaterialTheme.typography.bodyLarge.copy(fontSize = (19 * readingScale).sp, lineHeight = (30 * readingScale).sp))
            Spacer(Modifier.height(12.dp))
            SourceDetails(q.source)
        }
    }
    if (index < cards.size - 1) {
        ShimmerButton(stringResource(R.string.prep_next), onClick = { LearningStore.update(session.copy(cardIndex = index + 1), expected = session) }, modifier = Modifier.testTag("learning_card_next").semantics { testTagsAsResourceId = true })
    } else {
        ShimmerButton(stringResource(R.string.prep_start_quiz), onClick = onReady, modifier = Modifier.testTag("learning_card_quiz").semantics { testTagsAsResourceId = true })
    }
    GhostButton(stringResource(R.string.prep_skip), onClick = onReady)
}

@Composable
fun RoundResult(addedMinutes: Int, onAgain: (() -> Unit)?, onDone: () -> Unit, doneText: String = stringResource(R.string.action_done)) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(painterResource(R.drawable.ic_check), size = 48.dp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.learning_complete), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (addedMinutes > 0) stringResource(R.string.quiz_result_added, addedMinutes)
            else stringResource(R.string.learning_no_bonus),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
    Spacer(Modifier.height(8.dp))
    if (onAgain != null) PrimaryButton(stringResource(R.string.quiz_again), onClick = onAgain)
    GhostButton(doneText, onClick = onDone)
}
