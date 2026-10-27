package com.zikrtime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.AzkarStore
import com.zikrtime.app.data.SourceInfo
import com.zikrtime.app.ui.theme.ArabicStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AzkarScreen() {
    val nav = LocalNav.current
    val period = nav.azkarPeriod
    LaunchedEffect(period) { AzkarStore.start(period) }
    val index = AzkarStore.pendingIndex
    ScreenScaffold(title = stringResource(if (period == "evening") R.string.azkar_evening else R.string.azkar_morning), onBack = { nav.back() }) {
        Text(stringResource(R.string.azkar_collection_note), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { nav.openAzkar("morning") }) { Text(stringResource(R.string.azkar_morning)) }
            TextButton(onClick = { nav.openAzkar("evening") }) { Text(stringResource(R.string.azkar_evening)) }
        }
        ZCard {
            Text(stringResource(R.string.azkar_position, index + 1, TASBIH_ARABIC.size), style = MaterialTheme.typography.labelLarge)
            Text(TASBIH_ARABIC[index], modifier = Modifier.fillMaxWidth(), style = ArabicStyle.copy(fontSize = 34.sp, textDirection = TextDirection.Rtl), textAlign = TextAlign.Center)
            Text(stringResource(TASBIH_NAMES[index].second), style = MaterialTheme.typography.bodyLarge)
            SourceDetails(SourceInfo())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { AzkarStore.move(index - 1) }, enabled = index > 0) { Text(stringResource(R.string.azkar_previous)) }
            Button(onClick = {
                if (index < TASBIH_ARABIC.lastIndex) AzkarStore.move(index + 1)
                else { AzkarStore.finish(); nav.back() }
            }) { Text(stringResource(if (index < TASBIH_ARABIC.lastIndex) R.string.azkar_next else R.string.azkar_complete)) }
        }
        Text(stringResource(R.string.azkar_completion_note), style = MaterialTheme.typography.bodySmall)
    }
}
