package com.zikrtime.app.screentime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        running = true
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
        if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            PauseOverlay.hide(this)
            hideOverlay(this)
            return CHECK_SCREEN_OFF_MS
        }
        val now = System.currentTimeMillis()
        if (now - lastWidgetUpdate > 60_000) {
            lastWidgetUpdate = now
            runCatching { com.zikrtime.app.widget.Widgets.updateScreenTime(this) }
        }
        val snapshot = ScreenTime.snapshot(this, now) ?: run {
            PauseOverlay.hide(this); hideOverlay(this); return CHECK_SCREEN_OFF_MS
        }
        val open = snapshot.openPackages.filterKeys { ScreenTime.isSafeTarget(this,it) }.maxByOrNull { it.value }
        if (open == null) { PauseOverlay.hide(this); hideOverlay(this); return CHECK_MS }
        val reason = ScreenTime.evaluate(this, usedMillis = snapshot.usedMillis)
        if (reason !is BlockReason.None) {
            PauseOverlay.hide(this)
            if (open.value > (lastBlocked[open.key] ?: 0L)) {
                lastBlocked[open.key] = now
                block(reason)
            }
        } else {
            hideOverlay(this)
            if (FocusPause.needsPause(this,open.key,open.value,reason)) PauseGate.show(this,open.key)
            else PauseOverlay.hide(this)
        }
        return CHECK_MS
    }

    /**
     * Показываем экран блокировки. Некоторые телефоны (Xiaomi, Huawei и др.) не дают открывать экраны из фона —
     * тогда через секунду показываем запасной экран прямо поверх приложений (разрешение «Поверх других приложений»).
     */
    private fun block(reason: BlockReason) {
        runCatching { startActivity(BlockActivity.intent(this, reason)) }
        handler.postDelayed({
            if (!BlockActivity.visible && ScreenTime.hasUsageAccess(this) &&
                ScreenTime.snapshot(this)?.openPackages?.keys?.any { ScreenTime.isSafeTarget(this,it) } == true &&
                ScreenTime.evaluate(this) !is BlockReason.None) showOverlay(this, reason)
        }, 1_200)
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
            .setContentText(if (Store.blockingEnabled) res.getString(R.string.guard_text, Store.dailyLimitMinutes) else res.getString(R.string.pause_title))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        running = false
        PauseOverlay.hide(this)
        handler.removeCallbacksAndMessages(null)
        hideOverlay(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var running by mutableStateOf(false); private set
        private const val CHANNEL_ID = "guard"
        private const val NOTIFICATION_ID = 7
        private const val CHECK_MS = 3_000L
        private const val CHECK_SCREEN_OFF_MS = 15_000L

        fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

        private const val ACTION_CHECK = "check"
        private var overlay: View? = null

        /** Запасной экран блокировки поверх всех приложений. */
        fun showOverlay(context: Context, reason: BlockReason) {
            if (overlay != null || !canDrawOverlays(context)) return
            val res = com.zikrtime.app.i18n.Lang.wrap(context).resources
            val dp = context.resources.displayMetrics.density
            val wm = context.getSystemService(WindowManager::class.java)
            val title = when (reason) {
                is BlockReason.PrayerTime -> res.getString(R.string.block_prayer_label)
                is BlockReason.LimitReached -> res.getString(R.string.block_limit_title)
                BlockReason.None -> return
            }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((32 * dp).toInt(), 0, (32 * dp).toInt(), 0)
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0F5C4A.toInt(), 0xFF04231C.toInt()))
                isClickable = true
            }
            fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            }
            fun button(value: String, fill: Int, textColor: Int, onClick: () -> Unit) = TextView(context).apply {
                text = value
                textSize = 17f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = GradientDrawable().apply { setColor(fill); cornerRadius = 18 * dp }
                setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (14 * dp).toInt() }
            }
            root.addView(text("☪", 44f, 0xFFE2B659.toInt()))
            root.addView(text(title, 26f, 0xFFFFF6E0.toInt(), bold = true))
            root.addView(text(res.getString(R.string.slogan), 16f, 0xFFE2B659.toInt()))
            root.addView(button(res.getString(R.string.block_open_app), 0xFFE2B659.toInt(), 0xFF2B1E00.toInt()) {
                hideOverlay(context)
                context.startActivity(BlockActivity.intent(context, reason))
            })
            root.addView(button(res.getString(R.string.block_go_home), 0x33FFFFFF, 0xFFFFF6E0.toInt()) {
                hideOverlay(context)
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            runCatching {
                wm.addView(root, params)
                overlay = root
            }
        }

        fun hideOverlay(context: Context) {
            val v = overlay ?: return
            overlay = null
            runCatching { context.getSystemService(WindowManager::class.java).removeView(v) }
        }

        /**
         * Запасной режим нужен, когда ограничение включено, а служба специальных возможностей — нет.
         * Если служба включена, она работает быстрее и надёжнее, и эта служба не нужна.
         */
        fun shouldRun(context: Context): Boolean =
            (Store.blockingEnabled || FocusPause.enabled(context)) &&
                !BlockerService.running &&
                canDrawOverlays(context) &&
                ScreenTime.hasUsageAccess(context)

        /** Запустить или остановить службу в зависимости от настроек и разрешений. */
        fun sync(context: Context, checkNow: Boolean = false) {
            val intent = Intent(context, GuardService::class.java).apply { if (checkNow) action = ACTION_CHECK }
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
