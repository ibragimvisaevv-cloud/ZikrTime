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
            .filter { it.first != context.packageName }
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
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        // Берём с запасом в 3 часа до полуночи, чтобы правильно учесть окно, открытое ещё вчера.
        val queryFrom = startOfDay - 3 * 60 * 60 * 1000L

        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(queryFrom, now) ?: return Snapshot(0L, emptyMap())
        val list = ArrayList<UsageEventLite>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            if (pkg !in packages) continue
            when (e.eventType) {
                EVENT_RESUMED -> list.add(UsageEventLite(pkg, e.className ?: "", true, e.timeStamp))
                EVENT_PAUSED -> list.add(UsageEventLite(pkg, e.className ?: "", false, e.timeStamp))
            }
        }
        return Snapshot(UsageMath.foregroundMillis(list, startOfDay, now), UsageMath.openPackages(list))
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
