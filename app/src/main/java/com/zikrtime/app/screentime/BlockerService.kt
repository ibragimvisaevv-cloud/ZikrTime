package com.zikrtime.app.screentime

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zikrtime.app.data.Store

/** Reads package transitions only, never screen contents (canRetrieveWindowContent=false). */
class BlockerService : AccessibilityService() {
    private val handler=Handler(Looper.getMainLooper())
    private var foregroundPackage: String?=null
    private var openedAt=0L
    private var keyboards: Set<String> = emptySet()
    private val check=object: Runnable {
        override fun run() {
            if((!Store.blockingEnabled && !FocusPause.enabled(this@BlockerService)) || Store.blockedPackages.isEmpty()) return
            val interactive=getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked && !ScreenTime.hasActiveCall(this@BlockerService)
            if(interactive && ScreenTime.hasUsageAccess(this@BlockerService)) {
                val snapshot=ScreenTime.snapshot(this@BlockerService)
                if(snapshot!=null) {
                    if(foregroundPackage==null) recoverForeground(snapshot)
                    val pkg=foregroundPackage
                    if(pkg!=null) {
                        if(pkg !in Store.blockedPackages || !ScreenTime.isSafeTarget(this@BlockerService,pkg)) return
                        val reason=ScreenTime.evaluate(this@BlockerService,usedMillis=snapshot.usedMillis)
                        if(reason !is BlockReason.None) { block(reason); return }
                        if(FocusPause.needsPause(this@BlockerService,pkg,snapshot.openPackages[pkg] ?: openedAt,reason)) PauseGate.show(this@BlockerService,pkg)
                        else PauseOverlay.hide(this@BlockerService)
                    }
                }
            } else { PauseOverlay.hide(this@BlockerService); GuardService.hideOverlay(this@BlockerService) }
            // UsageEvents can arrive shortly after reconnect. Retry an unknown foreground;
            // a later explicit non-target window event still stops this timer normally.
            handler.postDelayed(this,CHECK_INTERVAL_MS)
        }
    }
    private fun recoverForeground(snapshot: ScreenTime.Snapshot) {
        val open=snapshot.openPackages.filterKeys {
            it in Store.blockedPackages && ScreenTime.isSafeTarget(this,it)
        }.maxByOrNull { it.value } ?: return
        foregroundPackage=open.key
        // Preserve the actual visit time; reconnect must not manufacture another soft-mode visit.
        openedAt=open.value
    }
    override fun onServiceConnected() {
        super.onServiceConnected(); Store.init(this)
        instance=this; running=true
        keyboards=getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
        GuardService.sync(this)
        foregroundPackage=null
        handler.removeCallbacks(check)
        handler.post(check)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(event?.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg=event.packageName?.toString() ?: return
        if(pkg in keyboards) return
        // System UI/settings/calls must dismiss overlays, not keep checking a stale social package.
        if(pkg!=foregroundPackage) { foregroundPackage=pkg; openedAt=System.currentTimeMillis() }
        if(pkg !in Store.blockedPackages || !ScreenTime.isSafeTarget(this,pkg)) PauseOverlay.hide(this)
        handler.removeCallbacks(check)
        if((Store.blockingEnabled || FocusPause.enabled(this)) && pkg in Store.blockedPackages) handler.post(check)
    }
    private fun block(reason: BlockReason) {
        foregroundPackage=null; handler.removeCallbacks(check); PauseOverlay.hide(this)
        performGlobalAction(GLOBAL_ACTION_HOME)
        runCatching { startActivity(BlockActivity.intent(this,reason)) }
    }
    override fun onInterrupt() {
        foregroundPackage=null
        handler.removeCallbacks(check)
        PauseOverlay.hide(this)
        handler.postDelayed(check,CHECK_INTERVAL_MS)
    }
    override fun onDestroy() {
        running=false; if(instance===this) instance=null
        handler.removeCallbacksAndMessages(null); PauseOverlay.hide(this)
        GuardService.sync(this)
        super.onDestroy()
    }
    companion object {
        var running by mutableStateOf(false); private set
        private var instance: BlockerService?=null
        private const val CHECK_INTERVAL_MS=3_000L
        fun isForeground(pkg: String)=instance?.foregroundPackage==pkg
        fun recheck() { instance?.let { it.handler.removeCallbacks(it.check); it.handler.post(it.check) } }
    }
}
