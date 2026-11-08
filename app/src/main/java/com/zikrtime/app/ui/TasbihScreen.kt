package com.zikrtime.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.ui.theme.ArabicStyle
import com.zikrtime.app.ui.theme.Tabular
import com.zikrtime.app.widget.Widgets
import kotlinx.coroutines.delay

/** The counter shares the app's surfaces; the large touch field gives a pause its own rhythm. */
private data class TasbihPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentSurface: Color,
)

@Composable
fun TasbihScreen() {
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme) {
        TasbihPalette(
            background = scheme.background, surface = scheme.surfaceContainerLow,
            text = scheme.onSurface, muted = scheme.onSurfaceVariant, line = scheme.outlineVariant,
            accent = scheme.primary, accentSurface = scheme.primaryContainer,
        )
    }
    TasbihContent(palette)
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TasbihContent(palette: TasbihPalette) {
    StandardSystemBars()
    val nav = LocalNav.current
    val context = LocalContext.current
    val phrase = Store.tasbihPhrase.coerceIn(TASBIH_ARABIC.indices)
    val target = Store.tasbihTarget
    val count = Store.tasbihCount
    // Keep the completed interval visible on the final repetition. The saved total never wraps.
    val cycleCount = if (target > 0 && count > 0) (count - 1) % target + 1 else count
    val progress = if (target > 0) cycleCount.toFloat() / target else 0f
    val laps = if (target > 0) count / target else 0
    val completed = target > 0 && count > 0 && count % target == 0
    var showPhrases by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(0f) }
    var previousCount by remember { mutableIntStateOf(count) }
    val animate = motionEnabled()
    LaunchedEffect(count, phrase, animate) {
        val tapped = count > previousCount
        previousCount = count
        if (tapped && animate) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(180))
        } else pulse.snapTo(0f)
    }
    // Store.addTasbih persists each tap; only the expensive widget redraw is debounced.
    LaunchedEffect(count) {
        delay(300)
        Widgets.updateTasbih(context)
    }
    DisposableEffect(Unit) { onDispose { Widgets.updateTasbih(context) } }
    val latestTarget by rememberUpdatedState(target)
    val addOne = remember(context) {
        {
            Store.addTasbih()
            val current = Store.tasbihCount
            when {
                latestTarget > 0 && current % latestTarget == 0 -> Haptics.done(context)
                current % 33 == 0 -> Haptics.lap(context)
                else -> Haptics.tick(context)
            }
        }
    }

    Surface(color = palette.background, modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        Box {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                    Text(stringResource(R.string.tasbih_title), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showReset = true }, modifier = Modifier.testTag("tasbih_reset")) {
                        Icon(Icons.Default.Refresh, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_reset), color = palette.muted)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    TasbihCard(palette) {
                        TextButton(onClick = { showPhrases = true },
                            modifier = Modifier.fillMaxWidth().testTag("tasbih_phrase_open")) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(stringResource(R.string.tb_choose_phrase), style = MaterialTheme.typography.labelMedium,
                                    color = palette.muted)
                                Text(stringResource(TASBIH_NAMES[phrase].first), style = MaterialTheme.typography.titleMedium,
                                    color = palette.text)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("⌄", fontSize = 24.sp, color = palette.accent)
                        }
                        Text(TASBIH_ARABIC[phrase], style = ArabicStyle.copy(fontSize = 30.sp,
                            textDirection = TextDirection.Rtl), color = palette.text,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp))
                        Text(stringResource(TASBIH_NAMES[phrase].second), style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RhythmCounter(palette, cycleCount, target, progress, pulse.value, addOne)
                        if (completed) Text(stringResource(R.string.tb_cycle_complete),
                            color = palette.accent, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.tb_gesture_hint), style = MaterialTheme.typography.bodySmall,
                            color = palette.muted, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                    }
                    TasbihCard(palette) {
                        Text(stringResource(R.string.tasbih_target), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(33, 99, 100, 0).forEach { value ->
                                val label = if (value == 0) stringResource(R.string.tb_unlimited) else value.toString()
                                FilterChip(selected = target == value, onClick = { Store.tasbihTarget = value },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("tasbih_target_$value"))
                            }
                        }
                    }
                    TasbihTotals(palette, count, target, laps)
                }
            }
        }
    }

    if (showPhrases) HorizonSheet(stringResource(R.string.tb_choose_phrase), { showPhrases = false }) {
        Text(stringResource(R.string.tb_phrase_change_hint), style = MaterialTheme.typography.bodySmall,
            color = palette.muted)
        TASBIH_NAMES.forEachIndexed { index, (name, meaning) ->
            Surface(color = if (index == phrase) palette.accentSurface else palette.surface,
                shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, palette.line),
                modifier = Modifier.fillMaxWidth().testTag("tasbih_phrase_$index").clip(MaterialTheme.shapes.medium)
                    .selectable(selected = index == phrase, role = Role.RadioButton, onClick = {
                        if (index != phrase) { Store.tasbihPhrase = index; Store.tasbihCount = 0 }
                        showPhrases = false
                    })) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected = index == phrase, onClick = null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(meaning), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                    }
                }
            }
        }
    }
    if (showReset) HorizonDialog(onDismissRequest = { showReset = false },
        title = { Text(stringResource(R.string.action_reset)) },
        text = { Text(stringResource(R.string.counter_reset_confirmation)) },
        confirmButton = {
            TextButton(onClick = {
                Store.tasbihCount = 0
                Widgets.updateTasbih(context)
                showReset = false
            }, modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("tasbih_reset_confirm")) {
                Text(stringResource(R.string.action_reset))
            }
        }, dismissButton = {
            TextButton(onClick = { showReset = false },
                modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("tasbih_reset_cancel")) {
                Text(stringResource(R.string.action_close))
            }
        })
}

