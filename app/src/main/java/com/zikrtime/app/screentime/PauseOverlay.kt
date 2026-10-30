package com.zikrtime.app.screentime

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.WindowManager
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.i18n.Lang

/** Only a fallback entry point; completing a pause remains in the normal accessible Activity. */
object PauseOverlay {
    private var view: View?=null
    var target: String?=null; private set
    fun show(context: Context,pkg: String) {
        if(view!=null || PauseActivity.visible || !FocusPause.eligible(context,pkg) || !GuardService.canDrawOverlays(context)) return
        val res=Lang.wrap(context).resources
        val panel=HorizonOverlay(context,res.getString(R.string.pause_title),res.getString(R.string.slogan))
        panel.button(res.getString(R.string.pause_title),R.id.pause_overlay_open,primary=true) {
            hide(context); runCatching { context.startActivity(PauseActivity.intent(context,pkg)) }
        }
        panel.button(res.getString(R.string.pause_exit),R.id.pause_exit) {
            hide(context); context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        panel.button(res.getString(R.string.pause_settings),R.id.pause_settings) {
            hide(context); context.startActivity(Intent(context,MainActivity::class.java).putExtra("open_focus",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        panel.button(res.getString(R.string.pause_disable),R.id.pause_disable) { FocusPause.setEnabled(context,false) }
        // The compact window keeps system gestures, calls and permission prompts available.
        runCatching { context.getSystemService(WindowManager::class.java).addView(panel.view,panel.params()); view=panel.view; target=pkg }
            .onFailure { android.util.Log.w("ZikrOverlay", "Could not attach pause window", it) }
    }
    fun hide(context: Context) {
        val old=view ?: return
        view=null; target=null
        runCatching { context.getSystemService(WindowManager::class.java).removeView(old) }
    }
}
