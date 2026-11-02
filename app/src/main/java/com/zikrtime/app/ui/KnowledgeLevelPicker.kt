@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.quiz.KnowledgeLevel
import com.zikrtime.app.quiz.LearningStore
import com.zikrtime.app.quiz.QuizBank

@Composable
private fun levelName(level: KnowledgeLevel): String = stringResource(when(level) {
    KnowledgeLevel.BEGINNER -> R.string.learning_level_beginner
    KnowledgeLevel.INTERMEDIATE -> R.string.learning_level_intermediate
    KnowledgeLevel.ADVANCED -> R.string.learning_level_advanced
})

/** Every quiz entry uses the same explicit first choice, including the focus blocker. */
@Composable
fun KnowledgeLevelGate(bank: QuizBank, content: @Composable (KnowledgeLevel) -> Unit) {
    val level = LearningStore.selectedLevel
    if (level == null) KnowledgeLevelPicker(bank, onConfirm = LearningStore::selectLevel)
    else content(level)
}

@Composable
fun KnowledgeLevelControl(bank: QuizBank) {
    val current = LearningStore.selectedLevel
    var choosing by remember { mutableStateOf(false) }
    val changeButton: @Composable () -> Unit = {
        TextButton(onClick = { choosing = true }, modifier = Modifier.testTag("learning_level_change")
            .semantics { testTagsAsResourceId = true }) {
            Text(stringResource(if(current==null) R.string.learning_level_choose else R.string.learning_level_change))
        }
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f),
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            if(maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KnowledgeLevelSummary(current)
                    changeButton()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KnowledgeLevelSummary(current, Modifier.weight(1f))
                    changeButton()
                }
            }
        }
    }
    if(choosing) HorizonSheet(stringResource(R.string.learning_level_title), onDismiss = { choosing = false }) {
        KnowledgeLevelPicker(bank, current, showTitle = false, onConfirm = {
            LearningStore.selectLevel(it); choosing = false
        })
        TextButton(onClick = { choosing = false }, modifier = Modifier.testTag("learning_level_cancel")) {
            Text(stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun KnowledgeLevelSummary(current: KnowledgeLevel?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.learning_level_label), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(current?.let { levelName(it) } ?: stringResource(R.string.learning_level_not_selected),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("learning_level_current")
                .semantics { testTagsAsResourceId = true })
    }
}

@Composable
private fun KnowledgeLevelPicker(bank: QuizBank, initial: KnowledgeLevel? = null, showTitle: Boolean = true,
    onConfirm: (KnowledgeLevel) -> Unit) {
    var choice by remember(initial) { mutableStateOf(initial) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true },
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if(showTitle) Text(stringResource(R.string.learning_level_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.learning_level_intro), style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KnowledgeLevel.entries.forEach { level ->
                val selected = choice == level
                Surface(shape = RoundedCornerShape(22.dp),
                    color = if(selected) scheme.secondaryContainer else scheme.surfaceContainer,
                    border = BorderStroke(1.dp, if(selected) scheme.secondary else scheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { choice = level })
                        .testTag("learning_level_${level.id}").padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(levelName(level), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(when(level) {
                                KnowledgeLevel.BEGINNER -> R.string.learning_level_beginner_hint
                                KnowledgeLevel.INTERMEDIATE -> R.string.learning_level_intermediate_hint
                                KnowledgeLevel.ADVANCED -> R.string.learning_level_advanced_hint
                            }), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            Text(stringResource(R.string.learning_level_count, bank.questionsFor(null,level.id).size),
                                style = MaterialTheme.typography.labelMedium, color = scheme.secondary)
                        }
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }
        }
        if(initial != null) Text(stringResource(R.string.learning_level_change_hint),
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        PrimaryButton(stringResource(R.string.learning_level_confirm),
            onClick = { choice?.let(onConfirm) }, enabled = choice != null,
            modifier = Modifier.testTag("learning_level_confirm"))
    }
}
