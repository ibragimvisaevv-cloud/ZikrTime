package com.zikrtime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.data.Streaks
import com.zikrtime.app.quiz.QuestionBank
import java.time.LocalDate

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

    TabColumn(title = stringResource(R.string.knowledge_title), subtitle = stringResource(R.string.knowledge_subtitle)) {
        ZCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                stringResource(R.string.knowledge_round_title, Store.CORRECT_ANSWERS_PER_ROUND, Store.BONUS_MINUTES_PER_ROUND),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.knowledge_round_text, Store.MAX_BONUS_MINUTES_PER_DAY),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { bonus / Store.MAX_BONUS_MINUTES_PER_DAY.toFloat() },
                    modifier = Modifier.weight(1f).height(8.dp),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.knowledge_bonus_today, bonus, Store.MAX_BONUS_MINUTES_PER_DAY),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.knowledge_start), onClick = { nav.startQuiz(null) })
        }

        SectionLabel(stringResource(R.string.knowledge_topics))
        bank.categories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (id, name) ->
                    val total = bank.questions.count { it.category == id }
                    ZCard(
                        modifier = Modifier.weight(1f),
                        onClick = { nav.startQuiz(id) },
                        padding = PaddingValues(16.dp),
                    ) {
                        IconBadge(painterResource(categoryIcon(id)), size = 36.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.knowledge_topic_progress, Store.categoryCorrect(id), total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        SectionLabel(stringResource(R.string.knowledge_progress))
        ZCard {
            val answered = Store.totalAnswered
            val correct = Store.totalCorrect
            StatLine(stringResource(R.string.stat_correct), correct.toString())
            StatLine(stringResource(R.string.stat_accuracy), if (answered > 0) "${correct * 100 / answered}%" else "—")
            StatLine(stringResource(R.string.streak_knowledge), daysText(Streaks.current(Store.quizDays, LocalDate.now())))
            StatLine(stringResource(R.string.stat_best_streak), daysText(Streaks.best(Store.quizDays)))
        }
        Text(
            stringResource(R.string.knowledge_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Экран раунда викторины. */
@Composable
fun QuizScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val bank = remember { QuestionBank.load(context) }
    val category = nav.quizCategory
    val title = category?.let { bank.categoryName(it) } ?: stringResource(R.string.knowledge_title)
    ScreenScaffold(title = title, onBack = { nav.back() }) {
        androidx.compose.runtime.key(nav.quizRound) {
            QuizFlow(
                bank = bank,
                category = category,
                onDone = { nav.back() },
                onAgain = { nav.quizRound++ },
            )
        }
    }
}

/** Этапы раунда: предложение подготовиться → карточки → вопросы → итог. */
private enum class Stage { ASK, CARDS, QUIZ, RESULT }

@Composable
private fun QuizFlow(bank: com.zikrtime.app.quiz.QuizBank, category: String?, onDone: () -> Unit, onAgain: () -> Unit) {
    val nav = LocalNav.current
    var stage by rememberSaveable { mutableStateOf(if (nav.quizStartWithCards) Stage.CARDS else Stage.ASK) }
    var added by rememberSaveable { mutableStateOf(0) }
    // Порядок вопросов определяем сразу: карточки — ровно по первым вопросам раунда.
    val order = remember(bank, category) { QuestionBank.roundOrder(bank, category, Store.recentQuestions) }
    val cards = remember(order) { order.take(PREP_CARDS) }
    when (stage) {
        Stage.ASK -> PrepOffer(onRead = { stage = Stage.CARDS }, onSkip = { stage = Stage.QUIZ })
        Stage.CARDS -> StudyCards(bank = bank, cards = cards, onReady = { stage = Stage.QUIZ })
        Stage.QUIZ -> QuizRound(
            bank = bank,
            category = category,
            plannedOrder = order,
            onFinished = {
                added = Store.addBonus()
                Store.recordRoundCompleted()
                stage = Stage.RESULT
            },
        )
        Stage.RESULT -> RoundResult(addedMinutes = added, onAgain = onAgain, onDone = onDone)
    }
}

private const val PREP_CARDS = 5

/** «Хотите подготовиться?» — две кнопки. */
@Composable
private fun PrepOffer(onRead: () -> Unit, onSkip: () -> Unit) {
    GlassHero {
        Column(Modifier.padding(24.dp)) {
            Text("📖", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.prep_title), style = MaterialTheme.typography.headlineSmall, color = com.zikrtime.app.ui.theme.Cream)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.prep_text), style = MaterialTheme.typography.bodyLarge, color = com.zikrtime.app.ui.theme.Cream.copy(alpha = 0.85f))
            Spacer(Modifier.height(20.dp))
            GoldButton(stringResource(R.string.prep_read), onClick = onRead)
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.prep_skip), onClick = onSkip, color = com.zikrtime.app.ui.theme.Cream)
        }
    }
}

/** Учебные карточки: по одной справке на карточку, листаются кнопкой «Дальше». */
@Composable
private fun StudyCards(bank: com.zikrtime.app.quiz.QuizBank, cards: List<com.zikrtime.app.quiz.Question>, onReady: () -> Unit) {
    var index by rememberSaveable { mutableStateOf(0) }
    if (cards.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onReady() }
        return
    }
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
            (androidx.compose.animation.slideInHorizontally { it / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                (androidx.compose.animation.slideOutHorizontally { -it / 3 } + androidx.compose.animation.fadeOut())
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
                    style = com.zikrtime.app.ui.theme.ArabicStyle.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(q.info, style = MaterialTheme.typography.titleMedium.copy(lineHeight = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)))
        }
    }
    if (index < cards.size - 1) {
        ShimmerButton(stringResource(R.string.prep_next), onClick = { index++ })
    } else {
        ShimmerButton(stringResource(R.string.prep_start_quiz), onClick = onReady)
    }
    GhostButton(stringResource(R.string.prep_skip), onClick = onReady)
}

@Composable
fun RoundResult(addedMinutes: Int, onAgain: (() -> Unit)?, onDone: () -> Unit, doneText: String = stringResource(R.string.action_done)) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(painterResource(R.drawable.ic_check), size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.quiz_mashallah), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            if (addedMinutes > 0) stringResource(R.string.quiz_result_added, addedMinutes)
            else stringResource(R.string.quiz_result_no_bonus),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
    Spacer(Modifier.height(8.dp))
    if (onAgain != null) PrimaryButton(stringResource(R.string.quiz_again), onClick = onAgain)
    GhostButton(doneText, onClick = onDone)
}
