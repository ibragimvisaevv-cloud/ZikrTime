package com.zikrtime.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import com.zikrtime.app.ui.theme.LocalDarkTheme
import com.zikrtime.app.ui.theme.Tabular
import com.zikrtime.app.widget.Widgets
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** A quiet cloth-and-wood setting, kept separate from the app's green navigation theme. */
private data class TasbihMaterials(
    val cloth: Color,
    val paper: Color,
    val raised: Color,
    val ink: Color,
    val muted: Color,
    val edge: Color,
    val amber: Color,
    val amberWash: Color,
    val onAmber: Color,
)

@Composable
fun TasbihScreen() {
    val dark = LocalDarkTheme.current
    val materials = remember(dark) {
        if (dark) TasbihMaterials(
            cloth = Color(0xFF191816), paper = Color(0xFF25231F), raised = Color(0xFF302C26),
            ink = Color(0xFFF2E8D6), muted = Color(0xFFC0B29D), edge = Color(0xFF514635),
            amber = Color(0xFFE4B977), amberWash = Color(0xFF493A27), onAmber = Color(0xFF332313),
        ) else TasbihMaterials(
            cloth = Color(0xFFF1EADC), paper = Color(0xFFFFFBF1), raised = Color(0xFFE9DECB),
            ink = Color(0xFF30291F), muted = Color(0xFF70614D), edge = Color(0xFFD8C8AA),
            amber = Color(0xFF8B571E), amberWash = Color(0xFFF0DFBF), onAmber = Color(0xFFFFF8EA),
        )
    }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = materials.amber, onPrimary = materials.onAmber,
        primaryContainer = materials.amberWash, onPrimaryContainer = materials.ink,
        secondary = materials.amber, onSecondary = materials.onAmber,
        secondaryContainer = materials.amberWash, onSecondaryContainer = materials.ink,
        background = materials.cloth, onBackground = materials.ink,
        surface = materials.paper, onSurface = materials.ink, onSurfaceVariant = materials.muted,
        surfaceVariant = materials.raised, surfaceContainerLow = materials.paper,
        surfaceContainer = materials.paper, surfaceContainerHigh = materials.raised,
        surfaceContainerHighest = materials.raised, outline = materials.muted, outlineVariant = materials.edge,
    )) {
        TasbihContent(materials, dark)
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TasbihContent(materials: TasbihMaterials, dark: Boolean) {
    StandardSystemBars()
    val nav = LocalNav.current
    val context = LocalContext.current
    val phrase = Store.tasbihPhrase.coerceIn(TASBIH_ARABIC.indices)
    val target = Store.tasbihTarget
    val count = Store.tasbihCount
    // Keep the full circle visible on the final repetition. The saved total never wraps.
    val cycleCount = if (target > 0 && count > 0) (count - 1) % target + 1 else count
    val progress = if (target > 0) cycleCount.toFloat() / target else 0f
    val laps = if (target > 0) count / target else 0
    val completed = target > 0 && count > 0 && count % target == 0
    var showPhrases by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    val travel = remember { BeadTravel(count, phrase) }
    val turn = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    var previousCount by remember { mutableIntStateOf(count) }
    val animate = motionEnabled()
    LaunchedEffect(count, phrase, animate) {
        val reset = travel.update(count, phrase)
        val destination = -travel.steps * (360f / 33f)
        if (reset || !animate) turn.snapTo(destination)
        else turn.animateTo(destination, tween(230, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(count, phrase, animate) {
        val tapped = count > previousCount
        previousCount = count
        if (tapped && animate) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(360))
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

    Surface(color = materials.cloth, modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        Box {
            LinenWeave(materials, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                    Text(stringResource(R.string.tasbih_title), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showReset = true }, modifier = Modifier.testTag("tasbih_reset")) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.action_reset), tint = materials.muted)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TasbihCard(materials) {
                        TextButton(onClick = { showPhrases = true },
                            modifier = Modifier.fillMaxWidth().testTag("tasbih_phrase_open")) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(stringResource(R.string.tb_choose_phrase), style = MaterialTheme.typography.labelMedium,
                                    color = materials.muted)
                                Text(stringResource(TASBIH_NAMES[phrase].first), style = MaterialTheme.typography.titleMedium,
                                    color = materials.ink)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("⌄", fontSize = 24.sp, color = materials.amber)
                        }
                        Text(TASBIH_ARABIC[phrase], style = ArabicStyle.copy(fontSize = 30.sp,
                            textDirection = TextDirection.Rtl), color = materials.ink,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp))
                        Text(stringResource(TASBIH_NAMES[phrase].second), style = MaterialTheme.typography.bodyMedium,
                            color = materials.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BeadCounter(materials, dark, cycleCount, target, progress, turn.value, pulse.value, addOne)
                        if (completed) Text(stringResource(R.string.tb_cycle_complete),
                            color = materials.amber, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.tb_gesture_hint), style = MaterialTheme.typography.bodySmall,
                            color = materials.muted, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                    }
                    TasbihCard(materials) {
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
                    TasbihTotals(materials, count, target, laps)
                }
            }
        }
    }

    if (showPhrases) HorizonSheet(stringResource(R.string.tb_choose_phrase), { showPhrases = false }) {
        Text(stringResource(R.string.tb_phrase_change_hint), style = MaterialTheme.typography.bodySmall,
            color = materials.muted)
        TASBIH_NAMES.forEachIndexed { index, (name, meaning) ->
            Surface(color = if (index == phrase) materials.amberWash else materials.paper,
                shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, materials.edge),
                modifier = Modifier.fillMaxWidth().testTag("tasbih_phrase_$index").clip(RoundedCornerShape(18.dp))
                    .selectable(selected = index == phrase, role = Role.RadioButton, onClick = {
                        if (index != phrase) { Store.tasbihPhrase = index; Store.tasbihCount = 0 }
                        showPhrases = false
                    })) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected = index == phrase, onClick = null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(meaning), style = MaterialTheme.typography.bodyMedium, color = materials.muted)
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
private fun TasbihCard(materials: TasbihMaterials, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), color = materials.paper,
        shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, materials.edge)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun LinenWeave(materials: TasbihMaterials, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val spacing = 7.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(materials.ink.copy(alpha = .025f), Offset(x, 0f), Offset(x, size.height), .5.dp.toPx())
            x += spacing
        }
        var y = 0f
        while (y < size.height) {
            drawLine(materials.ink.copy(alpha = .025f), Offset(0f, y), Offset(size.width, y), .5.dp.toPx())
            y += spacing
        }
    }
}

