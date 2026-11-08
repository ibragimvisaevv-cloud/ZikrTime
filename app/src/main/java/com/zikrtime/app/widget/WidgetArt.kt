package com.zikrtime.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/** Non-text progress marks for RemoteViews. All values and labels remain native accessible text. */
object WidgetArt {
    /** Keep launcher bitmaps bounded; no photo, shader or recurring animation is needed. */
    private fun px(context: Context): Float = min(context.resources.displayMetrics.density, 2.5f)

    private fun alpha(color: Int, a: Float): Int =
        (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    /** Remaining time, on a quiet line. The native text above gives the exact amount and total. */
    fun focusMeter(context: Context, widthDp: Int, progress: Float, color: Int, trackTint: Int): Bitmap {
        val scale = px(context)
        val width = max(1, (widthDp.coerceIn(48, 560) * scale).toInt())
        val height = max(1, (12 * scale).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        val inset = 3 * scale
        val y = height / 2f
        val end = width - inset
        paint.color = alpha(trackTint, .22f)
        paint.strokeWidth = 2 * scale
        canvas.drawLine(inset, y, end, y, paint)
        val position = inset + (end - inset) * progress.coerceIn(0f, 1f)
        if (progress > 0f) {
            paint.color = color
            paint.strokeWidth = 4 * scale
            canvas.drawLine(inset, y, position, y, paint)
            canvas.drawCircle(position, y, 3 * scale, paint)
        }
        return bitmap
    }

    /** The existing 33-count cycle and goal progress, now two readable linear scales. */
    fun beads(context: Context, sizeDp: Int, count: Int, target: Int, light: Boolean = false): Bitmap {
        val scale = px(context)
        val width = max(1, (sizeDp.coerceIn(48, 560) * scale).toInt())
        val height = max(1, (28 * scale).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        val accent = if (light) 0xFF3157C8.toInt() else 0xFFB5C5FF.toInt()
        val ink = if (light) 0xFF5C626F.toInt() else 0xFFB9C0CD.toInt()
        val inset = 2 * scale
        val end = width - inset
        val lap = count % 33
        val lit = if (count > 0 && lap == 0) 33 else lap
        for (index in 0 until 33) {
            val x = inset + (end - inset) * index / 32f
            val major = index % 11 == 0 || index == 32
            paint.color = if (index < lit) accent else alpha(ink, .28f)
            paint.strokeWidth = max(scale, (end - inset) / 66f)
            canvas.drawLine(x, 17 * scale, x, (if (major) 26 else 23) * scale, paint)
        }
        if (target > 0) {
            paint.strokeWidth = 2 * scale
            paint.color = alpha(ink, .22f)
            canvas.drawLine(inset, 6 * scale, end, 6 * scale, paint)
            val fraction = (count % target).toFloat() / target
            if (fraction > 0f) {
                paint.color = accent
                paint.strokeWidth = 3 * scale
                val position = inset + (end - inset) * fraction
                canvas.drawLine(inset, 6 * scale, position, 6 * scale, paint)
                canvas.drawCircle(position, 6 * scale, 3 * scale, paint)
            }
        }
        return bitmap
    }
}
