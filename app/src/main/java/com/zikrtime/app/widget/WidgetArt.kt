package com.zikrtime.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Картинки для виджетов (виджеты не умеют рисовать градиенты и кольца сами — рисуем их в Bitmap):
 * небо по времени суток со звёздами, солнцем или полумесяцем; кольца прогресса; линия дня.
 */
object WidgetArt {
    const val GOLD = 0xFFE2B659.toInt()
    const val GOLD_LIGHT = 0xFFFFE7A8.toInt()
    const val GOLD_DARK = 0xFFC9962F.toInt()
    const val CREAM = 0xFFFFF6E0.toInt()

    enum class Body { SUN, MOON, NONE }

    /** Не больше 2,5 пикселя на dp — иначе картинки слишком тяжёлые для виджета. */
    private fun px(context: Context): Float = min(context.resources.displayMetrics.density, 2.5f)

    private fun alpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    /** Фон виджета: градиент неба, свечение у горизонта, звёзды, узор и светило в углу. */
    fun sky(
        context: Context,
        wDp: Int,
        hDp: Int,
        colors: IntArray,
        glow: Int,
        stars: Float,
        body: Body,
        seed: Int,
    ): Bitmap {
        val s = px(context)
        val w = max(1, (wDp * s).toInt())
        val h = max(1, (hDp * s).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = 24 * s
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })

