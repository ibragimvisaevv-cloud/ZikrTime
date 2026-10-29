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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = period == "morning", onClick = { nav.openAzkar("morning") }, label = { Text(stringResource(R.string.azkar_morning)) })
            FilterChip(selected = period == "evening", onClick = { nav.openAzkar("evening") }, label = { Text(stringResource(R.string.azkar_evening)) })
        }
        LandscapePanel(if (period == "evening") R.drawable.azkar_evening else R.drawable.azkar_morning) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.azkar_position, index + 1, TASBIH_ARABIC.size), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { (index + 1f) / TASBIH_ARABIC.size },
                    modifier = Modifier.fillMaxWidth().height(4.dp), color = Sand, trackColor = Porcelain.copy(alpha = .25f), drawStopIndicator = {})
            }
        }
        ZCard(padding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)) {
            Text(stringResource(R.string.azkar_position, index + 1, TASBIH_ARABIC.size), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(36.dp))
            Text(TASBIH_ARABIC[index], modifier = Modifier.fillMaxWidth(), style = ArabicStyle.copy(fontSize = 34.sp, textDirection = TextDirection.Rtl), textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(TASBIH_NAMES[index].second), style = MaterialTheme.typography.bodyLarge)
            SourceDetails(SourceInfo())
        }
        ShimmerButton(stringResource(if (index < TASBIH_ARABIC.lastIndex) R.string.azkar_next else R.string.azkar_complete), onClick = {
            if (index < TASBIH_ARABIC.lastIndex) AzkarStore.move(index + 1)
            else { AzkarStore.finish(); nav.back() }
        })
        OutlinedButton(onClick = { AzkarStore.move(index - 1) }, enabled = index > 0, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.azkar_previous))
        }
        Text(stringResource(R.string.azkar_completion_note), style = MaterialTheme.typography.bodySmall)
    }
}
