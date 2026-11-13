package com.zikrtime.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.AzkarContent
import com.zikrtime.app.data.AzkarStore
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.theme.ArabicStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AzkarScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val period = nav.azkarPeriod
    LaunchedEffect(period) { AzkarStore.start(period) }
    val items = remember(period) { AzkarContent.items(period) }
    val index = AzkarStore.index(period)
    val item = items[index]
    val count = AzkarStore.count(period, item)
    val ru = Lang.content(context) == "ru"
    var source by remember(period, item.id) { mutableStateOf(false) }
    var scale by rememberReadingScale()
    val title = stringResource(if (period == "evening") R.string.azkar_evening else R.string.azkar_morning)
    ScreenScaffold(title = title, onBack = { nav.back() }, showHeading = false,
        scrollKey = "$period-${item.id}", actions = { ReadingTools(scale) { scale = it } }) {
        LandscapePanel(if (period == "evening") R.drawable.azkar_evening else R.drawable.azkar_morning) {
            Column(Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.azkar_position, index + 1, items.size), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { (index + 1f) / items.size },
                    modifier = Modifier.fillMaxWidth().height(3.dp), color = Sand, trackColor = Porcelain.copy(alpha = .25f), drawStopIndicator = {})
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = period == "morning", onClick = { nav.openAzkar("morning") }, label = { Text(stringResource(R.string.azkar_morning)) })
            FilterChip(selected = period == "evening", onClick = { nav.openAzkar("evening") }, label = { Text(stringResource(R.string.azkar_evening)) })
        }
        Text(if (ru) item.titleRu else item.titleEn, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { source = true }, modifier = Modifier.testTag("azkar_source")) {
            Text(stringResource(R.string.azkar_source_button))
        }
        if (Lang.content(context) !in listOf("ru", "en"))
            Text(stringResource(R.string.source_translation, "English"), style = MaterialTheme.typography.labelMedium)
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text(item.arabic, modifier = Modifier.fillMaxWidth().testTag("azkar_arabic"),
                style = ArabicStyle.copy(fontSize = (32 * scale).sp, textDirection = TextDirection.Rtl),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(if (ru) item.transliterationRu else item.transliterationEn,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * scale).sp, lineHeight = (28 * scale).sp),
                color = MaterialTheme.colorScheme.secondary)
            Text(if (ru) item.meaningRu else item.meaningEn,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = (19 * scale).sp, lineHeight = (30 * scale).sp))
        }
        FilledTonalButton(onClick = {
            AzkarStore.increment(period, item)
            if (count + 1 == item.repetitions) Haptics.done(context) else Haptics.tick(context)
        }, enabled = count < item.repetitions,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("azkar_count")) {
            Text(if (!item.countIsPrescribed) stringResource(if (count > 0) R.string.azkar_read_done else R.string.azkar_mark_read)
                else stringResource(R.string.azkar_repetitions, count, item.repetitions))
        }
        PrimaryButton(stringResource(if (index < items.lastIndex) R.string.azkar_next else R.string.azkar_complete), onClick = {
            if (index < items.lastIndex) AzkarStore.move(period, index + 1)
            else { AzkarStore.finish(); nav.back() }
        }, modifier = Modifier.testTag("azkar_next"))
        OutlinedButton(onClick = { AzkarStore.move(period, index - 1) }, enabled = index > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("azkar_previous")) {
            Text(stringResource(R.string.azkar_previous))
        }
    }
    if (source) HorizonSheet(stringResource(R.string.azkar_source_button), { source = false }) {
        Text(item.sourceLabel, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.azkar_sources_note), style = MaterialTheme.typography.bodyMedium)
        if (!item.countIsPrescribed) Text(stringResource(R.string.azkar_single_read_note), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.azkar_translation_note), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))) }
                .onFailure { Toast.makeText(context, R.string.source_link_failed, Toast.LENGTH_SHORT).show() }
        }) { Text(stringResource(R.string.source_open_link)) }
    }
}
