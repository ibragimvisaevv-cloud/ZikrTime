@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zikrtime.app.audio

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.i18n.Lang

/** Foreground service exists only while a user-requested download can progress. */
class QuranDownloadService : DownloadService(
    NOTIFICATION_ID, 1000L, CHANNEL_ID, R.string.audio_download_channel, R.string.audio_download_channel_description,
) {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun getDownloadManager(): DownloadManager = QuranAudioRepository.downloadManager(this)

    override fun getScheduler(): Scheduler = PlatformScheduler(this, SCHEDULER_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val active = downloads.filter { it.state == Download.STATE_DOWNLOADING }
        val total = active.sumOf { it.contentLength.coerceAtLeast(0) }
        val bytes = active.sumOf { it.bytesDownloaded.coerceAtLeast(0) }
        val text = when {
            notMetRequirements and Requirements.NETWORK != 0 -> getString(R.string.audio_download_waiting_network)
            notMetRequirements and Requirements.NETWORK_UNMETERED != 0 -> getString(R.string.audio_download_waiting_wifi)
            active.isNotEmpty() -> getString(R.string.audio_download_running)
            else -> getString(R.string.audio_download_preparing)
        }
        val open = Intent(this, MainActivity::class.java)
            .setAction("com.zikrtime.app.OPEN_QURAN_AUDIO")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_knowledge)
            .setContentTitle(getString(R.string.audio_download_channel))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true).setSilent(true).setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0,
                total <= 0 || active.isEmpty())
            .build()
    }

    // Android 15 limits the total background time of dataSync services. Retain partial data and stop.
    override fun onTimeout(startId: Int, fgsType: Int) {
        getDownloadManager().pauseDownloads()
        stopSelf()
    }

    companion object {
        private const val NOTIFICATION_ID = 8271
        private const val SCHEDULER_ID = 8272
        private const val CHANNEL_ID = "quran_audio_downloads_v1"
    }
}
