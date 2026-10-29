package com.zikrtime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.AzkarStore
import com.zikrtime.app.ui.theme.ArabicStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AzkarScreen() {
    val nav = LocalNav.current
    val period = nav.azkarPeriod
    LaunchedEffect(period) { AzkarStore.start(period) }
    val index = AzkarStore.pendingIndex
    var scale by rememberReadingScale()
    val title = stringResource(if (period == "evening") R.string.azkar_evening else R.string.azkar_morning)
    ScreenScaffold(title = title, onBack = { nav.back() }, showHeading = false,
        scrollKey = "$period-$index", actions = { ReadingTools(scale) { scale = it } }) {
        LandscapePanel(if (period == "evening") R.drawable.azkar_evening else R.drawable.azkar_morning) {
            Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.azkar_position, index + 1, TASBIH_ARABIC.size), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { (index + 1f) / TASBIH_ARABIC.size },
                    modifier = Modifier.fillMaxWidth().height(3.dp), color = Sand, trackColor = Porcelain.copy(alpha = .25f), drawStopIndicator = {})
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = period == "morning", onClick = { nav.openAzkar("morning") }, label = { Text(stringResource(R.string.azkar_morning)) })
            FilterChip(selected = period == "evening", onClick = { nav.openAzkar("evening") }, label = { Text(stringResource(R.string.azkar_evening)) })
        }
        // Quiet reading surface: Arabic, transcription and meaning have separate visual roles.
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(TASBIH_ARABIC[index], modifier = Modifier.fillMaxWidth(),
                style = ArabicStyle.copy(fontSize = (34 * scale).sp, lineHeight = (60 * scale).sp, textDirection = TextDirection.Rtl),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(TASBIH_NAMES[index].first), style = MaterialTheme.typography.titleMedium.copy(fontSize = (18 * scale).sp),
                color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(TASBIH_NAMES[index].second), style = MaterialTheme.typography.bodyLarge.copy(fontSize = (19 * scale).sp, lineHeight = (30 * scale).sp))
        }
        PrimaryButton(stringResource(if (index < TASBIH_ARABIC.lastIndex) R.string.azkar_next else R.string.azkar_complete), onClick = {
            if (index < TASBIH_ARABIC.lastIndex) AzkarStore.move(index + 1)
            else { AzkarStore.finish(); nav.back() }
        })
        OutlinedButton(onClick = { AzkarStore.move(index - 1) }, enabled = index > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.azkar_previous))
        }
        Text(stringResource(R.string.azkar_completion_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
