package com.zikrtime.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zikrtime.app.R
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.prayer.PrayerTimes
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.EmeraldNight
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.Tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// «Живые» детали интерфейса: небо по времени суток, наклон телефона, искры, вибрация, заставка.

// ---------------- Наклон телефона (параллакс) ----------------

/** Включатели «живых» эффектов (для проверки на эмуляторе можно выключать по одному). */
object LiveFlags {
    var tilt = true
    var parallax = true
    var splash = true
}

/** Наклон телефона относительно привычного положения: x и y от -1 до 1. */
val LocalTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

/** Слушает датчик гравитации, пока приложение на экране. */
@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // Если в телефоне выключены анимации («убрать движение» для удобства) — фон не двигаем.
        val motion = android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (!LiveFlags.tilt || motion == 0f) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var bx = Float.NaN
        var by = 0f
        var vx = 0f
        var vy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gx = (e.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val gy = (e.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                if (bx.isNaN()) {
                    bx = gx
                    by = gy
                }
                // «Ноль» медленно подстраивается под то, как человек держит телефон.
                bx += (gx - bx) * 0.015f
                by += (gy - by) * 0.015f
                vx += ((bx - gx) * 2.4f - vx) * 0.18f
                vy += ((gy - by) * 2.4f - vy) * 0.18f
                val next = Offset(vx.coerceIn(-1f, 1f), vy.coerceIn(-1f, 1f))
                // Перерисовываем только при заметном наклоне — бережём батарею.
                if (abs(next.x - tilt.value.x) > 0.01f || abs(next.y - tilt.value.y) > 0.01f) tilt.value = next
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
                Lifecycle.Event.ON_PAUSE -> sm.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
        }
    }
    return tilt
}

/**
 * Слой смещается при наклоне телефона на [depth] (отрицательное — в обратную сторону).
 * [overscan] слегка увеличивает слой, чтобы при сдвиге не открывались края.
 */
fun Modifier.parallax(depth: Dp, overscan: Boolean = false): Modifier = if (!LiveFlags.parallax) this else composed {
    val tilt = LocalTilt.current
    graphicsLayer {
        val t = tilt.value
        val d = depth.toPx()
        translationX = t.x * d
        translationY = t.y * d
        if (overscan && size.minDimension > 0f) {
            val s = 1f + 2.2f * abs(d) / size.minDimension
            scaleX = s
            scaleY = s
        }
    }
}

// ---------------- Вибрация ----------------

object Haptics {
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    /** Лёгкий «щелчок» бусины. */
    fun tick(context: Context) {
        val v = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else v.vibrate(VibrationEffect.createOneShot(12, 120))
    }

    /** Круг из 33 бусин пройден. */
    fun lap(context: Context) {
        val v = vibrator(context) ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40), intArrayOf(0, 200, 0, 200), -1))
    }

    /** Цель достигнута: длинная мягкая волна. */
    fun done(context: Context) {
        val v = vibrator(context) ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 60, 60, 180), intArrayOf(0, 255, 0, 255, 0, 160), -1))
    }
}

// ---------------- Небо по времени суток ----------------

enum class SkyPeriod { NIGHT, DAWN, MORNING, DAY, GOLDEN, SUNSET }

fun skyPeriod(now: ZonedDateTime, t: PrayerTimes): SkyPeriod {
    val sunrise = t[Prayer.SUNRISE]
    val maghrib = t[Prayer.MAGHRIB]
    return when {
        now.isBefore(t[Prayer.FAJR]) -> SkyPeriod.NIGHT
        now.isBefore(sunrise) -> SkyPeriod.DAWN
        now.isBefore(sunrise.plusMinutes(90)) -> SkyPeriod.MORNING
        now.isBefore(t[Prayer.ASR]) -> SkyPeriod.DAY
        now.isBefore(maghrib.minusMinutes(40)) -> SkyPeriod.GOLDEN
        now.isBefore(maghrib.plusMinutes(35)) -> SkyPeriod.SUNSET
        else -> SkyPeriod.NIGHT
    }
}

