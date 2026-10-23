package com.zikrtime.app

import android.app.Application
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.PrayerAlarms

class ZikrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        PrayerAlarms.createChannels(this)
    }
}
