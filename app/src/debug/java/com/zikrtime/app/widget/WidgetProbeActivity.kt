package com.zikrtime.app.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.ui.CityZones
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

/** Disposable emulator instrumentation only. Excluded from release builds. No fabricated schedule. */
class WidgetProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); Store.init(this)
        val manager=AppWidgetManager.getInstance(this)
        val provider=intent.getStringExtra("provider") ?: "CompactPrayerWidget"
        val component=ComponentName(this,"com.zikrtime.app.widget.$provider")
        when(intent.getStringExtra("action")) {
            "city" -> Store.setLocation(com.zikrtime.app.data.Cities.load(this).second.first { it.id == intent.getStringExtra("city") }.toCity("ru"))
            "adjust" -> Store.setAdjustment(com.zikrtime.app.prayer.Prayer.DHUHR, intent.getStringExtra("minutes")?.toInt() ?: 0)
            "wallpaper" -> {
                val bitmap=android.graphics.Bitmap.createBitmap(1080,1920,android.graphics.Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(bitmap); val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val mode=intent.getStringExtra("theme")
                canvas.drawColor(if(mode=="light") 0xFFF7EFDC.toInt() else 0xFF101E26.toInt())
                if(mode=="busy") for(i in 0..12) {
                    paint.color=intArrayOf(0xFFD4A470.toInt(),0xFF668F89.toInt(),0xFFABC4B0.toInt(),0xFF4E617A.toInt())[i%4]
                    canvas.drawCircle(((i*317)%1080).toFloat(),((i*257)%1920).toFloat(),340f,paint)
                }
                android.app.WallpaperManager.getInstance(this).setBitmap(bitmap)
            }
            "pin" -> {
                val callback=PendingIntent.getBroadcast(this,provider.hashCode(),Intent(this,WidgetProbeReceiver::class.java).putExtra("provider",provider),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                manager.requestPinAppWidget(component,null,callback)
            }
            "theme" -> {
                manager.getAppWidgetIds(component).forEach { WidgetPreferences.save(this,it,intent.getStringExtra("theme") ?: "app",true) }
                Widgets.updateAll(this)
            }
            "refresh" -> Widgets.updateAll(this)
        }
        dump(this)
        finish()
    }
    companion object {
        fun dump(c:Context) {
            val now=ZonedDateTime.now(CityZones.forCity(Store.location.id)); val next=PrayerSchedule.next(now)
            val widgets=JSONArray(); val m=AppWidgetManager.getInstance(c)
            listOf("CompactPrayerWidget","TimelineWidget","NextPrayerWidget","TodayWidget","TasbihWidget","ScreenTimeWidget").forEach { name ->
                m.getAppWidgetIds(ComponentName(c,"com.zikrtime.app.widget.$name")).forEach { id ->
                    val o=m.getAppWidgetOptions(id)
                    widgets.put(JSONObject().put("provider",name).put("id",id).put("minWidth",o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)).put("maxHeight",o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)).put("options",o.toString()))
                }
            }
            c.filesDir.resolve("widget-probe.json").writeText(JSONObject().put("now",now.toString()).put("next",next.first.name).put("at",next.second.toString())
                .put("widgets",widgets).put("isha",PrayerSchedule.forDate(now.toLocalDate(),now.zone)[com.zikrtime.app.prayer.Prayer.ISHA].toString()).put("date",now.toLocalDate().toString()).put("city",Store.location.name).toString(2))
        }
    }
}
class WidgetProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) { Widgets.updateAll(context); WidgetProbeActivity.dump(context) }
}
