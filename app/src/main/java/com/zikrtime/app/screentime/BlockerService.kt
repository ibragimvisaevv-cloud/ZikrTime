package com.zikrtime.app.screentime

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.zikrtime.app.data.Store

/**
 * Служба специальных возможностей. Видит только название открытого приложения
 * (содержимое экрана не читается: canRetrieveWindowContent = false).
 * Пока открыта соцсеть из списка, раз в несколько секунд проверяет лимит и время намаза.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPackage: String? = null
    private var ignoredPackages: Set<String> = emptySet()

    private val check = object : Runnable {
        override fun run() {
            val pkg = foregroundPackage ?: return
            if (!Store.blockingEnabled || pkg !in Store.blockedPackages) return

            val power = getSystemService(PowerManager::class.java)
            if (power.isInteractive) {
                val reason = ScreenTime.evaluate(this@BlockerService)
                if (reason !is BlockReason.None) {
                    block(reason)
                    return
                }
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        ignoredPackages = buildIgnoredPackages()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Клавиатура, шторка уведомлений и наш собственный экран не меняют «текущее приложение».
        if (pkg in ignoredPackages) return

        foregroundPackage = pkg
        handler.removeCallbacks(check)
        if (Store.blockingEnabled && pkg in Store.blockedPackages) handler.post(check)
    }

    private fun block(reason: BlockReason) {
        foregroundPackage = null
        handler.removeCallbacks(check)
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(BlockActivity.intent(this, reason))
    }

    private fun buildIgnoredPackages(): Set<String> {
        val imm = getSystemService(InputMethodManager::class.java)
        val keyboards = imm?.enabledInputMethodList?.map { it.packageName }.orEmpty()
        return keyboards.toSet() + setOf(packageName, "com.android.systemui", "android")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 5_000L
    }
}