/** Три цвета неба сверху вниз. Все достаточно тёмные, чтобы светлый текст хорошо читался. */
fun skyColors(p: SkyPeriod): List<Color> = when (p) {
    SkyPeriod.NIGHT -> listOf(Color(0xFF071226), Color(0xFF0C1F3F), Color(0xFF0A3A33))
    SkyPeriod.DAWN -> listOf(Color(0xFF1B2050), Color(0xFF4F3A72), Color(0xFF8E5566))
    SkyPeriod.MORNING -> listOf(Color(0xFF17507A), Color(0xFF226F8A), Color(0xFF2E7F6C))
    SkyPeriod.DAY -> listOf(Color(0xFF12627A), Color(0xFF116A5C), Color(0xFF0A4A3D))
    SkyPeriod.GOLDEN -> listOf(Color(0xFF164E64), Color(0xFF3E6A4E), Color(0xFF8C6A2E))
    SkyPeriod.SUNSET -> listOf(Color(0xFF261848), Color(0xFF7A3556), Color(0xFFA4553A))
}

/** Тёплое свечение у горизонта. */
private fun horizonGlow(p: SkyPeriod): Color = when (p) {
    SkyPeriod.NIGHT -> Color(0xFF3A7BD5).copy(alpha = 0.18f)
    SkyPeriod.DAWN -> Color(0xFFFFA477).copy(alpha = 0.45f)
    SkyPeriod.MORNING -> Color(0xFFFFE2A0).copy(alpha = 0.30f)
    SkyPeriod.DAY -> Color(0xFFFFF1C0).copy(alpha = 0.20f)
    SkyPeriod.GOLDEN -> Color(0xFFFFC266).copy(alpha = 0.45f)
    SkyPeriod.SUNSET -> Color(0xFFFF8A4C).copy(alpha = 0.55f)
}

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float)

private fun crescent(center: Offset, r: Float): Path {
    val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    val inner = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + r * 0.42f, center.y - r * 0.18f), r * 0.86f)) }
    return Path().apply { op(outer, inner, PathOperation.Difference) }
}

/** Солнце: свечение, медленно вращающиеся лучи и ядро. */
private fun DrawScope.sun(c: Offset, spin: Float) {
    val glow = 30.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(GoldLight.copy(alpha = 0.75f), Gold.copy(alpha = 0.25f), Color.Transparent), c, glow), glow, c)
    rotate(spin, c) {
        for (i in 0 until 8) {
            val a = (i * 45.0) * PI / 180
            val s = 12.dp.toPx()
            val e = 17.dp.toPx()
            drawLine(
                GoldLight.copy(alpha = 0.8f),
                Offset(c.x + s * cos(a).toFloat(), c.y + s * sin(a).toFloat()),
                Offset(c.x + e * cos(a).toFloat(), c.y + e * sin(a).toFloat()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color.White, GoldLight, Gold), c, 9.dp.toPx()), 9.dp.toPx(), c)
}

private fun DrawScope.moon(c: Offset) {
    val glow = 26.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(Cream.copy(alpha = 0.35f), Color.Transparent), c, glow), glow, c)
    drawPath(crescent(c, 10.dp.toPx()), Cream)
}

/**
 * Главный блок «Следующий намаз»: небо меняется по времени суток, по дуге идёт солнце (ночью — луна),
 * справа — кольцо обратного отсчёта. За 5 минут до намаза кольцо загорается золотом.
 */
