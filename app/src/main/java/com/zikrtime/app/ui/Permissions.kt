package com.zikrtime.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.alarm.FajrAlarm
import com.zikrtime.app.prayer.PrayerAlarms

/** Разрешения, без которых не работают азан, будильник и блокировка. */
object Perms {
    fun notifications(c: Context) = NotificationManagerCompat.from(c).areNotificationsEnabled()
    fun exact(c: Context) = PrayerAlarms.canScheduleExact(c)
    fun fullScreen(c: Context) = FajrAlarm.canFullScreen(c)
    fun overlay(c: Context) = Settings.canDrawOverlays(c)
    fun battery(c: Context) = c.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
    val xiaomi: Boolean get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    /** Всё ли важное разрешено (Xiaomi-разрешение проверить нельзя — не учитываем). */
    fun allGranted(c: Context) = notifications(c) && exact(c) && fullScreen(c) && overlay(c) && battery(c)

    fun open(c: Context, action: String, withPackage: Boolean = true) {
        runCatching {
            c.startActivity(
                Intent(action).apply { if (withPackage) data = Uri.parse("package:${c.packageName}") }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching {
                c.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun openXiaomi(c: Context) {
        val miui = Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", c.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(miui) }.onFailure { open(c, Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }
    }
}

/** Список важных разрешений с кнопками «Разрешить»; разрешённые отмечены галочкой. */
@Composable
fun EssentialPermissions(showGranted: Boolean = true) {
    val context = LocalContext.current
    val resume = AppEvents.resumeCount
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { AppEvents.resumeCount++ }
    val notif = remember(resume) { Perms.notifications(context) }
    val exact = remember(resume) { Perms.exact(context) }
    val full = remember(resume) { Perms.fullScreen(context) }
    val overlay = remember(resume) { Perms.overlay(context) }
    val battery = remember(resume) { Perms.battery(context) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showGranted || !notif) {
            PermissionRow(stringResource(R.string.perm_notifications_title), stringResource(R.string.perm_notifications_text), notif) {
                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 31 && (showGranted || !exact)) {
            PermissionRow(stringResource(R.string.perm_exact_title), stringResource(R.string.perm_exact_text), exact) {
                Perms.open(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && (showGranted || !full)) {
            PermissionRow(stringResource(R.string.perm_fullscreen_title), stringResource(R.string.perm_fullscreen_text), full) {
                Perms.open(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        }
        if (showGranted || !overlay) {
            PermissionRow(stringResource(R.string.perm_overlay_title), stringResource(R.string.perm_overlay_both_text), overlay) {
                Perms.open(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            }
        }
        if (showGranted || !battery) {
            PermissionRow(stringResource(R.string.perm_battery_title), stringResource(R.string.perm_battery_text), battery) {
                Perms.open(context, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            }
        }
        if (Perms.xiaomi) {
            PermissionRow(stringResource(R.string.perm_xiaomi_title), stringResource(R.string.perm_xiaomi_text), false) {
                Perms.openXiaomi(context)
            }
        }
    }
}
