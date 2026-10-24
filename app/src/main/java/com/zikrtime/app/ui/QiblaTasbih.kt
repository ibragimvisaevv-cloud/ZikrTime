package com.zikrtime.app.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Направление телефона (азимут от истинного севера) по датчику ориентации. null — датчика нет. */
@Composable
private fun rememberHeading(lat: Double, lon: Double): Pair<Float?, Boolean> {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    var lowAccuracy by remember { mutableStateOf(false) }
    DisposableEffect(lat, lon) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        val rot = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                SensorManager.getOrientation(rot, orientation)
                val azimuth = ((Math.toDegrees(orientation[0].toDouble()) + declination + 360.0) % 360.0).toFloat()
                val old = heading
                // Угол «без скачков»: после 359° идёт 360°, 361°… — так стрелка поворачивается плавно.
                heading = if (old == null) azimuth else old + norm180(azimuth - old) * 0.15f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                lowAccuracy = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading to lowAccuracy
}

@Composable
fun QiblaScreen() {
    val nav = LocalNav.current
    val view = LocalView.current
    val loc = Store.location
    val qibla = remember(loc) { Qibla.bearing(loc.latitude, loc.longitude).toFloat() }
    val distance = remember(loc) { Qibla.distanceKm(loc.latitude, loc.longitude) }
    val (heading, lowAccuracy) = rememberHeading(loc.latitude, loc.longitude)
    val relative = heading?.let { norm180(qibla - it) }
    // Пружинка поверх датчика: циферблат догоняет телефон мягко, без дрожи.
    val shownHeading by androidx.compose.animation.core.animateFloatAsState(
        heading ?: 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 90f),
        label = "heading",
    )
    val aligned = relative != null && abs(relative) < 5f
    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(aligned) {
        if (aligned && !wasAligned) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        wasAligned = aligned
    }

    ScreenScaffold(title = stringResource(R.string.qibla_title), onBack = { nav.back() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp), contentAlignment = Alignment.Center) {
            Compass(heading = shownHeading, qibla = qibla, aligned = aligned)
        }
        Text(
            when {
                heading == null || relative == null -> stringResource(R.string.qibla_no_sensor)
                aligned -> stringResource(R.string.qibla_aligned)
                relative < 0 -> stringResource(R.string.qibla_turn_left, abs(relative).roundToInt())
                else -> stringResource(R.string.qibla_turn_right, relative.roundToInt())
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "qibla").animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "qibla-pulse",
    )
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val radius = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) / 2).toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            if (glowOn > 0f) {
                // Золотое сияние вокруг компаса, когда телефон смотрит на Каабу
                val g = r * (1.08f + 0.05f * pulse)
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
                    val sc = 1f + glowOn * (0.12f + 0.08f * pulse)
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
private val TASBIH_ARABIC = listOf(
    "سُبْحَانَ اللَّهِ",
    "الْحَمْدُ لِلَّهِ",
    "اللَّهُ أَكْبَرُ",
    "لَا إِلَٰهَ إِلَّا اللَّهُ",
    "أَسْتَغْفِرُ اللَّهَ",
    "اللَّهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ",
)

private val TASBIH_NAMES = listOf(
    R.string.zikr_1_name to R.string.zikr_1_meaning,
    R.string.zikr_2_name to R.string.zikr_2_meaning,
    R.string.zikr_3_name to R.string.zikr_3_meaning,
    R.string.zikr_4_name to R.string.zikr_4_meaning,
    R.string.zikr_5_name to R.string.zikr_5_meaning,
    R.string.zikr_6_name to R.string.zikr_6_meaning,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TasbihScreen() {
    val nav = LocalNav.current
    val view = LocalView.current
    val phrase = Store.tasbihPhrase.coerceIn(TASBIH_ARABIC.indices)
    val target = Store.tasbihTarget
    val count = Store.tasbihCount
    val progress = if (target > 0) (count % target).toFloat() / target else 0f
    val laps = if (target > 0) count / target else 0

    ScreenScaffold(
        title = stringResource(R.string.tasbih_title),
        onBack = { nav.back() },
        actions = { TextButton(onClick = { Store.tasbihCount = 0 }) { Text(stringResource(R.string.action_reset)) } },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TASBIH_NAMES.forEachIndexed { i, (name, _) ->
                FilterChip(
                    selected = i == phrase,
                    onClick = {
                        if (i != phrase) {
                            Store.tasbihPhrase = i
                            Store.tasbihCount = 0
                        }
                    },
                    label = { Text(stringResource(name)) },
                )
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                TASBIH_ARABIC[phrase],
                style = ArabicStyle.copy(fontSize = 34.sp, textDirection = TextDirection.Rtl),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(TASBIH_NAMES[phrase].second),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        // Чётки: 33 бусины по кругу. Нажатие или движение пальцем вниз «перекидывает» бусину.
        val context = LocalContext.current
        var burst by remember { mutableIntStateOf(0) }
        val count33 = count % 33
        val lit = if (count > 0 && count33 == 0) 33 else count33
        val turn by androidx.compose.animation.core.animateFloatAsState(
            -count * (360f / 33f),
            androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 260f),
            label = "beads",
        )
        val pop = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(count) {
            if (count > 0) {
                pop.snapTo(1f)
                pop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 300f))
            }
        }
        val press = remember { MutableInteractionSource() }
        fun addOne() {
            Store.addTasbih()
            val c = Store.tasbihCount
            when {
                target > 0 && c % target == 0 -> {
                    Haptics.done(context)
                    burst++
                }
                c % 33 == 0 -> {
                    Haptics.lap(context)
                    burst++
                }
                else -> Haptics.tick(context)
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .pressScale(press, 0.97f)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(EmeraldBright, Emerald, EmeraldDeep)))
                    .border(1.5.dp, Brush.linearGradient(listOf(GoldLight.copy(alpha = 0.8f), Gold.copy(alpha = 0.2f), GoldLight.copy(alpha = 0.6f))), CircleShape)
                    .pointerInput(target) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                dragged += dy
                                if (dragged > 46.dp.toPx()) {
                                    dragged = 0f
                                    addOne()
                                }
                            },
                        )
                    }
                    .clickable(interactionSource = press, indication = null) { addOne() },
                contentAlignment = Alignment.Center,
            ) {
                StarPattern(Modifier.fillMaxSize().parallax(8.dp, overscan = true), color = Gold.copy(alpha = 0.08f))
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val ringR = size.minDimension / 2 - 16.dp.toPx()
                    val beadR = (ringR * sin(Math.PI / 33).toFloat() * 0.86f)
                    drawCircle(Cream.copy(alpha = 0.18f), ringR, center, style = Stroke(1.2.dp.toPx()))
                    for (k in 0 until 33) {
                        val d = ((count - k) % 33 + 33) % 33
                        val on = d < lit
                        val angle = Math.toRadians((k * 360.0 / 33) + turn - 90)
                        val c = Offset(center.x + ringR * cos(angle).toFloat(), center.y + ringR * sin(angle).toFloat())
                        val scale = if (d == 0 && count > 0) 1f + 0.55f * pop.value else 1f
                        val r = beadR * scale
                        if (on) {
                            drawCircle(Gold.copy(alpha = 0.25f), r * 1.5f, c)
                            drawCircle(Brush.radialGradient(BeadGold, Offset(c.x - r * 0.35f, c.y - r * 0.35f), r * 1.4f), r, c)
                        } else {
                            drawCircle(Brush.radialGradient(BeadIdle, Offset(c.x - r * 0.35f, c.y - r * 0.35f), r * 1.4f), r, c)
                            drawCircle(Cream.copy(alpha = 0.22f), r, c, style = Stroke(0.8.dp.toPx()))
                        }
                    }
                    // Прогресс до цели — тонкая золотая дуга внутри
                    if (target > 0) {
                        val inner = ringR - beadR * 2.6f
                        drawArc(
                            Cream.copy(alpha = 0.12f), 0f, 360f, false,
                            Offset(center.x - inner, center.y - inner), androidx.compose.ui.geometry.Size(inner * 2, inner * 2),
                            style = Stroke(3.dp.toPx()),
                        )
                        drawArc(
                            Gold, -90f, 360f * progress, false,
                            Offset(center.x - inner, center.y - inner), androidx.compose.ui.geometry.Size(inner * 2, inner * 2),
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        (if (target > 0) count % target else count).toString(),
                        fontSize = 68.sp,
                        color = Cream,
                        style = Tabular,
                        modifier = Modifier.graphicsLayer {
                            val sc = 1f + 0.08f * pop.value
                            scaleX = sc
                            scaleY = sc
                        },
                    )
                    if (target > 0) {
                        Text(stringResource(R.string.tasbih_of, target), style = MaterialTheme.typography.titleMedium, color = Cream.copy(alpha = 0.8f))
                    }
                    if (laps > 0) {
                        Text(stringResource(R.string.tasbih_laps, laps), style = MaterialTheme.typography.labelLarge, color = Gold)
                    }
                }
            }
            RingBurst(burst, Modifier.fillMaxSize())
        }
        Text(stringResource(R.string.tasbih_tap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ZCard {
            Text(stringResource(R.string.tasbih_target), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(33, 99, 100, 0).forEach { t ->
                    FilterChip(
                        selected = target == t,
                        onClick = { Store.tasbihTarget = t },
                        label = { Text(if (t == 0) "∞" else t.toString()) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tasbih_today, Store.tasbihToday()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
