package com.zikrtime.app.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.Qibla
import com.zikrtime.app.ui.theme.ArabicStyle
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.Tabular
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.roundToInt

// ---------------- Кибла ----------------

@Composable
fun QiblaScreen() {
    val nav = LocalNav.current
    val view = LocalView.current
    val loc = Store.location
    val qibla = remember(loc) { Qibla.bearing(loc.latitude, loc.longitude).toFloat() }
    val distance = remember(loc) { Qibla.distanceKm(loc.latitude, loc.longitude) }
    var viewOpen by remember { mutableStateOf(false) }
    val pose = rememberQiblaPose(loc.latitude, loc.longitude, upright = false, enabled = !viewOpen)
    val heading = pose.heading
    val lowAccuracy = pose.lowAccuracy
    val relative = heading?.let { norm180(qibla - it) }
    // Пружинка поверх датчика: циферблат догоняет телефон мягко, без дрожи.
    val shownHeading by androidx.compose.animation.core.animateFloatAsState(
        heading ?: 0f,
        if (motionEnabled()) androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 90f)
        else androidx.compose.animation.core.snap(),
        label = "heading",
    )
    val aligned = relative != null && !lowAccuracy && abs(relative) < 5f
    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(aligned) {
        if (aligned && !wasAligned) view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        wasAligned = aligned
    }

    ScreenScaffold(title = stringResource(R.string.qibla_title), onBack = { nav.back() }, showHeading = false) {
        PageHeading(stringResource(R.string.qibla_title), com.zikrtime.app.data.Cities.displayName(LocalContext.current, loc))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).testTag("qibla_compass").padding(8.dp), contentAlignment = Alignment.Center) {
            Compass(heading = shownHeading, qibla = qibla, aligned = aligned)
        }
        Text(
            when {
                heading == null || relative == null -> stringResource(R.string.qibla_no_sensor)
                lowAccuracy -> stringResource(R.string.qibla_calibrate)
                aligned -> stringResource(R.string.qibla_aligned)
                relative < 0 -> stringResource(R.string.qibla_turn_left, abs(relative).roundToInt())
                else -> stringResource(R.string.qibla_turn_right, relative.roundToInt())
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        QiblaViewButton { viewOpen = it }
        ZCard {
            InfoLine(stringResource(R.string.qibla_bearing), "${qibla.roundToInt()}°")
            InfoLine(stringResource(R.string.qibla_distance), stringResource(R.string.unit_km, distance.roundToInt()))
            InfoLine(stringResource(R.string.settings_city), com.zikrtime.app.data.Cities.displayName(LocalContext.current, loc))
        }
        Text(
            stringResource(if (lowAccuracy) R.string.qibla_calibrate else R.string.qibla_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (lowAccuracy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium.merge(Tabular))
    }
}

@Composable
private fun Compass(heading: Float, qibla: Float, aligned: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val ring = if (aligned) Gold else scheme.outlineVariant
    val letters = listOf(
        stringResource(R.string.compass_n), stringResource(R.string.compass_e),
        stringResource(R.string.compass_s), stringResource(R.string.compass_w),
    )
    val glowOn by androidx.compose.animation.core.animateFloatAsState(if (aligned) 1f else 0f, androidx.compose.animation.core.tween(450), label = "qibla-glow")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val radius = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) / 2).toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            if (glowOn > 0f) {
                // Золотое сияние вокруг компаса, когда телефон смотрит на Каабу
                val g = r * 1.10f
                drawCircle(
                    Brush.radialGradient(
                        0.78f to Color.Transparent,
                        0.9f to Gold.copy(alpha = 0.55f * glowOn),
                        1f to Color.Transparent,
                        center = center, radius = g,
                    ),
                    radius = g,
                )
            }
            drawCircle(
                Brush.radialGradient(listOf(scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer), center, r),
                radius = r,
            )
            drawCircle(ring, radius = r - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
            // Циферблат поворачивается вместе с телефоном: север указывает на север.
            rotate(-heading) {
                for (deg in 0 until 360 step 5) {
                    val major = deg % 30 == 0
                    rotate(deg.toFloat()) {
                        drawLine(
                            color = if (major) scheme.onSurfaceVariant else scheme.outlineVariant,
                            start = Offset(center.x, center.y - r + 10.dp.toPx()),
                            end = Offset(center.x, center.y - r + (if (major) 22.dp else 16.dp).toPx()),
                            strokeWidth = (if (major) 2.dp else 1.dp).toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                rotate(qibla) {
                    // Золотая метка на кольце и стрелка от центра к Каабе
                    val mark = Path().apply {
                        moveTo(center.x, center.y - r + 8.dp.toPx())
                        lineTo(center.x - 8.dp.toPx(), center.y - r + 24.dp.toPx())
                        lineTo(center.x + 8.dp.toPx(), center.y - r + 24.dp.toPx())
                        close()
                    }
                    drawPath(mark, Gold)
                    val tipY = center.y - r * 0.5f + 22.dp.toPx()
                    val w = 6.dp.toPx()
                    val needle = Path().apply {
                        moveTo(center.x, tipY)
                        lineTo(center.x + w, center.y)
                        lineTo(center.x, center.y + w * 1.8f)
                        lineTo(center.x - w, center.y)
                        close()
                    }
                    drawPath(needle, Gold)
                }
            }
            // Неподвижная метка «куда смотрит телефон»
            val pointer = Path().apply {
                moveTo(center.x, center.y - r - 4.dp.toPx())
                lineTo(center.x - 10.dp.toPx(), center.y - r + 12.dp.toPx())
                lineTo(center.x + 10.dp.toPx(), center.y - r + 12.dp.toPx())
                close()
            }
            drawPath(pointer, if (aligned) Gold else scheme.primary)
            drawCircle(scheme.surface, radius = 10.dp.toPx())
            drawCircle(if (aligned) Gold else scheme.primary, radius = 6.dp.toPx())
        }
        // Буквы сторон света: двигаются по кругу, но всегда стоят ровно.
        letters.forEachIndexed { i, letter ->
            Text(
                letter,
                style = MaterialTheme.typography.titleMedium,
                color = if (i == 0) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.offset { polar(i * 90f - heading, radius - 40.dp.toPx()) },
            )
        }
        // Кааба на своём азимуте
        Box(
            Modifier
                .offset { polar(qibla - heading, radius * 0.5f) }
                .graphicsLayer {
                    val sc = 1f + glowOn * 0.12f
                    scaleX = sc
                    scaleY = sc
                }
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Emerald, EmeraldDeep)))
                .border(2.dp, Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_kaaba), contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp))
        }
    }
}

