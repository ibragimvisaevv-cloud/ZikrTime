@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import java.time.LocalDate

@Composable
fun PersonalProgressSetting() {
    val progressLabel = stringResource(R.string.progress_title)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.progress_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = Store.personalProgressEnabled,
                onCheckedChange = { Store.personalProgressEnabled = it },
                modifier = Modifier.testTag("personal_progress_toggle").semantics { testTagsAsResourceId = true; contentDescription = progressLabel },
            )
        }
        Text(stringResource(R.string.progress_setting_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen() {
    val nav = LocalNav.current
    var days by rememberSaveable { mutableIntStateOf(30) }
    var clear by remember { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.progress_title), onBack = { nav.back() }) {
        ZCard { PersonalProgressSetting() }
        if (Store.personalProgressEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { d ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(daysText(d)) })
                }
            }
            val summary = Store.personalProgressSummary(LocalDate.now(), days)
            ZCard {
                Text(stringResource(R.string.progress_sessions, summary.sessions))
                Text(stringResource(R.string.progress_topics, summary.topics))
                Text(stringResource(R.string.progress_reviewed, summary.reviewed))
                Text(stringResource(R.string.progress_active_days, summary.activeDays))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.progress_measurement_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (Store.totalAnswered > 0) {
                ZCard {
                    Text(stringResource(R.string.progress_answers, Store.totalAnswered, Store.totalCorrect))
                    Text(stringResource(R.string.progress_legacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Clearing is separate from disabling and remains available even with the switch off.
        TextButton(onClick = { clear = true }, modifier = Modifier.testTag("personal_progress_clear").semantics { testTagsAsResourceId = true }) {
            Text(stringResource(R.string.progress_clear))
        }
    }
    if (clear) AlertDialog(
        onDismissRequest = { clear = false },
        title = { Text(stringResource(R.string.progress_clear)) },
        text = { Text(stringResource(R.string.progress_clear_confirm)) },
        confirmButton = { TextButton(onClick = { Store.clearPersonalProgress(); clear = false }, modifier = Modifier.testTag("personal_progress_clear_confirm").semantics { testTagsAsResourceId = true }) { Text(stringResource(R.string.action_reset)) } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text(stringResource(R.string.action_close)) } },
    )
}
