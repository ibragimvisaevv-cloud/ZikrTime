package com.zikrtime.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zikrtime.app.data.Store
import com.zikrtime.app.data.Streaks
import com.zikrtime.app.quiz.QuestionBank
import java.time.LocalDate

private sealed class TasksMode {
    data object Menu : TasksMode()
    data object Playing : TasksMode()
    data class Done(val addedMinutes: Int) : TasksMode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen() {
    val context = LocalContext.current
    val all = remember { QuestionBank.all(context) }
    val categories = remember(all) { all.map { it.category }.distinct() }
    var category by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf<TasksMode>(TasksMode.Menu) }
    var roundKey by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Задачи", style = MaterialTheme.typography.headlineSmall)

        when (val m = mode) {
            TasksMode.Menu -> {
                SectionCard {
                    Text(
                        "Ответьте правильно на ${Store.CORRECT_ANSWERS_PER_ROUND} вопроса и получите " +
                            "+${Store.BONUS_MINUTES_PER_ROUND} минут к лимиту соцсетей. " +
                            "Можно заработать до ${Store.MAX_BONUS_MINUTES_PER_DAY} минут в день.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Сегодня заработано: ${Store.bonusMinutes()} из ${Store.MAX_BONUS_MINUTES_PER_DAY} мин",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Text("Тема", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Все темы") })
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }

                Button(
                    onClick = {
                        roundKey++
                        mode = TasksMode.Playing
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Начать раунд") }

                SectionCard(title = "Ваш прогресс") {
                    val answered = Store.totalAnswered
                    val correct = Store.totalCorrect
                    val accuracy = if (answered > 0) correct * 100 / answered else 0
                    StatLine("Правильных ответов", "$correct")
                    StatLine("Точность", if (answered > 0) "$accuracy%" else "—")
                    StatLine("Серия знаний", days(Streaks.current(Store.quizDays, LocalDate.now())))
                    StatLine("Лучшая серия", days(Streaks.best(Store.quizDays)))
                }
            }

            TasksMode.Playing -> {
                val pool = remember(category, roundKey) {
                    all.filter { category == null || it.category == category }.ifEmpty { all }
                }
                key(roundKey) {
                    QuizRound(
                        questions = pool,
                        onFinished = {
                            val added = Store.addBonus()
                            Store.recordRoundCompleted()
                            mode = TasksMode.Done(added)
                        },
                    )
                }
                OutlinedButton(onClick = { mode = TasksMode.Menu }, modifier = Modifier.fillMaxWidth()) {
                    Text("Прервать раунд")
                }
            }

            is TasksMode.Done -> {
                SectionCard {
                    Text("МашаАллах!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (m.addedMinutes > 0) {
                            "Раунд пройден. К лимиту соцсетей добавлено ${m.addedMinutes} мин."
                        } else {
                            "Раунд пройден. Бонус на сегодня уже набран полностью, но знания остаются с вами."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Button(
                    onClick = {
                        roundKey++
                        mode = TasksMode.Playing
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Ещё раунд") }
                OutlinedButton(onClick = { mode = TasksMode.Menu }, modifier = Modifier.fillMaxWidth()) {
                    Text("Готово")
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
    }
}