@Composable
private fun TasbihCard(palette: TasbihPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 620.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
    }
}

@Composable
private fun RhythmCounter(palette: TasbihPalette, count: Int, target: Int,
    progress: Float, pulse: Float, addOne: () -> Unit) {
    val label = stringResource(R.string.tasbih_title)
    val tapHint = stringResource(R.string.tasbih_tap_hint)
    val targetLabel = if (target > 0) stringResource(R.string.tasbih_of, target) else stringResource(R.string.tb_unlimited)
    val countDescription = "$count, $targetLabel"
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(min = 232.dp)
        .clip(MaterialTheme.shapes.large).background(palette.surface)
        .testTag("tasbih_counter")
        .pointerInput(addOne) {
            var dragged = 0f
            detectVerticalDragGestures(onDragStart = { dragged = 0f }, onVerticalDrag = { change, dy ->
                change.consume()
                dragged += dy
                while (dragged > 46.dp.toPx()) { dragged -= 46.dp.toPx(); addOne() }
            })
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = tapHint, onClick = addOne)
        .semantics { contentDescription = label; stateDescription = countDescription },
        contentAlignment = Alignment.Center) {
        val counterWidth = maxWidth
        Canvas(Modifier.matchParentSize()) {
            val inset = 24.dp.toPx()
            val trackWidth = (size.width - 2 * inset).coerceAtLeast(1f)
            val y = size.height - 24.dp.toPx()
            drawLine(palette.line, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
            if (target > 0) {
                drawLine(palette.accent, Offset(inset, y), Offset(inset + trackWidth * progress, y), 3.dp.toPx(), StrokeCap.Round)
                for (index in 0..10) {
                    val x = inset + trackWidth * index / 10f
                    drawLine(palette.line, Offset(x, y + 5.dp.toPx()), Offset(x, y + 9.dp.toPx()), 1.dp.toPx())
                }
                drawCircle(palette.accent, 4.dp.toPx() + pulse * 2.dp.toPx(), Offset(inset + trackWidth * progress, y))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 42.dp)
                .graphicsLayer { scaleX = 1f + pulse * .018f; scaleY = scaleX }) {
            val preferredSize = if (fontScale > 1.3f) 64f else 88f
            // Unlimited mode can have ten digits. Fit the complete value, never clip it.
            val numberSize = preferredSize.coerceAtMost((counterWidth.value - 48f) /
                (count.toString().length * .65f * fontScale))
            Text(count.toString(), modifier = Modifier.testTag("tasbih_count_value"),
                style = Tabular, fontSize = numberSize.sp, fontWeight = FontWeight.Medium,
                color = palette.text, maxLines = 1)
            Text(targetLabel, style = MaterialTheme.typography.bodyMedium, color = palette.muted,
                textAlign = TextAlign.Center)
            Text(tapHint, style = MaterialTheme.typography.labelLarge, color = palette.accent,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun TasbihTotals(palette: TasbihPalette, total: Int, target: Int, laps: Int) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    TasbihCard(palette) {
        if (largeText) {
            TasbihStat(stringResource(R.string.tb_total_count), total, "tasbih_total_value", palette)
            if (target > 0) TasbihStat(stringResource(R.string.tb_completed_laps), laps, "tasbih_laps_value", palette)
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                TasbihStat(stringResource(R.string.tb_total_count), total, "tasbih_total_value", palette)
            }
            if (target > 0) Column(Modifier.weight(1f)) {
                TasbihStat(stringResource(R.string.tb_completed_laps), laps, "tasbih_laps_value", palette)
            }
        }
        if (Store.personalProgressEnabled) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
            Text(stringResource(R.string.tasbih_today, Store.tasbihToday()),
                style = MaterialTheme.typography.bodyMedium, color = palette.muted,
                modifier = Modifier.padding(top = 4.dp).testTag("tasbih_today_value"))
        }
    }
}

@Composable
private fun TasbihStat(label: String, value: Int, tag: String, palette: TasbihPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = palette.muted)
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall.merge(Tabular),
            color = palette.text, modifier = Modifier.testTag(tag))
    }
}
