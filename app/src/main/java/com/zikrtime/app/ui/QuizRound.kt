@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.quiz.LearningStore
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.zikrtime.app.quiz.QuizBank
import com.zikrtime.app.quiz.hasArabic
import com.zikrtime.app.ui.theme.ArabicStyle

/**
 * Раунд викторины: нужно набрать [target] правильных ответов.
 * После каждого ответа показываем справку по теме вопроса.
 */
@Composable
fun QuizRound(
    bank: QuizBank,
    category: String?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    target: Int = Store.CORRECT_ANSWERS_PER_ROUND,
    finishText: String = stringResource(R.string.quiz_finish),
    /** Готовый порядок вопросов (например, после учебных карточек). */
    plannedOrder: List<com.zikrtime.app.quiz.Question>? = null,
    sessionScope: String = "blocking",
    reviewMode: Boolean = false,
) {
    val persisted = LearningStore.session(sessionScope)
    val initial = remember(bank, category, sessionScope) { LearningStore.begin(bank, category, sessionScope) }
    val session = persisted ?: initial ?: return
    if (session.stage == "RESULT") return
    val question = bank.questions.firstOrNull { it.id == session.questionIds.getOrNull(session.index) } ?: return
    val displayOrder = LearningStore.displayOrder(session, question)
    val selected = session.selected?.let { displayOrder.indexOf(it) }
    val correct = session.correctIds.size
    val view = androidx.compose.ui.platform.LocalView.current
    val scheme = MaterialTheme.colorScheme

    Column(modifier.semantics { testTagsAsResourceId = true }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Прогресс: точки правильных ответов
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(if (reviewMode) session.questionIds.size else target) { i ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(width = 28.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i < (if (reviewMode) session.index else correct)) scheme.primary else scheme.surfaceVariant),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.quiz_progress, if (reviewMode) session.index else correct, if (reviewMode) session.questionIds.size else target),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = scheme.secondaryContainer) {
                Text(
                    bank.categoryName(question.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))

        }

        Text(question.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("learning_question"))

        question.arabic?.let {
            Surface(shape = RoundedCornerShape(20.dp), color = scheme.primaryContainer.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                Text(
                    it,
                    style = ArabicStyle.copy(textDirection = TextDirection.Rtl),
                    textAlign = TextAlign.Center,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            displayOrder.forEachIndexed { i, canonical ->
                val option = question.options[canonical]
                OptionCard(
                    celebrate = Store.personalProgressEnabled && selected == i && canonical == question.answerIndex,
                    modifier = Modifier.testTag("learning_answer_$i"),
                    letter = ('A' + i).toString(),
                    text = option,
                    state = when {
                        selected == null -> OptionState.Idle
                        canonical == question.answerIndex -> OptionState.Correct
                        i == selected -> OptionState.Wrong
                        else -> OptionState.Dimmed
                    },
                    onClick = {
                        if (selected == null) {
                            val ok = canonical == question.answerIndex
                            LearningStore.answer(sessionScope, question, canonical)
                            view.performHapticFeedback(if (ok) android.view.HapticFeedbackConstants.VIRTUAL_KEY else android.view.HapticFeedbackConstants.LONG_PRESS)
                        }
                    },
                )
            }
        }

        AnimatedVisibility(visible = selected != null, enter = fadeIn() + expandVertically(), modifier = Modifier.testTag("learning_feedback")) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val ok = session.selected == question.answerIndex
                InfoCard(ok = ok, correctAnswer = question.correctOption, info = question.info)
                SourceDetails(question.source)
                val finished = (if (reviewMode) false else correct >= target) || session.index >= session.questionIds.lastIndex
                PrimaryButton(
                    if (finished) finishText else stringResource(R.string.quiz_next),
                    modifier = Modifier.testTag(if (finished) "learning_finish" else "learning_next"),
                    onClick = {
                        if (finished) {
                            onFinished()
                        } else {
                            LearningStore.update(session.copy(index=session.index+1, selected=null))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun InfoCard(ok: Boolean, correctAnswer: String, info: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (ok) scheme.primaryContainer else scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ok) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Default.Close, contentDescription = null, tint = scheme.error, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ok) stringResource(R.string.quiz_correct) else stringResource(R.string.quiz_wrong),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!ok) {
                Text(
                    stringResource(R.string.quiz_correct_answer, correctAnswer),
                    style = if (correctAnswer.hasArabic()) ArabicStyle.copy(textDirection = TextDirection.Content) else MaterialTheme.typography.bodyLarge,
                )
            }
            if (info.isNotBlank()) {
                HorizontalDivider(color = scheme.outlineVariant)
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.quiz_info_title), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                        Spacer(Modifier.height(2.dp))
                        Text(info, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private enum class OptionState { Idle, Correct, Wrong, Dimmed }

@Composable
private fun OptionCard(letter: String, text: String, state: OptionState, onClick: () -> Unit, celebrate: Boolean = false, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // Верный ответ «подпрыгивает» и рассыпает искры, неверный — покачивается.
    val bump = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(celebrate) {
        if (celebrate) {
            bump.animateTo(1.045f, androidx.compose.animation.core.tween(120))
            bump.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    Box(
        modifier
            .shake(Store.personalProgressEnabled && state == OptionState.Wrong)
            .graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            },
    ) {
        OptionSurface(letter, text, state, onClick)
        if (celebrate) {
            SparkleBurst(key = text, modifier = Modifier.matchParentSize(), count = 22, spread = 120.dp)
        }
    }
}

@Composable
private fun OptionSurface(letter: String, text: String, state: OptionState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, border) = when (state) {
        OptionState.Idle -> scheme.surfaceContainerLowest to scheme.outlineVariant
        OptionState.Correct -> scheme.primaryContainer to scheme.primary
        OptionState.Wrong -> scheme.errorContainer to scheme.error
        OptionState.Dimmed -> scheme.surfaceContainerLow to Color.Transparent
    }
    val arabic = text.hasArabic()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(if (state == OptionState.Idle) 1.dp else 1.5.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = state == OptionState.Idle, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = if (arabic) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            OptionState.Correct -> scheme.primary
                            OptionState.Wrong -> scheme.error
                            else -> scheme.surfaceContainerHigh
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (state) {
                        OptionState.Correct -> scheme.onPrimary
                        OptionState.Wrong -> scheme.onError
                        else -> scheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text,
                style = if (arabic) ArabicStyle.copy(textDirection = TextDirection.Rtl) else MaterialTheme.typography.bodyLarge,
                textAlign = if (arabic) TextAlign.Center else TextAlign.Start,
                color = if (state == OptionState.Dimmed) scheme.onSurfaceVariant.copy(alpha = 0.6f) else scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
