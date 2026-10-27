package com.zikrtime.app.screentime

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.i18n.Lang

/** Only a fallback entry point; completing a pause remains in the normal accessible Activity. */
object PauseOverlay {
    private var view: View?=null
    var target: String?=null; private set
    fun show(context: Context,pkg: String) {
        if(view!=null || PauseActivity.visible || !FocusPause.eligible(context,pkg) || !GuardService.canDrawOverlays(context)) return
        val wrapped=Lang.wrap(context)
        val dp=context.resources.displayMetrics.density
        val root=LinearLayout(wrapped).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
            setPadding((24*dp).toInt(),(24*dp).toInt(),(24*dp).toInt())
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xFF263648.toInt(),0xFF16212E.toInt()))
        }
        root.addView(TextView(wrapped).apply { setText(R.string.pause_title); textSize=26f; setTextColor(0xFFF0F4F8.toInt()); gravity=Gravity.CENTER })
        fun button(label: Int,id: String,action:()->Unit) {
            root.addView(Button(wrapped).apply {
                setText(label); contentDescription=id; setOnClickListener { action() }
                layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=(12*dp).toInt() }
            })
        }
        button(R.string.pause_title,"pause_overlay_open") { hide(context); runCatching { context.startActivity(PauseActivity.intent(context,pkg)) } }
        button(R.string.pause_exit,"pause_exit") { hide(context); context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        button(R.string.pause_settings,"pause_settings") { hide(context); context.startActivity(Intent(context,MainActivity::class.java).putExtra("open_focus",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
        button(R.string.pause_disable,"pause_disable") { FocusPause.setEnabled(context,false) }
        // Non-modal to system bars: calls, notification shade and system permissions remain reachable.
        val params=WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.CENTER }
        runCatching { context.getSystemService(WindowManager::class.java).addView(root,params); view=root; target=pkg }
    }
    fun hide(context: Context) {
        val old=view ?: return
        view=null; target=null
        runCatching { context.getSystemService(WindowManager::class.java).removeView(old) }
    }
}
