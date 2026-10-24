package com.zikrtime.app.screentime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Store

/**
 * Запасной режим блокировки — без службы специальных возможностей.
 * Работает как фоновая служба со значком в уведомлениях: раз в несколько секунд смотрит
 * статистику использования и, если открыта соцсеть из списка, показывает экран блокировки
 * поверх неё (для этого нужно разрешение «Показ поверх других приложений»).
 */
class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    /** Когда мы в последний раз закрывали каждое приложение — чтобы не показывать экран повторно по старому событию. */
    private val lastBlocked = HashMap<String, Long>()

    private val tick = object : Runnable {
        override fun run() {
            val delay = check()
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!shouldRun(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private var lastWidgetUpdate = 0L

    /** Одна проверка. Возвращает, через сколько миллисекунд проверить снова. */
    private fun check(): Long {
        if (!shouldRun(this)) {
            stopSelf()
            return CHECK_SCREEN_OFF_MS
        }
        if (!getSystemService(PowerManager::class.java).isInteractive) return CHECK_SCREEN_OFF_MS

        val now = System.currentTimeMillis()
        // Раз в минуту обновляем виджет «Экранное время».
        if (now - lastWidgetUpdate > 60_000) {
            lastWidgetUpdate = now
            runCatching { com.zikrtime.app.widget.Widgets.updateScreenTime(this) }
        }
        val snapshot = ScreenTime.snapshot(this, now) ?: return CHECK_SCREEN_OFF_MS
        // Открытая соцсеть, которую мы ещё не закрывали после её последнего открытия.
        val open = snapshot.openPackages.filter { (pkg, openedAt) -> openedAt > (lastBlocked[pkg] ?: 0L) }
        if (open.isEmpty()) return CHECK_MS

        val reason = ScreenTime.evaluate(this, usedMillis = snapshot.usedMillis)
        if (reason !is BlockReason.None) {
            open.keys.forEach { lastBlocked[it] = now }
            startActivity(BlockActivity.intent(this, reason))
        }
        return CHECK_MS
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val res = com.zikrtime.app.i18n.Lang.wrap(this).resources
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.guard_title))
            .setContentText(res.getString(R.string.guard_text, Store.dailyLimitMinutes))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "guard"
        private const val NOTIFICATION_ID = 7
        private const val CHECK_MS = 3_000L
        private const val CHECK_SCREEN_OFF_MS = 15_000L

        fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

        /**
         * Запасной режим нужен, когда ограничение включено, а служба специальных возможностей — нет.
         * Если служба включена, она работает быстрее и надёжнее, и эта служба не нужна.
         */
        fun shouldRun(context: Context): Boolean =
            Store.blockingEnabled &&
                !ScreenTime.isAccessibilityEnabled(context) &&
                canDrawOverlays(context) &&
                ScreenTime.hasUsageAccess(context)

        /** Запустить или остановить службу в зависимости от настроек и разрешений. */
        fun sync(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            if (shouldRun(context)) {
                try {
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
                } catch (_: Exception) {
                    // Система может запретить запуск из фона — повторим, когда пользователь откроет приложение.
                }
            } else {
                context.stopService(intent)
            }
        }

        fun createChannel(context: Context) {
            val res = com.zikrtime.app.i18n.Lang.wrap(context).resources
            val channel = NotificationChannel(CHANNEL_ID, res.getString(R.string.channel_guard), NotificationManager.IMPORTANCE_MIN)
                .apply { description = res.getString(R.string.channel_guard_desc) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
