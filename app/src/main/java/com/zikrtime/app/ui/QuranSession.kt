package com.zikrtime.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.zikrtime.app.data.*
import kotlinx.coroutines.delay
import java.time.Instant

internal fun Context.readerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.readerActivity()
    else -> null
}

/** Monotonic elapsed time; inactive intervals and process downtime are never counted. */
internal class QuranSession(private val progress: QuranProgress) {
    var timer by mutableStateOf(progress.timer)
        private set
    private var lastTick = SystemClock.elapsedRealtime()
    private var wasActive = false
    private var pending = 0L
    private var started: Instant? = null

    fun tick(active: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastTick
        lastTick = now
        val counted = timer.readingMillis(delta, active && wasActive)
        wasActive = active
        if (counted > 0) {
            if (started == null) started = Instant.now().minusMillis(counted)
            pending += counted
            timer = timer.advance(counted)
        }
        if (!active || timer.finished || pending >= 5000) flush()
    }

    fun flush() {
        val start = started
        if (start != null && pending > 0) QuranStatistics.recordTime(start, pending)
        pending = 0
        started = null
        progress.timer = timer
    }

    fun setTimer(next: QuranReadingTimer) {
        tick(false)
        timer = next
        progress.timer = next
    }
}

@Composable
internal fun rememberQuranSession(progress: QuranProgress, reading: Boolean): QuranSession {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = (context.readerActivity() as? LifecycleOwner)?.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) }
    val session = remember(progress) { QuranSession(progress) }
    val active by rememberUpdatedState(reading && resumed)
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
            if (!resumed) session.tick(false)
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            session.tick(false)
        }
    }
    LaunchedEffect(session, view) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        while (true) {
            session.tick(active && view.hasWindowFocus() && power.isInteractive)
            delay(1000)
        }
    }
    LaunchedEffect(reading) { if (!reading) session.tick(false) }
    return session
}

internal fun readingClock(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1000
    return (seconds / 60).toString().padStart(2, '0') + ":" + (seconds % 60).toString().padStart(2, '0')
}
