package com.zikrtime.app.screentime

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.zikrtime.app.R
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
    private val ink = if (dark) 0xFFFAF7EE.toInt() else 0xFF102D26.toInt()
    val view = FrameLayout(this.context).apply { isClickable = true }
    private val content = LinearLayout(this.context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(28), dp(24), dp(28))
    }
    init {
        if (fullScreen) {
            view.addView(ImageView(this.context).apply {
                setImageResource(R.drawable.horizon_portrait_night)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
            view.addView(View(this.context).apply { setBackgroundColor(0xA6071F1B.toInt()) }, FrameLayout.LayoutParams(-1, -1))
        } else {
            view.background = GradientDrawable().apply {
                setColor(if (dark) 0xFF102A23.toInt() else 0xFFFFFCF5.toInt())
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), if (dark) 0xFF50462F.toInt() else 0xFFE3D6BA.toInt())
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
            text = title; textSize = if (fullScreen) 34f else 28f; setTextColor(ink)
            typeface = Typeface.create("serif", Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        if (subtitle != null) content.addView(TextView(this.context).apply {
            text = subtitle; textSize = 16f; setTextColor(if (dark) 0xFFD9BE87.toInt() else 0xFF80623C.toInt())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12))
        })
    }
    fun button(label: String, id: Int = View.NO_ID, primary: Boolean = false, action: () -> Unit) {
        val fill = if (primary) if (fullScreen) 0xFFD9BE87.toInt() else 0xFF124C40.toInt()
            else if (dark) 0xFF234136.toInt() else 0xFFF0EBDD.toInt()
        content.addView(Button(context).apply {
            text = label; this.id = id; isAllCaps = false; textSize = 16f
            setTextColor(if (primary) if (fullScreen) 0xFF302716.toInt() else Color.WHITE else ink)
            minHeight = dp(52); setPadding(dp(16), dp(14), dp(16), dp(14)); stateListAnimator = null
            val shape = GradientDrawable().apply { setColor(fill); cornerRadius = dp(18).toFloat() }
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
