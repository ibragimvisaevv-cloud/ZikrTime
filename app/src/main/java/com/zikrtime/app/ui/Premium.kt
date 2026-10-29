package com.zikrtime.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zikrtime.app.ui.theme.*

internal val EmeraldBright = Color(0xFF61734D)
internal val GoldLight = Color(0xFFF0D5A4)
internal val GoldDark = Color(0xFF956B3E)

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) = ArchitectureBackground(modifier)

/** Small tactile feedback; Compose respects the system animation duration scale. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

// Kept for older decorative call sites. Primary buttons animate their sheen on interaction.
fun Modifier.sheen(alpha: Float = 0.28f, periodMs: Int = 3_600): Modifier = this

@Composable
fun glassFill(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(listOf(scheme.surfaceContainer, scheme.surfaceContainerLow))
}

@Composable
fun glassBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))

@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), elevation: Dp = 10.dp): Modifier {
    val dark = LocalDarkTheme.current
    return this.shadow(if (dark) 0.dp else 2.dp, shape, ambientColor = Clay.copy(alpha = .08f), spotColor = Clay.copy(alpha = .12f))
        .clip(shape).background(glassFill()).border(glassBorder(), shape)
}

@Composable
fun GlassHero(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    MotifPanel(modifier = modifier, tone = SectionTone.Ink, content = content)
}

@Composable
fun ShimmerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sheen by animateFloatAsState(if (pressed) 1f else 0f, tween(500), label = "button-light")
    val shape = RoundedCornerShape(16.dp)
    val colors = if (enabled) listOf(Olive, Color(0xFF607147), Olive) else listOf(Color(0xFF7C8171), Color(0xFF7C8171))
    Button(onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp).pressScale(interaction).clip(shape)
            .background(Brush.horizontalGradient(colors))
            .drawWithContent {
                drawContent()
                if (sheen > .01f) {
                    val x = size.width * sheen
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Porcelain.copy(alpha = .12f), Color.Transparent),
                        start = Offset(x - size.width * .2f, 0f), end = Offset(x + size.width * .2f, size.height)))
                }
            }, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Porcelain,
            disabledContainerColor = Color.Transparent, disabledContentColor = Porcelain.copy(alpha = .7f))) {
        Text(text, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick = onClick, interactionSource = interaction,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp).pressScale(interaction), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Sand, contentColor = Color(0xFF382A18))) {
        Text(text, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
    }
}

@Composable
fun CategoryChip(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(scheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer)
    }
}

/** Anchored navigation with a sliding terracotta rule, like the chosen editorial concept. */
@Composable
fun <T> GlassNavBar(items: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier,
    item: @Composable (T, Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val position by animateFloatAsState(items.indexOf(selected).coerceAtLeast(0).toFloat(),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f), label = "nav-rule")
    Box(modifier.fillMaxWidth().background(scheme.surfaceContainer)) {
        Canvas(Modifier.matchParentSize()) {
            drawLine(scheme.outlineVariant, Offset.Zero, Offset(size.width, 0f), .8.dp.toPx())
            val width = size.width / items.size
            val x = (position + .5f) * width
            drawLine(scheme.secondary, Offset(x - 15.dp.toPx(), 1.dp.toPx()), Offset(x + 15.dp.toPx(), 1.dp.toPx()),
                3.dp.toPx(), cap = StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 6.dp, vertical = 8.dp)) {
            items.forEach { value ->
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.weight(1f).heightIn(min = 54.dp).pressScale(interaction, .95f)
                    .selectable(selected = value == selected, role = Role.Tab, interactionSource = interaction,
                        indication = ripple(), onClick = { onSelect(value) }), contentAlignment = Alignment.Center) {
                    item(value, value == selected)
                }
            }
        }
    }
}
