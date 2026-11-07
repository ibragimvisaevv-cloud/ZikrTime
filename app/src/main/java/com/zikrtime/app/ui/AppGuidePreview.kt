package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R

/**
 * Compact, labelled screen diagrams rather than screenshots of somebody's data.
 * Drawn locally, they follow the active palette and font scale without media files,
 * network requests or continuous animation. The numbered labels are real controls.
 */
@Composable
internal fun AppGuidePreview(topic: GuideTopic, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(topic.icon), contentDescription = null,
                    tint = scheme.secondary, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.guide_preview_label),
                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            GuideDiagram(topic, Modifier.fillMaxWidth().height(if (LocalDensity.current.fontScale > 1.3f) 72.dp else 96.dp))
            topic.controls.forEachIndexed { index, label ->
                Surface(shape = RoundedCornerShape(13.dp),
                    color = if (index == 0) scheme.primaryContainer else scheme.surface,
                    border = BorderStroke(1.dp, if (index == 0) scheme.primary.copy(alpha = .30f) else scheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GuideNumber(index + 1)
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                            color = if (index == 0) scheme.onPrimaryContainer else scheme.onSurface,
                            modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideDiagram(topic: GuideTopic, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val accent = colors.secondary
    val faint = colors.outlineVariant
    val fill = colors.primaryContainer
    val paper = colors.surface
    val soft = colors.secondaryContainer
    val frame = RoundedCornerShape(16.dp)
    Box(modifier.background(Brush.horizontalGradient(listOf(fill.copy(alpha = .45f), soft.copy(alpha = .5f))), frame),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.dp.toPx()
            val round = CornerRadius(5.dp.toPx())
            when (topic) {
                GuideTopic.WELCOME, GuideTopic.PRAYER -> {
                    // The home screen's landscape and a short schedule, without invented times.
                    val arch = Path().apply {
                        moveTo(w * .08f, h * .9f)
                        lineTo(w * .08f, h * .56f)
                        cubicTo(w * .08f, h * .18f, w * .23f, h * .12f, w * .27f, 0f)
                        cubicTo(w * .31f, h * .12f, w * .46f, h * .18f, w * .46f, h * .56f)
                        lineTo(w * .46f, h * .9f)
                    }
                    drawPath(arch, accent.copy(alpha = .65f), style = Stroke(stroke))
                    drawCircle(soft, h * .12f, Offset(w * .27f, h * .40f))
                    repeat(4) { i ->
                        drawRoundRect(if (i == 0) primary.copy(alpha = .65f) else faint,
                            Offset(w * .58f, h * (.12f + i * .22f)), Size(w * (.32f - i * .035f), h * .07f), round)
                    }
                }
                GuideTopic.JOURNAL -> {
                    // Calendar cells are an illustration, not the user's prayer history.
                    val cellW = w * .105f
                    val cellH = h * .22f
                    repeat(3) { row ->
                        repeat(5) { col ->
                            drawRoundRect(if (row == 0 && col == 2) soft else paper,
                                Offset(w * .19f + col * cellW * 1.18f, h * .06f + row * cellH * 1.35f),
                                Size(cellW, cellH), round)
                        }
                    }
                    drawLine(accent, Offset(w * .19f, h * .01f), Offset(w * .78f, h * .01f), stroke, StrokeCap.Round)
                }
                GuideTopic.QURAN -> {
                    // An open book, with abstract rules instead of manufactured Arabic.
                    val left = Path().apply {
                        moveTo(w * .5f, h * .14f)
                        quadraticTo(w * .32f, -h * .04f, w * .16f, h * .1f)
                        lineTo(w * .16f, h * .84f)
                        quadraticTo(w * .32f, h * .70f, w * .5f, h * .93f)
                        close()
                    }
                    val right = Path().apply {
                        moveTo(w * .5f, h * .14f)
                        quadraticTo(w * .68f, -h * .04f, w * .84f, h * .1f)
                        lineTo(w * .84f, h * .84f)
                        quadraticTo(w * .68f, h * .70f, w * .5f, h * .93f)
                        close()
                    }
                    drawPath(left, paper); drawPath(right, paper)
                    drawPath(left, accent, style = Stroke(stroke)); drawPath(right, accent, style = Stroke(stroke))
                    repeat(4) { i ->
                        val y = h * (.29f + .12f * i)
                        drawLine(faint, Offset(w * .23f, y), Offset(w * .43f, y + h * .04f), stroke, StrokeCap.Round)
                        drawLine(faint, Offset(w * .57f, y + h * .04f), Offset(w * .77f, y), stroke, StrokeCap.Round)
                    }
                }
                GuideTopic.LIMITS -> {
                    val center = Offset(w * .31f, h * .5f)
                    val radius = h * .40f
                    drawCircle(faint, radius, center, style = Stroke(stroke * 2))
                    drawArc(primary, -90f, 245f, false, Offset(center.x - radius, center.y - radius),
                        Size(radius * 2, radius * 2), style = Stroke(stroke * 2, cap = StrokeCap.Round))
                    repeat(3) { i ->
                        drawRoundRect(if (i == 0) soft else paper,
                            Offset(w * .59f, h * (.08f + i * .31f)), Size(w * .24f, h * .22f), round)
                    }
                }
                GuideTopic.PERMISSIONS -> {
                    val phoneW = h * .55f
                    val x = w * .28f - phoneW / 2
                    drawRoundRect(paper, Offset(x, 0f), Size(phoneW, h), round)
                    drawRoundRect(primary.copy(alpha = .6f), Offset(x, 0f), Size(phoneW, h), round, style = Stroke(stroke))
                    drawLine(faint, Offset(x + phoneW * .28f, h * .89f),
                        Offset(x + phoneW * .72f, h * .89f), stroke, StrokeCap.Round)
                    repeat(3) { i ->
                        val y = h * (.19f + i * .29f)
                        drawCircle(accent.copy(alpha = .45f), h * .065f, Offset(w * .54f, y))
                        drawLine(faint, Offset(w * .61f, y), Offset(w * .85f, y), stroke * 2, StrokeCap.Round)
                    }
                }
                GuideTopic.KNOWLEDGE -> {
                    drawRoundRect(soft, Offset(w * .20f, h * .02f), Size(w * .60f, h * .83f), round)
                    drawRoundRect(paper, Offset(w * .16f, h * .13f), Size(w * .60f, h * .83f), round)
                    drawLine(primary.copy(alpha = .6f), Offset(w * .24f, h * .35f),
                        Offset(w * .64f, h * .35f), stroke * 2, StrokeCap.Round)
                    repeat(2) { i ->
                        drawCircle(faint, h * .045f, Offset(w * .25f, h * (.58f + i * .20f)))
                        drawLine(faint, Offset(w * .31f, h * (.58f + i * .20f)),
                            Offset(w * .60f, h * (.58f + i * .20f)), stroke, StrokeCap.Round)
                    }
                }
                GuideTopic.PLACES -> {
                    // Abstract streets intentionally carry no geographic claim.
                    drawLine(paper, Offset(0f, h * .8f), Offset(w, h * .22f), h * .17f)
                    drawLine(paper, Offset(w * .21f, 0f), Offset(w * .48f, h), h * .13f)
                    drawLine(paper, Offset(w * .65f, 0f), Offset(w * .80f, h), h * .13f)
                    listOf(Offset(w * .32f, h * .37f), Offset(w * .69f, h * .55f)).forEach { center ->
                        drawCircle(soft, h * .19f, center)
                        drawCircle(primary, h * .085f, center)
                    }
                }
                GuideTopic.TOOLS -> {
                    val center = Offset(w * .5f, h * .5f)
                    val radius = h * .43f
                    drawCircle(accent.copy(alpha = .6f), radius, center, style = Stroke(stroke))
                    drawCircle(faint, radius * .75f, center, style = Stroke(stroke))
                    val needle = Path().apply {
                        moveTo(center.x, h * .14f)
                        lineTo(center.x + h * .12f, h * .66f)
                        lineTo(center.x, h * .56f)
                        lineTo(center.x - h * .12f, h * .66f)
                        close()
                    }
                    drawPath(needle, primary)
                }
                GuideTopic.SHOP -> {
                    repeat(3) { i ->
                        val x = w * (.12f + i * .27f)
                        drawRoundRect(if (i == 0) soft else paper, Offset(x, h * .08f), Size(w * .22f, h * .84f), round)
                        drawRoundRect(primary.copy(alpha = if (i == 0) .25f else .10f),
                            Offset(x + w * .03f, h * .19f), Size(w * .16f, h * .38f), round)
                        drawLine(faint, Offset(x + w * .03f, h * .72f), Offset(x + w * .15f, h * .72f), stroke, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

