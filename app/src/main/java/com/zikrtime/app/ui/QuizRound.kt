package com.zikrtime.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.zikrtime.app.data.Store
import com.zikrtime.app.quiz.Question
import com.zikrtime.app.quiz.hasArabic
import com.zikrtime.app.ui.theme.ArabicStyle

/**
 * Раунд викторины: нужно набрать [target] правильных ответов.
 * После неверного ответа показываем правильный и пояснение — и идём дальше.
 */
@Composable
fun QuizRound(
    questions: List<Question>,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    target: Int = Store.CORRECT_ANSWERS_PER_ROUND,
) {
    val order = remember(questions) { questions.shuffled() }
    var index by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val question = remember(order, index) { order[index % order.size].shuffled() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Правильных ответов: $correct из $target",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { correct / target.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                question.category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Text(question.text, style = MaterialTheme.typography.titleLarge)

        question.arabic?.let {
            Text(
                it,
                style = ArabicStyle.copy(textDirection = TextDirection.Rtl),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            question.options.forEachIndexed { i, option ->
                OptionRow(
                    text = option,
                    state = when {
                        selected == null -> OptionState.Idle
                        i == question.answerIndex -> OptionState.Correct
                        i == selected -> OptionState.Wrong
                        else -> OptionState.Dimmed
                    },
                    onClick = {
                        if (selected == null) {
                            selected = i
                            val ok = i == question.answerIndex
                            Store.recordAnswer(ok)
                            if (ok) correct++
                        }
                    },
                )
            }
        }

        AnimatedVisibility(visible = selected != null) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val ok = selected == question.answerIndex
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (ok) "Верно, машаАллах!" else "Правильный ответ: ${question.correctOption}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (question.explanation.isNotBlank()) {
                            Text(question.explanation, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Button(
                    onClick = {
                        if (correct >= target) {
                            onFinished()
                        } else {
                            index++
                            selected = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (correct >= target) "Завершить" else "Следующий вопрос")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private enum class OptionState { Idle, Correct, Wrong, Dimmed }

@Composable
private fun OptionRow(text: String, state: OptionState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, border) = when (state) {
        OptionState.Idle -> scheme.surfaceContainerLowest to scheme.outlineVariant
        OptionState.Correct -> scheme.primaryContainer to scheme.primary
        OptionState.Wrong -> scheme.errorContainer to scheme.error
        OptionState.Dimmed -> scheme.surfaceContainerLow to Color.Transparent
    }
    val arabic = text.hasArabic()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state == OptionState.Idle, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = if (arabic) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = if (arabic) ArabicStyle.copy(textDirection = TextDirection.Rtl) else MaterialTheme.typography.bodyLarge,
                textAlign = if (arabic) TextAlign.Center else TextAlign.Start,
                color = if (state == OptionState.Dimmed) scheme.onSurfaceVariant.copy(alpha = 0.6f) else scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            when (state) {
                OptionState.Correct -> Icon(Icons.Default.Check, contentDescription = "Верно", tint = scheme.primary, modifier = Modifier.size(22.dp))
                OptionState.Wrong -> Icon(Icons.Default.Close, contentDescription = "Неверно", tint = scheme.error, modifier = Modifier.size(22.dp))
                else -> Unit
            }
        }
    }
}