/** Угол в диапазоне −180…180°. */
private fun norm180(x: Float): Float = ((x % 360f + 540f) % 360f) - 180f

/** Смещение от центра на distance пикселей в направлении angle (0° — вверх, по часовой стрелке). */
private fun polar(angle: Float, distance: Float): IntOffset {
    val a = Math.toRadians(angle.toDouble())
    return IntOffset((distance * sin(a)).roundToInt(), (-distance * cos(a)).roundToInt())
}

// ---------------- Тасбих ----------------

/** Зикры: текст на арабском (одинаков для всех языков), название и смысл — в ресурсах. */
internal val TASBIH_ARABIC = listOf(
    "سُبْحَانَ اللَّهِ",
    "الْحَمْدُ لِلَّهِ",
    "اللَّهُ أَكْبَرُ",
    "لَا إِلَٰهَ إِلَّا اللَّهُ",
    "أَسْتَغْفِرُ اللَّهَ",
    "اللَّهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ",
)

internal val TASBIH_NAMES = listOf(
    R.string.zikr_1_name to R.string.zikr_1_meaning,
    R.string.zikr_2_name to R.string.zikr_2_meaning,
    R.string.zikr_3_name to R.string.zikr_3_meaning,
    R.string.zikr_4_name to R.string.zikr_4_meaning,
    R.string.zikr_5_name to R.string.zikr_5_meaning,
    R.string.zikr_6_name to R.string.zikr_6_meaning,
)
