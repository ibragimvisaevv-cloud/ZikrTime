package com.zikrtime.app.screentime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import org.json.JSONObject

/** Additive opt-in preferences, separate from statistics; intentions stay only on this device. */
object FocusPause {
    var version by mutableIntStateOf(0); private set
    var deliveryFailed by mutableStateOf(false); internal set
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("focus_pause_v1", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun strict(context: Context) = prefs(context).getBoolean("strict", false)
    fun phrase(context: Context) = prefs(context).getInt("phrase", 0).coerceIn(0, 2)
    fun setStrict(context: Context, value: Boolean) { prefs(context).edit().putBoolean("strict", value).apply(); version++; BlockerService.recheck() }
    fun setPhrase(context: Context, value: Int) { prefs(context).edit().putInt("phrase", value.coerceIn(0,2)).apply(); version++ }
    fun setEnabled(context: Context, value: Boolean) {
        val editor = prefs(context).edit().putBoolean("enabled", value)
        if (!value) prefs(context).all.keys.filter { it.startsWith("session:") || it.startsWith("visit:") }.forEach { editor.remove(it) }
        editor.apply(); version++
        deliveryFailed = false
        if (!value) {
            PauseActivity.closeIfVisible()
            PauseOverlay.hide(context)
            context.getSystemService(NotificationManager::class.java).cancel(81)
            context.getSystemService(NotificationManager::class.java).cancel(82)
        }
        GuardService.sync(context)
        BlockerService.recheck()
    }
    fun selectionChanged(context: Context) {
        val editor=prefs(context).edit()
        prefs(context).all.keys.filter { it.startsWith("session:") || it.startsWith("visit:") }
            .filter { it.substringAfter(":") !in Store.blockedPackages }.forEach { editor.remove(it) }
        editor.apply(); version++
        if (PauseOverlay.target !in Store.blockedPackages) PauseOverlay.hide(context)
    }
    fun session(context: Context, pkg: String): PauseSession? = runCatching {
        val j = JSONObject(prefs(context).getString("session:$pkg", null) ?: return null)
        PauseSession(j.getLong("start"), j.getLong("budget"), j.optLong("consumed"), j.optBoolean("reminded"), j.optString("intention"), j.optBoolean("expiryHandled"))
    }.getOrNull()
    private fun save(context: Context, pkg: String, s: PauseSession, openedAt: Long? = null) {
        val j = JSONObject().put("start",s.startedAt).put("budget",s.budgetMillis).put("consumed",s.consumedMillis)
            .put("reminded",s.reminderSent).put("intention",s.intention).put("expiryHandled",s.expiryHandled)
        val editor = prefs(context).edit().putString("session:$pkg",j.toString())
        if (openedAt != null) editor.putLong("visit:$pkg",openedAt)
        editor.apply()
    }
    fun eligible(context: Context, pkg: String) = enabled(context) && pkg in Store.blockedPackages && ScreenTime.isSafeTarget(context,pkg)
    fun grant(context: Context, pkg: String, requestedMinutes: Int, intention: String): Boolean {
        if (!eligible(context,pkg) || !ScreenTime.hasUsageAccess(context)) return false
        val now = System.currentTimeMillis()
        val used = ScreenTime.usedTodayMillis(context,now) ?: return false
        if (ScreenTime.evaluate(context,usedMillis=used) !is BlockReason.None) return false
        val remaining = if (Store.blockingEnabled) Store.allowedMinutes()*60_000L-used else null
        val budget = PausePolicy.budget(requestedMinutes,remaining)
        if (budget <= 0) return false
        save(context,pkg,PauseSession(now,budget,intention=intention.take(120)),0)
        version++
        return true
    }
    fun needsPause(context: Context, pkg: String, openedAt: Long, existingReason: BlockReason): Boolean {
        if (!eligible(context,pkg) || existingReason !is BlockReason.None || !ScreenTime.hasUsageAccess(context)) return false
        val s = session(context,pkg) ?: return true
        val now = System.currentTimeMillis()
        val measured = ScreenTime.usageWindow(context,s.startedAt,now,setOf(pkg))?.usedMillis ?: return false
        val consumed = maxOf(s.consumedMillis,measured)
        val remaining = PausePolicy.remaining(s,measured,now)
        val priorVisit = prefs(context).getLong("visit:$pkg",0)
        if (remaining > 0) {
            if (consumed != s.consumedMillis || priorVisit != openedAt) save(context,pkg,s.copy(consumedMillis=consumed),openedAt)
            return false
        }
        if (PausePolicy.needsPause(true,strict(context),s,measured,now,openedAt,priorVisit,existingReason)) {
            // Persist exhaustion too: system history may later be trimmed or the clock moved back.
            save(context,pkg,s.copy(consumedMillis=maxOf(consumed,s.budgetMillis),intention=""))
            return true
        }
        if (!s.reminderSent && !s.expiryHandled) {
            val accepted=remind(context)
            save(context,pkg,s.copy(consumedMillis=consumed,reminderSent=accepted,expiryHandled=true,intention=""),openedAt)
            return false
        }
        return false
    }
    fun remainingMinutes(context: Context, pkg: String): Long? {
        val s=session(context,pkg)?:return null
        val now=System.currentTimeMillis()
        val measured=ScreenTime.usageWindow(context,s.startedAt,now,setOf(pkg))?.usedMillis?:return null
        return (PausePolicy.remaining(s,measured,now)+59_999)/60_000
    }
    fun deliveryUnavailable(context: Context,pkg: String) {
        deliveryFailed=true
        val res=com.zikrtime.app.i18n.Lang.wrap(context).resources
        val nm=context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("focus_pause",res.getString(R.string.pause_title),NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,82,PauseActivity.intent(context,pkg),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { nm.notify(82,NotificationCompat.Builder(context,"focus_pause").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.pause_title)).setContentText(res.getString(R.string.pause_pending))
            .setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true).build()) }
    }
    fun notificationsAvailable(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                context,android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        val nm=context.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled() && nm.getNotificationChannel("focus_pause")?.importance != NotificationManager.IMPORTANCE_NONE
    }
    /** True means accepted by NotificationManager, not proof that the user saw it. */
    private fun remind(context: Context): Boolean {
        if (!notificationsAvailable(context)) return false
        val res=com.zikrtime.app.i18n.Lang.wrap(context).resources
        val nm=context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("focus_pause",res.getString(R.string.pause_title),NotificationManager.IMPORTANCE_DEFAULT))
        val intent=Intent(context,MainActivity::class.java).putExtra("open_focus",true)
        val open=PendingIntent.getActivity(context,81,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return runCatching { nm.notify(81,NotificationCompat.Builder(context,"focus_pause").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.pause_title)).setContentText(res.getString(R.string.pause_reminder))
            .setContentIntent(open).setAutoCancel(true).build()) }.isSuccess
    }
}
