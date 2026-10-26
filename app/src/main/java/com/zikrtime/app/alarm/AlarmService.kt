package com.zikrtime.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.AdhanSound

/** Состояние будильника для экрана: звонит ли сейчас. */
object AlarmState {
    var ringing by mutableStateOf(false)
}

/**
 * Служба будильника: громко играет азан по кругу, вибрирует и держит уведомление
 * с полноэкранным экраном будильника. Выключается только вручную.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var savedVolume = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        when (intent?.action) {
            ACTION_STOP -> stopAlarm()
            ACTION_SNOOZE -> {
                Store.alarmSnoozeAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
                FajrAlarm.schedule(this)
                stopAlarm()
            }
            else -> startAlarm(intent?.getBooleanExtra(FajrAlarm.EXTRA_TEST, false) == true)
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(test: Boolean) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(test), type)
        } catch (_: Exception) {
            stopSelf()
            return
        }
        AlarmState.ringing = true
        if (player == null) startSound()
        if (Store.alarmVibrate && vibrator == null) startVibration()
        // Если экран включён и телефон разблокирован, полноэкранное уведомление может показаться баннером —
        // поэтому пробуем открыть экран будильника и напрямую.
        runCatching { startActivity(AlarmActivity.intent(this, test).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        // Подстраховка: если через несколько секунд экран будильника так и не появился (телефон запретил
        // показ поверх других окон), в уведомлении появляется кнопка «Выключить», чтобы звук можно было остановить.
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (AlarmState.ringing && !AlarmActivity.visible) {
                runCatching { startActivity(AlarmActivity.intent(this, test).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                fallbackStop = true
                runCatching { getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, notification(test)) }
            }
        }, 8_000)
        // Никогда не звоним бесконечно: через 20 минут будильник выключается сам.
        handler.postDelayed({ if (AlarmState.ringing) stopAlarm() }, AUTO_STOP_MINUTES * 60_000L)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var fallbackStop = false

    private fun startSound() {
        val audio = getSystemService(AudioManager::class.java)
        if (Store.alarmMaxVolume) {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        fun play(setSource: MediaPlayer.() -> Unit): MediaPlayer? {
            val candidate = MediaPlayer()
            return try {
                candidate.apply {
                    setAudioAttributes(attributes)
                    setSource()
                    isLooping = true
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                candidate.release()
                null
            }
        }

        val adhanId = if (Store.alarmSound == "system") 0 else AdhanSound.resId(this, AdhanSound.voice(Store.alarmSound).id)
        player = if (adhanId != 0) {
            play {
                resources.openRawResourceFd(adhanId).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
        } else {
            null
        } ?: run {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            uri?.let { u -> play { setDataSource(this@AlarmService, u) } }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val pattern = longArrayOf(0, 800, 600, 800, 1600)
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, 0)) }
    }

    private fun stopAlarm() {
        releaseAlarm()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Одинаковая очистка при ручном выключении и завершении службы системой. */
    private fun releaseAlarm() {
        handler.removeCallbacksAndMessages(null)
        fallbackStop = false
        player?.runCatching { stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        if (savedVolume >= 0) {
            runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0) }
            savedVolume = -1
        }
        AlarmState.ringing = false
    }

    private fun notification(test: Boolean): Notification {
        val res = Lang.wrap(this).resources
        val full = PendingIntent.getActivity(
            this, 10, AlarmActivity.intent(this, test),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, FajrAlarm.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.alarm_ring_title))
            .setContentText(res.getString(R.string.alarm_ring_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
        // «Открыть» — всегда: нажатие открывает экран будильника, даже если он не появился сам.
        builder.addAction(0, res.getString(R.string.alarm_open), full)
        if (!Store.alarmWakeCheck || fallbackStop) {
            val stop = PendingIntent.getService(
                this, 11, Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, res.getString(R.string.alarm_stop), stop)
        }
        return builder.build()
    }

    override fun onDestroy() {
        releaseAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.zikrtime.app.ALARM_STOP"
        const val ACTION_SNOOZE = "com.zikrtime.app.ALARM_SNOOZE"
        const val SNOOZE_MINUTES = 5
        const val AUTO_STOP_MINUTES = 20

        fun start(context: Context, test: Boolean, fajrMillis: Long) {
            val intent = Intent(context, AlarmService::class.java)
                .putExtra(FajrAlarm.EXTRA_TEST, test)
                .putExtra(FajrAlarm.EXTRA_FAJR, fajrMillis)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }

        fun snooze(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE))
        }
    }
}
