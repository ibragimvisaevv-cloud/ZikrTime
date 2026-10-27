package com.zikrtime.app.screentime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Debounce both service paths; background-start restrictions must not produce repeated windows. */
object PauseGate {
    private val handler=Handler(Looper.getMainLooper())
    private var attemptedAt=0L
    private var attemptedPackage=""
    fun show(context: Context,pkg: String) {
        if(PauseActivity.visible || PauseOverlay.target==pkg || BlockActivity.visible) return
        val now=SystemClock.elapsedRealtime()
        if(attemptedPackage==pkg && now-attemptedAt<10_000) return
        attemptedAt=now; attemptedPackage=pkg
        runCatching { context.startActivity(PauseActivity.intent(context,pkg)) }
        handler.postDelayed({
            if(PauseActivity.visible || !FocusPause.eligible(context,pkg) || !ScreenTime.hasUsageAccess(context)) return@postDelayed
            if(!context.getSystemService(android.os.PowerManager::class.java).isInteractive || context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return@postDelayed
            if(BlockerService.running && !BlockerService.isForeground(pkg)) return@postDelayed
            val snapshot=ScreenTime.snapshot(context) ?: return@postDelayed
            val openedAt=snapshot.openPackages[pkg] ?: return@postDelayed
            val reason=ScreenTime.evaluate(context,usedMillis=snapshot.usedMillis)
            if(reason !is BlockReason.None || !FocusPause.needsPause(context,pkg,openedAt,reason)) return@postDelayed
            if(GuardService.canDrawOverlays(context)) PauseOverlay.show(context,pkg)
            else FocusPause.deliveryUnavailable(context,pkg)
        },1_200)
    }
}