@Composable
fun SkyHero(prayer: Prayer, at: ZonedDateTime, previous: java.time.Instant?, times: PrayerTimes, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = ZonedDateTime.now()
        }
    }
    val today = times[Prayer.FAJR].toLocalDate()
    val yesterday = remember(times) { PrayerSchedule.forDate(today.minusDays(1)) }
    val tomorrow = remember(times) { PrayerSchedule.forDate(today.plusDays(1)) }
    val period = skyPeriod(now, times)
    val target = skyColors(period)
    val c0 by animateColorAsState(target[0], tween(1500), label = "sky0")
    val c1 by animateColorAsState(target[1], tween(1500), label = "sky1")
    val c2 by animateColorAsState(target[2], tween(1500), label = "sky2")
    val glowColor by animateColorAsState(horizonGlow(period), tween(1500), label = "skyGlow")

    // Дуга: днём от восхода до магриба, ночью от магриба до восхода.
    val sunrise = times[Prayer.SUNRISE]
    val maghrib = times[Prayer.MAGHRIB]
    val dayMode = !now.isBefore(sunrise) && now.isBefore(maghrib)
    val (arcStart, arcEnd) = when {
        dayMode -> sunrise to maghrib
        !now.isBefore(maghrib) -> maghrib to tomorrow[Prayer.SUNRISE]
        else -> yesterday[Prayer.MAGHRIB] to sunrise
    }
    val marks: List<Pair<ZonedDateTime, Boolean>> = when {
        dayMode -> listOf(times[Prayer.DHUHR], times[Prayer.ASR])
        !now.isBefore(maghrib) -> listOf(times[Prayer.ISHA], tomorrow[Prayer.FAJR])
        else -> listOf(yesterday[Prayer.ISHA], times[Prayer.FAJR])
    }.map { it to (it == at) }
    fun frac(t: ZonedDateTime): Float {
        val total = Duration.between(arcStart, arcEnd).seconds.toFloat()
        return if (total <= 0f) 0f else (Duration.between(arcStart, t).seconds / total).coerceIn(0f, 1f)
    }
    val bodyTarget = frac(now)
    val body by animateFloatAsState(bodyTarget, spring(stiffness = 40f), label = "body")

    val secondsLeft = Duration.between(now, at).seconds.coerceAtLeast(0)
    val soon = secondsLeft in 1..300
    val ring = previous?.let {
        val total = Duration.between(it, at.toInstant()).seconds.toFloat()
        val passed = Duration.between(it, now.toInstant()).seconds.toFloat()
        if (total > 0) (passed / total).coerceIn(0f, 1f) else null
    } ?: 0f

    val inf = rememberInfiniteTransition(label = "sky")
    val twinkle by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "twinkle")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "spin")
    val shoot by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "shoot")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val stars = remember {
        val r = Random(29)
        List(48) { Star(r.nextFloat(), r.nextFloat() * 0.72f, 0.6f + r.nextFloat() * 1.3f, r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 1.4f) }
    }
    val starAlpha = when (period) {
        SkyPeriod.NIGHT -> 1f
        SkyPeriod.DAWN, SkyPeriod.SUNSET -> 0.45f
        else -> 0f
    }
    val starsShown by animateFloatAsState(starAlpha, tween(1500), label = "stars")

    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = c1, spotColor = c2)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c0, c1, c2)))
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(GoldLight.copy(alpha = 0.65f), Gold.copy(alpha = 0.12f), GoldLight.copy(alpha = 0.4f)))), shape),
    ) {
        // Дальний слой: звёзды (двигаются при наклоне телефона)
        Canvas(Modifier.matchParentSize().parallax((-8).dp, overscan = true)) {
            if (starsShown > 0.01f) {
                stars.forEach { s ->
                    val a = starsShown * (0.35f + 0.65f * (0.5f + 0.5f * sin(twinkle * s.speed + s.phase)))
                    drawCircle(Color.White.copy(alpha = a), s.r.dp.toPx(), Offset(s.x * size.width, s.y * size.height))
                }
                // Падающая звезда раз в несколько секунд
                if (shoot < 0.12f) {
                    val k = shoot / 0.12f
                    val head = Offset(size.width * (0.15f + 0.5f * k), size.height * (0.06f + 0.22f * k))
                    val tail = Offset(head.x - 40.dp.toPx(), head.y - 18.dp.toPx())
                    drawLine(
                        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = starsShown * (1 - k))), tail, head),
                        tail, head, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        }
        StarPattern(Modifier.matchParentSize().parallax(10.dp, overscan = true), color = Gold.copy(alpha = 0.07f))
        // Ближний слой: горизонт, дуга, солнце или луна
        Canvas(Modifier.matchParentSize().parallax(4.dp, overscan = true)) {
            val baseY = size.height - 22.dp.toPx()
            val pad = 26.dp.toPx()
            val rx = size.width / 2 - pad
            val ry = 50.dp.toPx()
            val cx = size.width / 2
            fun point(p: Float): Offset {
                val th = PI * (1 - p)
                return Offset(cx + rx * cos(th).toFloat(), baseY - ry * sin(th).toFloat())
            }
            val bodyAt = point(body)
            drawCircle(Brush.radialGradient(listOf(glowColor, Color.Transparent), Offset(bodyAt.x, baseY), size.width * 0.6f), size.width * 0.6f, Offset(bodyAt.x, baseY))
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, Cream.copy(alpha = 0.35f), Color.Transparent)),
                Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx(),
            )
            val topLeft = Offset(cx - rx, baseY - ry)
            val arcSize = Size(rx * 2, ry * 2)
            drawArc(
                Cream.copy(alpha = 0.32f), 180f, 180f, false, topLeft, arcSize,
                style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 6.dp.toPx()))),
            )
            drawArc(
                Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.2f), GoldLight)), 180f, 180f * body, false, topLeft, arcSize,
                style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round),
            )
            marks.forEach { (t, isNext) ->
                val c = point(frac(t))
                if (isNext) {
                    drawCircle(Gold.copy(alpha = 0.25f + 0.25f * pulse), (7 + 3 * pulse).dp.toPx(), c)
                    drawCircle(GoldLight, 4.dp.toPx(), c)
                } else {
                    drawCircle(Cream.copy(alpha = 0.7f), 3.dp.toPx(), c)
                }
            }
            if (dayMode) sun(bodyAt, spin) else moon(bodyAt)
        }
        Box(Modifier.matchParentSize().sheen(alpha = 0.08f, periodMs = 7_000))

        Column(Modifier.padding(start = 22.dp, end = 18.dp, top = 22.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_next_prayer), style = MaterialTheme.typography.labelLarge, color = Cream.copy(alpha = 0.85f))
                    Text(prayerName(prayer), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Cream, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    BigClock(at, fontSize = 40.sp, color = Gold)
                }
                CountdownRing(prayer, ring, now.second, soon, pulse)
            }
            Spacer(Modifier.height(2.dp))
            val leftText = if (secondsLeft < 3600) {
                "%d:%02d".format(secondsLeft / 60, secondsLeft % 60)
            } else {
                durationText(secondsLeft / 60 + 1)
            }
            Text(
                stringResource(R.string.home_in_time, leftText),
                style = MaterialTheme.typography.titleMedium.merge(Tabular),
                color = if (soon) GoldLight else Cream,
                fontWeight = if (soon) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(64.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(clock(arcStart), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = Cream.copy(alpha = 0.75f))
                Spacer(Modifier.weight(1f))
                Text(clock(arcEnd), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = Cream.copy(alpha = 0.75f))
            }
        }
    }
}

