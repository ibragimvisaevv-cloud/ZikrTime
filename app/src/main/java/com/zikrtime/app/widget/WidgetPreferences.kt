package com.zikrtime.app.widget

import android.content.Context
import android.content.res.Configuration
import com.zikrtime.app.data.Store

object WidgetPreferences {
    private fun prefs(c: Context) = c.getSharedPreferences("widget_appearance", Context.MODE_PRIVATE)
    fun theme(c: Context, id: Int) = prefs(c).getString("theme_$id", "app") ?: "app"
    fun dates(c: Context, id: Int) = prefs(c).getBoolean("dates_$id", true)
    fun save(c: Context, id: Int, theme: String, dates: Boolean) {
        prefs(c).edit().putString("theme_$id", theme).putBoolean("dates_$id", dates).apply()
    }
    fun remove(c: Context, id: Int) { prefs(c).edit().remove("theme_$id").remove("dates_$id").apply() }
    fun restore(c: Context, old: Int, new: Int) { save(c, new, theme(c, old), dates(c, old)); remove(c, old) }
    fun followsSystem(c: Context, id: Int): Boolean = theme(c,id) == "system" || (theme(c,id) == "app" && Store.appearance !in listOf("light", "dark"))
    fun dark(c: Context, id: Int): Boolean = when (val mode = theme(c, id)) {
        "light" -> false
        "dark" -> true
        else -> when (if (mode == "app") Store.appearance else "system") {
            "light" -> false
            "dark" -> true
            else -> c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
