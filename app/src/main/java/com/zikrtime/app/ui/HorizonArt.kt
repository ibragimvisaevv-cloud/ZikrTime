package com.zikrtime.app.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zikrtime.app.R
import com.zikrtime.app.ui.theme.LocalDarkTheme
import kotlin.math.cos
import kotlin.math.sin

// Shared accents; retained names keep secondary surfaces in the same palette.
val Clay = Color(0xFF8B663D)
val Olive = Color(0xFF124C40)
val Sand = Color(0xFFE2C58F)
val Porcelain = Color(0xFFFFF9ED)

/** Movement pauses off screen and follows the Android animation preference. */
@Composable
private fun horizonDrift(): Float {
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
    val value by rememberInfiniteTransition(label = "horizon-light").animateFloat(
        0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "cloud-light")
    return value
}

/** A local panorama, with a separate night exposure. No network image dependency. */
@Composable
fun HorizonLandscape(period: NaturalPeriod, modifier: Modifier = Modifier, motion: Boolean = false) {
    val drift = if (motion) horizonDrift() else .5f
    val tint by animateColorAsState(when (period) {
        NaturalPeriod.DAWN -> Color(0x20E9BB83)
        NaturalPeriod.DAY -> Color(0x152C7D72)
        NaturalPeriod.SUNSET -> Color(0x45B36443)
        NaturalPeriod.NIGHT -> Color(0x20071925)
    }, tween(1400), label = "horizon-period")
    Box(modifier.clip(RoundedCornerShape(26.dp))) {
        Image(painterResource(if (period == NaturalPeriod.NIGHT) R.drawable.horizon_night else R.drawable.horizon_dawn),
            null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Canvas(Modifier.matchParentSize()) {
            drawRect(tint)
            if (motion) drawRect(Brush.radialGradient(
                listOf(Sand.copy(alpha = if (period == NaturalPeriod.NIGHT) .035f else .12f), Color.Transparent),
                center = Offset(size.width * (.1f + .65f * drift), size.height * .25f), radius = size.width * .6f))
            // Fixed scrims protect text contrast throughout the animation.
            drawRect(Brush.horizontalGradient(listOf(Color(0x99051D18), Color(0x20051D18))))
            drawRect(Brush.verticalGradient(listOf(Color(0x55051D18), Color.Transparent, Color(0xD9051D18))))
        }
    }
}

@Composable
fun HorizonBackground(modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Olive.copy(alpha = if (dark) .18f else .06f), Color.Transparent),
                center = Offset(size.width, size.height * .15f), radius = size.width))
            drawRect(Brush.radialGradient(listOf(Sand.copy(alpha = if (dark) .035f else .08f), Color.Transparent),
                center = Offset(0f, size.height * .7f), radius = size.width))
        }
    }
}

/** Quiet mosque silhouette and a small geometric band for text-heavy secondary panels. */
@Composable
fun HorizonSilhouette(modifier: Modifier = Modifier, color: Color = Sand) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val base = h * .98f; val cx = w * .86f; val r = minOf(w * .16f, h * .45f)
        val path = Path().apply {
            moveTo(cx - r, base); lineTo(cx - r, base - r * .65f)
            cubicTo(cx - r, base - r * 1.35f, cx - r * .3f, base - r * 1.35f, cx, base - r * 1.7f)
            cubicTo(cx + r * .3f, base - r * 1.35f, cx + r, base - r * 1.35f, cx + r, base - r * .65f)
            lineTo(cx + r, base); close()
        }
        drawPath(path, color.copy(alpha = .12f))
        for (x in listOf(cx - r * 1.3f, cx + r * 1.3f)) {
            val tower = Path().apply {
                moveTo(x - r * .1f, base); lineTo(x - r * .1f, base - r * 1.65f)
                lineTo(x, base - r * 2.05f); lineTo(x + r * .1f, base - r * 1.65f)
                lineTo(x + r * .1f, base); close()
            }
            drawPath(tower, color.copy(alpha = .14f))
        }
        val star = Path()
        repeat(2) { square ->
            repeat(4) { i ->
                val a = Math.toRadians((i * 90 + square * 45).toDouble())
                val x = w - 20.dp.toPx() + 9.dp.toPx() * cos(a).toFloat()
                val y = 20.dp.toPx() + 9.dp.toPx() * sin(a).toFloat()
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }; star.close()
        }
        drawPath(star, color.copy(alpha = .28f), style = Stroke(.8.dp.toPx()))
    }
}
