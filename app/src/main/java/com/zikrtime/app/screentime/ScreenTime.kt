package com.zikrtime.app.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.PrayerSchedule
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class AppInfo(val packageName: String, val label: String)

/** Всё, что связано с разрешениями, учётом времени и решением о блокировке. */
object ScreenTime {

    /** Популярные соцсети и видеоплатформы — выбираются по умолчанию, если установлены. */
    val KNOWN_SOCIAL = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",        // TikTok
        "com.ss.android.ugc.trill",        // TikTok (некоторые регионы)
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.twitter.android",             // X
        "com.vkontakte.android",
        "ru.ok.android",
        "com.snapchat.android",
        "com.pinterest",
        "com.reddit.frontpage",
        "video.like",                      // Likee
    )

    // Коды событий UsageEvents: ACTIVITY_RESUMED / MOVE_TO_FOREGROUND = 1, ACTIVITY_PAUSED / MOVE_TO_BACKGROUND = 2.
    private const val EVENT_RESUMED = 1
    private const val EVENT_PAUSED = 2

    // ---------------- Разрешения ----------------

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------------- Приложения ----------------

    fun launchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(intent, 0)
        return activities
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { isSafeTarget(context, it.first) }
            .distinctBy { it.first }
            .map { AppInfo(it.first, it.second) }
            .sortedWith(compareByDescending<AppInfo> { it.packageName in KNOWN_SOCIAL }.thenBy { it.label.lowercase() })
    }

    /** При первом открытии отмечаем установленные соцсети из списка выше. */
    fun ensureDefaultSelection(apps: List<AppInfo>) {
        if (Store.appsInitialized) return
        Store.blockedPackages = apps.map { it.packageName }.filter { it in KNOWN_SOCIAL }.toSet()
        Store.appsInitialized = true
    }

    // ---------------- Учёт времени ----------------

    /** Снимок за сегодня: сколько выбранные приложения были на экране и какие из них открыты сейчас. */
    class Snapshot(val usedMillis: Long, val openPackages: Map<String, Long>)

    /** null — нет доступа к статистике использования. */
    fun snapshot(context: Context, now: Long = System.currentTimeMillis()): Snapshot? {
        if (!hasUsageAccess(context)) return null
        val packages = Store.blockedPackages
        if (packages.isEmpty()) return Snapshot(0L, emptyMap())

        val zone = ZoneId.systemDefault()
        val startOfDay = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        // Берём с запасом в 3 часа до полуночи, чтобы правильно учесть окно, открытое ещё вчера.
        val queryFrom = startOfDay - 3 * 60 * 60 * 1000L

        return usageWindow(context, startOfDay, now, packages, queryFrom)
    }

    /** Single foreground counter shared by daily limits and pause sessions. */
    fun usageWindow(context: Context, from: Long, now: Long, packages: Set<String>, queryFrom: Long = from - 3 * 60 * 60 * 1000L): Snapshot? {
        if (!hasUsageAccess(context)) return null
        if (now < from) return Snapshot(0, emptyMap())
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = runCatching { usm.queryEvents(queryFrom.coerceAtLeast(0), now) }.getOrNull() ?: return null
        val list = ArrayList<UsageEventLite>()
        val keyboards=context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            // Numeric codes keep API 26 compatibility (screen/keyguard events arrived at API 28).
            val state=when(e.eventType) {
                15 -> UsageEventLite("","",false,e.timeStamp,screenOn=true)
                16 -> UsageEventLite("","",false,e.timeStamp,screenOn=false)
                17 -> UsageEventLite("","",false,e.timeStamp,unlocked=false)
                18 -> UsageEventLite("","",false,e.timeStamp,unlocked=true)
                26,27 -> UsageEventLite("","",false,e.timeStamp,reset=true)
                else -> null
            }
            if(state!=null) { list.add(state); continue }
            val pkg = e.packageName ?: continue
            if (pkg !in packages) {
                // A real foreground transition also closes stale intervals when a vendor omits PAUSED.
                // The input method is part of the current app interaction, not a separate visit.
                if(e.eventType==EVENT_RESUMED && pkg !in keyboards) list.add(UsageEventLite("","",false,e.timeStamp,reset=true))
                continue
            }
            when (e.eventType) {
                EVENT_RESUMED -> list.add(UsageEventLite(pkg, e.className ?: "", true, e.timeStamp))
                EVENT_PAUSED -> list.add(UsageEventLite(pkg, e.className ?: "", false, e.timeStamp))
            }
        }
        return Snapshot(UsageMath.foregroundMillis(list, from, now), UsageMath.openPackages(list))
    }

    /** Includes VoIP calls inside a selected messenger, without requesting phone-state access. */
    fun hasActiveCall(context: Context): Boolean = context.getSystemService(android.media.AudioManager::class.java).mode in setOf(
        android.media.AudioManager.MODE_RINGTONE, android.media.AudioManager.MODE_IN_CALL, android.media.AudioManager.MODE_IN_COMMUNICATION,
    )

    /** Never intercept system UI, emergency/phone handlers, settings or this app. */
    fun isSafeTarget(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName || pkg in setOf("android", "com.android.systemui", "com.android.settings")) return false
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        if (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 && pkg !in KNOWN_SOCIAL) return false
        val protected = listOf(Intent(Settings.ACTION_SETTINGS), Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
        return pkg !in protected && !pkg.contains("permissioncontroller") && !pkg.contains("emergency") && !pkg.contains("dialer")
    }

    /** Сколько миллисекунд выбранные приложения были открыты сегодня. null — нет доступа к статистике. */
    fun usedTodayMillis(context: Context, now: Long = System.currentTimeMillis()): Long? =
        snapshot(context, now)?.usedMillis

    // ---------------- Решение ----------------

    fun evaluate(
        context: Context,
        now: ZonedDateTime = ZonedDateTime.now(),
        usedMillis: Long? = usedTodayMillis(context, now.toInstant().toEpochMilli()),
    ): BlockReason {
        if (!Store.blockingEnabled) return BlockReason.None
        // Проверка из настроек: на минуту ведём себя так, будто наступило время намаза.
        val test = Store.testLockUntil
        if (test > now.toInstant().toEpochMilli()) {
            return BlockReason.PrayerTime(PrayerSchedule.next(now).first, now.toLocalDate(), java.time.Instant.ofEpochMilli(test))
        }
        // Блокировка на намаз снимается, если намаз отмечен как совершённый.
        val starts = PrayerSchedule.recentStarts(now).filter {
            it.prayer !in Store.lockDismissed(it.date) && it.prayer !in Store.prayedOn(it.date)
        }
        return BlockPolicy.decide(
            now = now.toInstant(),
            prayerStarts = starts,
            prayerLockEnabled = Store.prayerLockEnabled,
            prayerLockMinutes = Store.prayerLockMinutes,
            usedMillis = usedMillis,
            allowedMinutes = Store.allowedMinutes(now.toLocalDate()),
        )
    }
}
