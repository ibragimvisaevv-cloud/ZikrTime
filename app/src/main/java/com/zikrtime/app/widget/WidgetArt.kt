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