@Composable
private fun BeadCounter(materials: TasbihMaterials, dark: Boolean, count: Int, target: Int,
    progress: Float, turn: Float, pulse: Float, addOne: () -> Unit) {
    val label = stringResource(R.string.tasbih_title)
    val tapHint = stringResource(R.string.tasbih_tap_hint)
    val targetLabel = if (target > 0) stringResource(R.string.tasbih_of, target) else stringResource(R.string.tb_unlimited)
    val countDescription = "$count, $targetLabel"
    val fontScale = LocalDensity.current.fontScale
    // Leave a usable viewport in landscape instead of making a full-width, unscrollable gesture area.
    val counterSize = minOf(340.dp, (LocalConfiguration.current.screenHeightDp.dp - 104.dp).coerceAtLeast(200.dp))
    BoxWithConstraints(Modifier.widthIn(max = counterSize).fillMaxWidth().aspectRatio(1f)
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
        Canvas(Modifier.fillMaxSize()) {
            val origin = Offset(size.width / 2f, size.height * .46f)
            val radius = size.minDimension * .385f
            val beadRadius = radius * .078f
            val inner = radius * .76f
            drawCircle(materials.ink.copy(alpha = if (dark) .05f else .035f), radius * 1.12f, origin)
            drawCircle(materials.edge, radius, origin, style = Stroke(1.2.dp.toPx()))
            // Shaded wooden beads are drawn natively, so they stay sharp at every density.
            for (index in 0 until 33) {
                val angle = Math.toRadians(index * 360.0 / 33 + turn - 90)
                val bead = Offset(origin.x + radius * cos(angle).toFloat(), origin.y + radius * sin(angle).toFloat())
                val active = target == 0 || index < progress * 33
                val light = if (active) Color(0xFFE8BB78) else if (dark) Color(0xFFA47B4C) else Color(0xFFB68A58)
                val middle = if (active) Color(0xFFAC7238) else Color(0xFF82582F)
                drawCircle(Color.Black.copy(alpha = if (dark) .35f else .16f), beadRadius * 1.12f,
                    bead + Offset(.5.dp.toPx(), 2.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(light, middle, Color(0xFF4B2D18)),
                    center = bead - Offset(beadRadius * .36f, beadRadius * .42f), radius = beadRadius * 1.55f),
                    beadRadius, bead)
                drawCircle(Color(0xFFFFEAC5).copy(alpha = if (active) .62f else .3f), beadRadius * .18f,
                    bead - Offset(beadRadius * .35f, beadRadius * .42f))
            }
            drawCircle(Brush.radialGradient(listOf(materials.paper, materials.raised),
                center = origin - Offset(inner * .2f, inner * .3f), radius = inner * 1.7f), inner, origin)
            drawCircle(materials.edge, inner, origin, style = Stroke(1.dp.toPx()))
            val ring = inner - 7.dp.toPx()
            drawCircle(materials.edge.copy(alpha = .55f), ring, origin, style = Stroke(2.dp.toPx()))
            if (target > 0) drawArc(materials.amber, -90f, progress * 360f, false,
                topLeft = origin - Offset(ring, ring), size = Size(ring * 2, ring * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            if (pulse > 0f) drawCircle(materials.amber.copy(alpha = pulse * .06f), inner, origin)
            // The imame and a short tassel give the counter the silhouette of prayer beads.
            val tailTop = origin.y + radius + beadRadius * 1.25f
            val tailBottom = size.height * .945f
            drawLine(materials.amber, Offset(origin.x, tailTop), Offset(origin.x, tailBottom),
                3.dp.toPx(), cap = StrokeCap.Round)
            drawOval(Brush.verticalGradient(listOf(Color(0xFFD0A367), Color(0xFF6E4624))),
                Offset(origin.x - 4.dp.toPx(), tailTop + 2.dp.toPx()), Size(8.dp.toPx(), 13.dp.toPx()))
            for (strand in -2..2) drawLine(materials.amber.copy(alpha = .65f),
                Offset(origin.x, tailBottom - 2.dp.toPx()),
                Offset(origin.x + strand * 3.dp.toPx(), size.height * .985f),
                1.dp.toPx(), cap = StrokeCap.Round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(.52f).padding(bottom = maxWidth * .08f)
                .graphicsLayer { scaleX = 1f + pulse * .025f; scaleY = scaleX }) {
            val preferredSize = if (fontScale > 1.3f) 44f else 58f
            // Infinite mode can have ten digits. Fit the number without cutting off its end.
            val numberSize = preferredSize.coerceAtMost(maxWidth.value * .49f /
                (count.toString().length * .65f * fontScale))
            Text(count.toString(), modifier = Modifier.testTag("tasbih_count_value"),
                style = Tabular, fontSize = numberSize.sp, fontWeight = FontWeight.Medium,
                color = materials.ink, maxLines = 1)
            Text(targetLabel, style = MaterialTheme.typography.bodySmall, color = materials.muted,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TasbihTotals(materials: TasbihMaterials, total: Int, target: Int, laps: Int) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    TasbihCard(materials) {
        if (largeText) {
            TasbihStat(stringResource(R.string.tb_total_count), total, "tasbih_total_value", materials)
            if (target > 0) TasbihStat(stringResource(R.string.tb_completed_laps), laps, "tasbih_laps_value", materials)
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                TasbihStat(stringResource(R.string.tb_total_count), total, "tasbih_total_value", materials)
            }
            if (target > 0) Column(Modifier.weight(1f)) {
                TasbihStat(stringResource(R.string.tb_completed_laps), laps, "tasbih_laps_value", materials)
            }
        }
        if (Store.personalProgressEnabled) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(materials.edge))
            Text(stringResource(R.string.tasbih_today, Store.tasbihToday()),
                style = MaterialTheme.typography.bodyMedium, color = materials.muted,
                modifier = Modifier.padding(top = 4.dp).testTag("tasbih_today_value"))
        }
    }
}

@Composable
private fun TasbihStat(label: String, value: Int, tag: String, materials: TasbihMaterials) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = materials.muted)
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall.merge(Tabular),
            color = materials.ink, modifier = Modifier.testTag(tag))
    }
}
