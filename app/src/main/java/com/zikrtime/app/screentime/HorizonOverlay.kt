package com.zikrtime.app.screentime

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.compose.ui.graphics.toArgb
import com.zikrtime.app.ui.theme.LightColors
import com.zikrtime.app.ui.theme.DarkColors
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang

/** Native fallback windows use the same materials as Compose and remain scrollable at 200%. */
internal class HorizonOverlay(context: Context, title: String, subtitle: String? = null, private val fullScreen: Boolean = false) {
    private val context = Lang.wrap(context)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val dark = fullScreen || when (Store.appearance) {
        "light" -> false
        "dark" -> true
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    private val overlayScheme = if (dark) DarkColors else LightColors
    private val ink = overlayScheme.onSurface.toArgb()
    val view = FrameLayout(this.context).apply { isClickable = true }
    private val content = LinearLayout(this.context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(28), dp(24), dp(28))
    }
    init {
        if (fullScreen) {
            view.setBackgroundColor(overlayScheme.background.toArgb())
        } else {
            view.background = GradientDrawable().apply {
                setColor(overlayScheme.surfaceContainerLow.toArgb())
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), overlayScheme.outlineVariant.toArgb())
            }
            view.clipToOutline = true
        }
        val scroll = object : ScrollView(this.context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val max = (resources.displayMetrics.heightPixels * .82f).toInt()
                val available = View.MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: max
                super.onMeasure(widthMeasureSpec, if (fullScreen) heightMeasureSpec else
                    View.MeasureSpec.makeMeasureSpec(minOf(available, max), View.MeasureSpec.AT_MOST))
            }
        }.apply {
            isFillViewport = fullScreen
            if (fullScreen) androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { target, insets ->
                val safe = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                target.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                insets
            }
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        view.addView(scroll, FrameLayout.LayoutParams(-1, if (fullScreen) -1 else -2))
        content.addView(TextView(this.context).apply {
            text = title; textSize = if (fullScreen) 30f else 26f; setTextColor(ink)
            typeface = Typeface.create("sans-serif", Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        if (subtitle != null) content.addView(TextView(this.context).apply {
            text = subtitle; textSize = 16f; setTextColor(overlayScheme.onSurfaceVariant.toArgb())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12))
        })
    }
    fun button(label: String, id: Int = View.NO_ID, primary: Boolean = false, action: () -> Unit) {
        val fill = (if (primary) overlayScheme.primary else overlayScheme.surfaceContainerHigh).toArgb()
        val labelColor = (if (primary) overlayScheme.onPrimary else overlayScheme.onSurface).toArgb()
        content.addView(Button(context).apply {
            text = label; this.id = id; isAllCaps = false; textSize = 16f
            setTextColor(labelColor)
            minHeight = dp(52); setPadding(dp(16), dp(14), dp(16), dp(14)); stateListAnimator = null
            val shape = GradientDrawable().apply { setColor(fill); cornerRadius = dp(14).toFloat() }
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        })
    }
    fun params() = WindowManager.LayoutParams(
        if (fullScreen) -1 else context.resources.displayMetrics.widthPixels - dp(32),
        if (fullScreen) -1 else -2,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        if (fullScreen) WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.CENTER }
}