        p.shader = LinearGradient(0f, 0f, w * 0.35f, h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        c.drawRect(rect, p)

        // Тёплое свечение там, где светило
        val gx = w * 0.82f
        val gy = h * 1.02f
        val gr = max(w, h) * 0.9f
        p.shader = RadialGradient(gx, gy, gr, intArrayOf(glow, alpha(glow, 0f)), null, Shader.TileMode.CLAMP)
        c.drawRect(rect, p)
        p.shader = RadialGradient(w * 0.1f, 0f, max(w, h) * 0.6f, intArrayOf(alpha(GOLD_LIGHT, 0.16f), 0), null, Shader.TileMode.CLAMP)
        c.drawRect(rect, p)
        p.shader = null

        // Звёзды
        if (stars > 0f) {
            val r = Random(seed)
            val n = (wDp * hDp / 700).coerceIn(10, 70)
            repeat(n) {
                val x = r.nextFloat() * w
                val y = r.nextFloat() * h * 0.8f
                val big = r.nextFloat() < 0.12f
                p.color = alpha(0xFFFFFFFF.toInt(), stars * (0.35f + r.nextFloat() * 0.6f))
                if (big) {
                    sparkle(c, x, y, 2.6f * s, p)
                } else {
                    c.drawCircle(x, y, (0.5f + r.nextFloat() * 1.1f) * s, p)
                }
            }
        }

        // Узор из восьмиконечных звёзд
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f * s
        p.color = alpha(GOLD, 0.10f)
        val step = 54 * s
        var y = 0f
        var row = 0
        while (y < h + step) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < w + step) {
                c.drawPath(octagram(x, y, step * 0.3f), p)
                x += step
            }
            y += step * 0.75f
            row++
        }
        p.style = Paint.Style.FILL

        // Светило в правом нижнем углу (частично за краем)
        when (body) {
            Body.SUN -> {
                val cx = w - 30 * s
                val cy = h - 26 * s
                p.shader = RadialGradient(cx, cy, 70 * s, intArrayOf(alpha(GOLD_LIGHT, 0.7f), alpha(GOLD, 0.25f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, 70 * s, p)
                p.shader = null
                p.color = alpha(GOLD_LIGHT, 0.75f)
                p.strokeWidth = 2.2f * s
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0 + 8)
                    val r1 = 25 * s
                    val r2 = (if (i % 2 == 0) 36 else 31) * s
                    c.drawLine(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(), cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(), p)
                }
                p.shader = RadialGradient(cx - 5 * s, cy - 5 * s, 22 * s, intArrayOf(0xFFFFFFFF.toInt(), GOLD_LIGHT, GOLD), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, 19 * s, p)
                p.shader = null
            }
            Body.MOON -> {
                val cx = w - 34 * s
                val cy = h - 30 * s
                p.shader = RadialGradient(cx, cy, 64 * s, intArrayOf(alpha(CREAM, 0.35f), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, 64 * s, p)
                p.shader = LinearGradient(cx - 24 * s, cy - 24 * s, cx + 24 * s, cy + 24 * s, intArrayOf(0xFFFFFFFF.toInt(), GOLD_LIGHT, GOLD), null, Shader.TileMode.CLAMP)
                c.drawPath(crescent(cx, cy, 24 * s), p)
                p.shader = null
                p.color = GOLD_LIGHT
                sparkle(c, cx + 2 * s, cy - 10 * s, 4f * s, p)
            }
            Body.NONE -> Unit
        }

        // Тонкая золотая рамка
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * s
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(alpha(GOLD_LIGHT, 0.75f), alpha(GOLD, 0.15f), alpha(GOLD_LIGHT, 0.5f)), null, Shader.TileMode.CLAMP)
        val inset = 0.6f * s
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius, p)
        return bmp
    }

    /** Кольцо прогресса с иконкой в центре. */
    fun ring(
        context: Context,
        sizeDp: Int,
        progress: Float,
        sweep: IntArray,
        icon: Int,
        iconTint: Int,
        glow: Boolean = false,
    ): Bitmap {
        val s = px(context)
        val size = max(1, (sizeDp * s).toInt())
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val stroke = size * 0.085f
        val r = size / 2f - stroke - (if (glow) size * 0.08f else size * 0.03f)
        if (glow) {
            p.maskFilter = BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL)
            p.color = alpha(sweep.last(), 0.8f)
            c.drawCircle(cx, cx, r + stroke * 0.6f, p)
            p.maskFilter = null
        }
        p.color = 0x33000000
        c.drawCircle(cx, cx, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.color = alpha(CREAM, 0.20f)
        c.drawCircle(cx, cx, r, p)
        val shader = SweepGradient(cx, cx, sweep + sweep.first(), null)
        shader.setLocalMatrix(Matrix().apply { postRotate(-90f, cx, cx) })
        p.shader = shader
        p.strokeCap = Paint.Cap.ROUND
        val sweepAngle = 360f * progress.coerceIn(0f, 1f)
        if (sweepAngle > 0.5f) c.drawArc(RectF(cx - r, cx - r, cx + r, cx + r), -90f, sweepAngle, false, p)
        p.shader = null
        // Точка на конце дуги
        if (sweepAngle > 0.5f) {
            val a = Math.toRadians((sweepAngle - 90f).toDouble())
            p.style = Paint.Style.FILL
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(cx + r * cos(a).toFloat(), cx + r * sin(a).toFloat(), stroke * 0.42f, p)
        }
        ContextCompat.getDrawable(context, icon)?.mutate()?.let { d ->
            val half = (r * 0.62f).toInt()
            d.setBounds((cx - half).toInt(), (cx - half).toInt(), (cx + half).toInt(), (cx + half).toInt())
            d.colorFilter = PorterDuffColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
            d.draw(c)
        }
        return bmp
    }

    /** Линия дня под колонками намазов: пройденная часть — золотом, «солнце» — там, где сейчас. */
    fun dayLine(context: Context, wDp: Int, position: Float, next: Int, dayMode: Boolean): Bitmap {
        val s = px(context)
        val w = max(1, (wDp * s).toInt())
        val h = max(1, (16 * s).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cy = h / 2f
        val pad = 6 * s
        val x = pad + (w - 2 * pad) * position.coerceIn(0f, 1f)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 2.2f * s
        p.color = alpha(CREAM, 0.22f)
        c.drawLine(pad, cy, w - pad, cy, p)
        p.shader = LinearGradient(pad, 0f, x, 0f, intArrayOf(alpha(GOLD, 0.2f), GOLD_LIGHT), null, Shader.TileMode.CLAMP)
        c.drawLine(pad, cy, x, cy, p)
        p.shader = null
        for (i in 0 until 6) {
            val dx = pad + (w - 2 * pad) * (i + 0.5f) / 6f
            p.color = if (i == next) GOLD_LIGHT else if (dx <= x) GOLD else alpha(CREAM, 0.5f)
            c.drawCircle(dx, cy, (if (i == next) 3.4f else 2.4f) * s, p)
        }
        p.shader = RadialGradient(x, cy, 9 * s, intArrayOf(alpha(if (dayMode) GOLD_LIGHT else CREAM, 0.8f), 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, cy, 9 * s, p)
        p.shader = null
        if (dayMode) {
            p.color = GOLD_LIGHT
            c.drawCircle(x, cy, 4.2f * s, p)
        } else {
            p.color = CREAM
            c.drawPath(crescent(x, cy, 5f * s), p)
        }
        return bmp
    }

    /**
     * Дуга суток над колонками намазов: днём (от восхода до заката) кривая поднимается над горизонтом,
     * ночью уходит под него. Точки — намазы по их времени, пунктир ведёт от точки к своей колонке.
     * [times] — доли суток (0…1) шести намазов, [now] — доля суток сейчас, [next] — индекс следующего.
     */
    fun dayArc(context: Context, wDp: Int, hDp: Int, times: List<Float>, now: Float, next: Int): Bitmap {
        val s = px(context)
        val w = max(1, (wDp * s).toInt())
        val h = max(1, (hDp * s).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val pad = 6 * s
        val horizon = h * 0.66f
        val up = horizon - 11 * s
        val down = h - horizon - 3 * s
        val sunrise = times[1]
        val sunset = times[4]
        fun x(t: Float) = pad + (w - 2 * pad) * t
        fun y(t: Float): Float {
            return if (t in sunrise..sunset) {
                horizon - up * kotlin.math.sin(Math.PI * (t - sunrise) / (sunset - sunrise)).toFloat()
            } else {
                val night = 1f - (sunset - sunrise)
                val u = ((t - sunset + 1f) % 1f) / night
                horizon + down * kotlin.math.sin(Math.PI * u).toFloat()
            }
        }
        val sunX = x(now)
        val isDay = now in sunrise..sunset
        // Горизонт
        p.strokeWidth = 1 * s
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(0, alpha(CREAM, 0.45f), 0), null, Shader.TileMode.CLAMP)
        c.drawLine(0f, horizon, w.toFloat(), horizon, p)
        p.shader = null
        // Кривая суток: пройденная часть золотая, остальная — пунктиром
        val steps = 120
        val passed = Path()
        val rest = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val (px, py) = x(t) to y(t)
            if (t <= now) {
                if (i == 0) passed.moveTo(px, py) else passed.lineTo(px, py)
                if (t + 1f / steps > now) rest.moveTo(px, py)
            } else {
                if (rest.isEmpty) rest.moveTo(px, py) else rest.lineTo(px, py)
            }
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 2.2f * s
        p.shader = LinearGradient(0f, 0f, sunX, 0f, intArrayOf(alpha(GOLD, 0.25f), GOLD_LIGHT), null, Shader.TileMode.CLAMP)
        c.drawPath(passed, p)
        p.shader = null
        p.strokeWidth = 1.4f * s
        p.color = alpha(CREAM, 0.4f)
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4 * s, 4 * s), 0f)
        c.drawPath(rest, p)
        // Пунктир от намаза на дуге к его колонке внизу
        p.color = alpha(CREAM, 0.18f)
        p.strokeWidth = 1 * s
        times.forEachIndexed { i, t ->
            c.drawLine(x(t), y(t), pad + (w - 2 * pad) * (i + 0.5f) / 6f, h.toFloat(), p)
        }
        p.pathEffect = null
        p.style = Paint.Style.FILL
        // Точки намазов
        times.forEachIndexed { i, t ->
            val done = t <= now
            if (i == next) {
                p.color = alpha(GOLD, 0.35f)
                c.drawCircle(x(t), y(t), 6.5f * s, p)
                p.color = GOLD_LIGHT
                c.drawCircle(x(t), y(t), 3.6f * s, p)
            } else {
                p.color = if (done) GOLD else alpha(CREAM, 0.7f)
                c.drawCircle(x(t), y(t), 2.6f * s, p)
            }
        }
        // Солнце или луна
        val sy = y(now)
        if (isDay) {
            p.shader = RadialGradient(sunX, sy, 13 * s, intArrayOf(alpha(GOLD_LIGHT, 0.9f), alpha(GOLD, 0.3f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(sunX, sy, 13 * s, p)
            p.shader = null
            p.color = alpha(GOLD_LIGHT, 0.85f)
            p.strokeWidth = 1.6f * s
            p.style = Paint.Style.STROKE
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                c.drawLine(sunX + 7 * s * cos(a).toFloat(), sy + 7 * s * sin(a).toFloat(), sunX + 10f * s * cos(a).toFloat(), sy + 10f * s * sin(a).toFloat(), p)
            }
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(sunX - 1.5f * s, sy - 1.5f * s, 7 * s, intArrayOf(0xFFFFFFFF.toInt(), GOLD_LIGHT, GOLD), null, Shader.TileMode.CLAMP)
            c.drawCircle(sunX, sy, 5 * s, p)
            p.shader = null
        } else {
            p.shader = RadialGradient(sunX, sy, 11 * s, intArrayOf(alpha(CREAM, 0.45f), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(sunX, sy, 11 * s, p)
            p.shader = null
            p.color = CREAM
            c.drawPath(crescent(sunX, sy, 5.5f * s), p)
        }
        return bmp
    }

    /** Чётки для виджета тасбиха: 33 бусины по кругу, отсчитанные — золотые, текущая — крупнее. */
    fun beads(context: Context, sizeDp: Int, count: Int, target: Int): Bitmap {
        val s = px(context)
        val size = max(1, (sizeDp * s).toInt())
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val ringR = size / 2f - 10 * s
        val beadR = (ringR * kotlin.math.sin(Math.PI / 33).toFloat() * 0.86f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * s
        p.color = alpha(CREAM, 0.2f)
        c.drawCircle(cx, cx, ringR, p)
        p.style = Paint.Style.FILL
        val lap = count % 33
        val lit = if (count > 0 && lap == 0) 33 else lap
        for (k in 0 until 33) {
            // Последняя отсчитанная бусина — сверху, остальные идут против часовой стрелки
            val d = k
            val on = d < lit
            val a = Math.toRadians(-90.0 - d * 360.0 / 33)
            val x = cx + ringR * cos(a).toFloat()
            val y = cx + ringR * sin(a).toFloat()
            val r = if (d == 0 && count > 0) beadR * 1.35f else beadR
            p.shader = null
            if (on) {
                // Золотая бусина с бликом
                p.color = alpha(GOLD, 0.35f)
                c.drawCircle(x, y, r * 1.45f, p)
                p.color = GOLD_DARK
                c.drawCircle(x, y, r, p)
                p.color = GOLD
                c.drawCircle(x - r * 0.12f, y - r * 0.12f, r * 0.82f, p)
                p.color = alpha(0xFFFFFFFF.toInt(), 0.75f)
                c.drawCircle(x - r * 0.38f, y - r * 0.38f, r * 0.28f, p)
            } else {
                p.color = alpha(CREAM, 0.16f)
                c.drawCircle(x, y, r, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.8f * s
                p.color = alpha(CREAM, 0.3f)
                c.drawCircle(x, y, r, p)
                p.style = Paint.Style.FILL
            }
        }
        p.shader = null
        // Прогресс к цели — тонкая дуга внутри
        if (target > 0) {
            val inner = ringR - beadR * 2.6f
            val rect = RectF(cx - inner, cx - inner, cx + inner, cx + inner)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3 * s
            p.strokeCap = Paint.Cap.ROUND
            p.color = alpha(CREAM, 0.14f)
            c.drawArc(rect, 0f, 360f, false, p)
            p.color = GOLD
            val sweep = 360f * (count % target) / target
            if (sweep > 0f) c.drawArc(rect, -90f, sweep, false, p)
        }
        return bmp
    }

    private fun octagram(x: Float, y: Float, r: Float): Path {
        val path = Path()
        for (square in 0..1) {
            for (i in 0..3) {
                val a = Math.toRadians(45.0 * square + 90.0 * i + 45.0)
                val px = x + r * cos(a).toFloat()
                val py = y + r * sin(a).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        }
        return path
    }

    private fun crescent(cx: Float, cy: Float, r: Float): Path {
        val outer = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val inner = Path().apply { addCircle(cx + r * 0.42f, cy - r * 0.18f, r * 0.86f, Path.Direction.CW) }
        outer.op(inner, Path.Op.DIFFERENCE)
        return outer
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
        val path = Path().apply {
            moveTo(x, y - r * 2)
            quadTo(x, y, x + r * 2, y)
            quadTo(x, y, x, y + r * 2)
            quadTo(x, y, x - r * 2, y)
            quadTo(x, y, x, y - r * 2)
            close()
        }
        c.drawPath(path, p)
    }
}
