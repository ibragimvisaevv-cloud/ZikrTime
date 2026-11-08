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

internal val EmeraldBright = Color(0xFF3157C8)
internal val GoldLight = Color(0xFFE7AB96)
internal val GoldDark = Color(0xFFA4523B)

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) = HorizonBackground(modifier)

/** Small tactile feedback; Compose respects the system animation duration scale. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val motion = motionEnabled()
    val scale by animateFloatAsState(if (pressed && motion) pressedScale else 1f,
        tween(if (motion) RhythmMotion.Press else 0, easing = FastOutSlowInEasing), label = "press")
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
    return this.clip(shape).background(glassFill()).border(glassBorder(), shape)
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

/** Stable navigation rail at the bottom; selection has an underline, icon and label. */
@Composable
fun <T> GlassNavBar(items: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier,
    item: @Composable (T, Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(scheme.surfaceContainerLow)) {
        HorizontalDivider(color = scheme.outlineVariant)
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 8.dp, vertical = 8.dp)) {
            items.forEach { value ->
                val interaction = remember { MutableInteractionSource() }
                Column(Modifier.weight(1f).heightIn(min = 60.dp).pressScale(interaction, .98f)
                    .clip(MaterialTheme.shapes.small)
                    .selectable(selected = value == selected, role = Role.Tab, interactionSource = interaction,
                        indication = ripple(), onClick = { onSelect(value) }).padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    item(value, value == selected)
                    Box(Modifier.width(20.dp).height(3.dp).background(
                        if (value == selected) scheme.primary else Color.Transparent, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
