package com.zikrtime.app.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zikrtime.app.ui.theme.LocalDarkTheme
import kotlin.math.cos
import kotlin.math.sin

/** The same plaster, pointed arches and eight-point tile geometry throughout the app. */
val Clay = Color(0xFFA54E35)
val Olive = Color(0xFF3D5037)
val Sand = Color(0xFFE2BE82)
val Porcelain = Color(0xFFFFF5E4)

/** Motion stops in the background and honours Android's animation setting. */
@Composable
private fun architectureDrift(): Float {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var enabled by remember { mutableStateOf(true) }
    DisposableEffect(lifecycle, context) {
        fun refresh() {
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            enabled = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
        val observer = LifecycleEventObserver { _, _ -> refresh() }
        lifecycle.addObserver(observer)
        refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!active || !enabled) return .5f
    val drift by rememberInfiniteTransition(label = "courtyard-light").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "light-position",
    )
    return drift
}

internal fun pointedArch(cx: Float, top: Float, width: Float, bottom: Float): Path {
    val r = width / 2f
    return Path().apply {
        moveTo(cx - r, bottom)
        lineTo(cx - r, top + r * 1.5f)
        cubicTo(cx - r, top + r * .6f, cx - r * .32f, top + r * .35f, cx, top)
        cubicTo(cx + r * .32f, top + r * .35f, cx + r, top + r * .6f, cx + r, top + r * 1.5f)
        lineTo(cx + r, bottom)
        close()
    }
}

internal fun DrawScope.tileStar(center: Offset, radius: Float, color: Color) {
    repeat(2) { square ->
        val path = Path()
        repeat(4) { i ->
            val angle = Math.toRadians((i * 90 + square * 45).toDouble())
            val x = center.x + radius * cos(angle).toFloat()
            val y = center.y + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color, style = Stroke(.7.dp.toPx()))
    }
}

/** Light on carved plaster, kept to the edges so it never competes with labels. */
@Composable
fun ArchitectureArt(
    modifier: Modifier = Modifier,
    color: Color = Sand,
    motion: Boolean = false,
    evening: Boolean = false,
) {
    val drift = if (motion) architectureDrift() else .5f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w * .88f
        // Nested pointed arches and their recessed shadows.
        for (i in 0..2) {
            val width = w * (.54f - i * .11f)
            val top = h * (-.15f + i * .16f)
            val path = pointedArch(cx, top, width, h * 1.2f)
            drawPath(path, color.copy(alpha = .11f + .035f * i))
            drawPath(path, color.copy(alpha = .24f), style = Stroke(1.dp.toPx()))
        }
        // One broad, slowly moving diagonal ray. Text is never animated.
        val start = w * (.44f + .12f * drift)
        val light = Path().apply {
            moveTo(start, 0f); lineTo(start + w * .12f, 0f)
            lineTo(start + w * .42f, h); lineTo(start + w * .22f, h); close()
        }
        drawPath(light, Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = .09f), Color.Transparent)))
        // A narrow mashrabiya-style band, not a full-page wallpaper.
        val step = 28.dp.toPx()
        var y = step / 2f
        while (y < h) {
            tileStar(Offset(w - step * .65f, y), step * .38f, color.copy(alpha = .22f))
            y += step
        }
        if (evening) {
            val r = minOf(w, h) * .08f
            val moon = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, h * .22f - r, cx + r, h * .22f + r)) }
            val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r * .35f, h * .22f - r * 1.25f, cx + r * 1.35f, h * .22f + r * .45f)) }
            drawPath(Path.combine(PathOperation.Difference, moon, cut), color.copy(alpha = .55f))
        }
    }
}

@Composable
fun ArchitectureBackground(modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(
                listOf(Clay.copy(alpha = if (dark) .13f else .09f), Color.Transparent),
                center = Offset(size.width * 1.15f, size.height * .15f), radius = size.width * 1.15f))
            drawRect(Brush.radialGradient(
                listOf(Sand.copy(alpha = if (dark) .045f else .13f), Color.Transparent),
                center = Offset(-size.width * .25f, size.height * .75f), radius = size.width))
        }
    }
}

/** Time-of-day tint is independent from the user's light/dark appearance choice. */
@Composable
fun CourtyardPanel(period: NaturalPeriod, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDarkTheme.current
    val target = when (period) {
        NaturalPeriod.DAWN -> if (dark) Color(0xFF4A342C) else Color(0xFFF1D8B4)
        NaturalPeriod.DAY -> if (dark) Color(0xFF343D2D) else Color(0xFFECE3C6)
        NaturalPeriod.SUNSET -> if (dark) Color(0xFF563329) else Color(0xFFEFC4A6)
        NaturalPeriod.NIGHT -> if (dark) Color(0xFF30372D) else Color(0xFFE2E4D3)
    }
    val color by animateColorAsState(target, tween(1400), label = "courtyard-period")
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 44.dp, bottomEnd = 12.dp, bottomStart = 12.dp))
        .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = .8f))))) {
        ArchitectureArt(Modifier.matchParentSize(), color = if (dark) Sand else Clay, motion = true,
            evening = period == NaturalPeriod.NIGHT)
        content()
    }
}