/** Кольцо до следующего намаза: золотая дуга, бегущая точка секунд, за 5 минут — пульсирующее свечение. */
@Composable
private fun CountdownRing(prayer: Prayer, progress: Float, second: Int, soon: Boolean, pulse: Float) {
    val p by animateFloatAsState(progress, tween(900), label = "ring")
    val sec by animateFloatAsState(second.toFloat(), if (second == 0) tween(0) else tween(950, easing = LinearEasing), label = "sec")
    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 5.dp.toPx()
            val r = size.minDimension / 2 - w - 4.dp.toPx()
            if (soon) {
                val g = r + 14.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.55f * (0.5f + 0.5f * pulse)), Color.Transparent), center, g), g, center)
            }
            drawCircle(Color.Black.copy(alpha = 0.18f), r, center)
            drawCircle(Cream.copy(alpha = 0.18f), r, center, style = Stroke(w))
            drawArc(
                Brush.sweepGradient(listOf(GoldDark, GoldLight, Gold, GoldDark), center),
                -90f, 360f * p, false,
                Offset(center.x - r, center.y - r), Size(r * 2, r * 2),
                style = Stroke(if (soon) w * (1.2f + 0.3f * pulse) else w, cap = StrokeCap.Round),
            )
            val a = (sec / 60f * 360f - 90f) * PI / 180
            val dot = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            drawCircle(Color.White.copy(alpha = 0.9f), 2.6.dp.toPx(), dot)
        }
        Icon(
            painterResource(prayerIconRes(prayer)),
            contentDescription = null,
            tint = if (soon) GoldLight else Gold,
            modifier = Modifier.size(36.dp).graphicsLayer {
                val s = if (soon) 1f + 0.08f * pulse else 1f
                scaleX = s
                scaleY = s
            },
        )
    }
}

// ---------------- Искры ----------------

private class Spark(val angle: Float, val dist: Float, val size: Float, val color: Color)

