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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zikrtime.app.ui.theme.*

internal val EmeraldBright = Color(0xFF397C60)
internal val GoldLight = Color(0xFFF0D5A4)
internal val GoldDark = Color(0xFF956B3E)

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) = HorizonBackground(modifier)

/** Small tactile feedback; Compose respects the system animation duration scale. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val motion = motionEnabled()
    val scale by animateFloatAsState(if (pressed && motion) pressedScale else 1f,
        tween(if (motion) 140 else 0, easing = FastOutSlowInEasing), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

// Retained for older call sites; decorative highlights remain static.
fun Modifier.sheen(alpha: Float = 0.28f, periodMs: Int = 3_600): Modifier = this

@Composable
fun glassFill(): Brush {
    val scheme = MaterialTheme.colorScheme
    return SolidColor(scheme.surfaceContainerLow)
}

@Composable
fun glassBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .8f))

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
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp).pressScale(interaction),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick = onClick, interactionSource = interaction,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp).pressScale(interaction), shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
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

/** The same neutral surface as the cards, with one clear selected destination. */
@Composable
fun <T> GlassNavBar(items: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier,
    item: @Composable (T, Boolean) -> Unit) {
    val motion = motionEnabled()
    val position by animateFloatAsState(items.indexOf(selected).coerceAtLeast(0).toFloat(),
        tween(if (motion) 220 else 0, easing = FastOutSlowInEasing), label = "nav-selection")
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .shadow(6.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
        .clip(RoundedCornerShape(26.dp))
        .background(scheme.surfaceContainerLow)
        .border(1.dp, scheme.outlineVariant.copy(alpha = .8f), RoundedCornerShape(26.dp))) {
        Canvas(Modifier.matchParentSize()) {
            val width = (size.width - 12.dp.toPx()) / items.size
            drawRoundRect(scheme.primaryContainer,
                topLeft = Offset(6.dp.toPx() + position * width + 2.dp.toPx(), 7.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(width - 4.dp.toPx(), size.height - 14.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(19.dp.toPx()))
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
