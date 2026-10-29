package com.zikrtime.app

import android.app.Application
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.PrayerAlarms

class ZikrApp : Application() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshWidgets = Runnable { com.zikrtime.app.widget.Widgets.updateAll(this) }
    private val widgetChanges = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("city", "city_id", "lat", "lon", "method", "madhab", "appearance", "language", "online_times", "online_updated_at", "onboarded") || key?.startsWith("adj_") == true) {
            handler.removeCallbacks(refreshWidgets)
            handler.postDelayed(refreshWidgets, 200)
        }
    }
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        getSharedPreferences("zikrtime", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(widgetChanges)
        PrayerAlarms.createChannels(this)
        com.zikrtime.app.ui.initYandexMapKey()
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.removeCallbacks(refreshWidgets); handler.post(refreshWidgets)
    }
}