/** Вспышка искр из центра; запускается при каждом изменении [key]. */
@Composable
fun SparkleBurst(key: Any, modifier: Modifier = Modifier, count: Int = 18, spread: Dp = 70.dp) {
    val anim = remember { Animatable(1f) }
    val sparks = remember(key) {
        val r = Random(key.hashCode())
        val colors = listOf(GoldLight, Gold, Color.White, Color(0xFF7FE0B5))
        List(count) { Spark(r.nextFloat() * 6.283f, 0.45f + r.nextFloat() * 0.55f, 2f + r.nextFloat() * 3f, colors[r.nextInt(colors.size)]) }
    }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val k = anim.value
        if (k >= 1f) return@Canvas
        val d = spread.toPx()
        sparks.forEach { s ->
            val dist = d * s.dist * k
            val c = Offset(center.x + dist * cos(s.angle), center.y + dist * sin(s.angle))
            val alpha = (1f - k).coerceIn(0f, 1f)
            sparkle(c, s.size.dp.toPx() * (1f - 0.4f * k), s.color.copy(alpha = alpha))
        }
    }
}

/** Четырёхлучевая искорка. */
private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - r * 2)
        quadraticTo(c.x, c.y, c.x + r * 2, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r * 2)
        quadraticTo(c.x, c.y, c.x - r * 2, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r * 2)
        close()
    }
    drawPath(path, color)
}

/** Расходящиеся золотые кольца и искры — когда тасбих завершён. */
@Composable
fun RingBurst(key: Int, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key > 0) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    val sparks = remember(key) {
        val r = Random(key)
        List(26) { Spark(r.nextFloat() * 6.283f, 0.7f + r.nextFloat() * 0.5f, 2.5f + r.nextFloat() * 3f, if (r.nextBoolean()) GoldLight else Color.White) }
    }
    Canvas(modifier) {
        val k = anim.value
        if (k >= 1f) return@Canvas
        val base = size.minDimension / 2
        for (i in 0 until 3) {
            val kk = (k - i * 0.12f).coerceIn(0f, 1f)
            if (kk <= 0f) continue
            drawCircle(Gold.copy(alpha = (1f - kk) * 0.8f), base * (0.85f + 0.35f * kk), center, style = Stroke((6 * (1 - kk) + 1).dp.toPx()))
        }
        sparks.forEach { s ->
            val dist = base * (0.8f + 0.45f * s.dist * k)
            sparkle(Offset(center.x + dist * cos(s.angle), center.y + dist * sin(s.angle)), s.size.dp.toPx(), s.color.copy(alpha = 1f - k))
        }
    }
}

// ---------------- Тряска ----------------

/** Короткое покачивание из стороны в сторону (неверный ответ). */
fun Modifier.shake(trigger: Boolean): Modifier = composed {
    val x = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            for (v in listOf(-14f, 12f, -9f, 7f, -4f, 0f)) x.animateTo(v, tween(55))
        }
    }
    graphicsLayer { translationX = x.value * density }
}

// ---------------- Заставка ----------------

/** Заставка при запуске: золотой полумесяц со звездой на изумрудном фоне. */
@Composable
fun CrescentSplash(modifier: Modifier = Modifier) {
    val grow = remember { Animatable(0.55f) }
    val turn = remember { Animatable(-35f) }
    val star = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 160f)) }
        launch { turn.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 120f)) }
        delay(300)
        star.animateTo(1f, tween(450))
    }
    val glow by rememberInfiniteTransition(label = "splash").animateFloat(
        0.3f, 0.6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "splash-glow",
    )
    Box(
        modifier.fillMaxSize().background(Brush.radialGradient(listOf(EmeraldBright, Emerald, EmeraldDeep, EmeraldNight))),
        contentAlignment = Alignment.Center,
    ) {
        StarPattern(Modifier.fillMaxSize(), color = Gold.copy(alpha = 0.08f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(150.dp)) {
                val r = size.minDimension * 0.30f * grow.value
                val c = center
                drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = glow), Color.Transparent), c, r * 2.2f), r * 2.2f, c)
                rotate(turn.value, c) {
                    drawPath(crescent(c, r), Brush.linearGradient(listOf(GoldLight, Gold, GoldDark), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
                    if (star.value > 0f) {
                        val sc = Offset(c.x + r * 0.62f, c.y - r * 0.05f)
                        sparkle(sc, r * 0.13f * star.value, GoldLight.copy(alpha = star.value))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.app_name),
                color = Cream.copy(alpha = star.value),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Цвета для бусин тасбиха. */
internal val BeadGold = listOf(Color.White, GoldLight, Gold, GoldDark)
internal val BeadIdle = listOf(Color.White.copy(alpha = 0.35f), Cream.copy(alpha = 0.16f), Cream.copy(alpha = 0.08f))
