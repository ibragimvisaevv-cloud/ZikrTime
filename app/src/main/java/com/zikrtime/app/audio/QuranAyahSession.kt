package com.zikrtime.app.audio

import android.os.Bundle
import androidx.media3.common.MediaItem

/** Main-thread service state. UI reads it but can change it only through a validated session command. */
internal object QuranAyahSession {
    const val COMMAND_CONFIGURE = "com.zikrtime.app.audio.CONFIGURE_AYAH"
    private const val REPEATS = "ayah_repeats"
    private const val SPEED = "ayah_speed"
    private const val CONTINUE = "ayah_continue"
    private const val ITERATION = "ayah_iteration"
    var reference: QuranAudioAyah? = null
        private set
    var options = QuranAyahOptions()
        private set
    var iteration = 1
        private set

    fun begin(mediaItem: MediaItem?) {
        reference = mediaItem?.mediaId?.let(QuranAyahPlaybackPolicy::reference)
        options = fromBundle(mediaItem?.mediaMetadata?.extras) ?: QuranAyahOptions()
        iteration = mediaItem?.mediaMetadata?.extras?.getInt(ITERATION, 1)?.coerceIn(1,
            options.repeatCount.takeIf { it > 0 } ?: Int.MAX_VALUE) ?: 1
    }

    fun configure(value: QuranAyahOptions) {
        if (value.repeatCount != options.repeatCount) iteration = 1
        options = value
    }

    fun repeat() { iteration = if (iteration < Int.MAX_VALUE) iteration + 1 else 1 }

    fun resetIteration() { iteration = 1 }

    fun bundle(options: QuranAyahOptions, iteration: Int = 1): Bundle = Bundle().apply {
        putInt(REPEATS, options.repeatCount)
        putFloat(SPEED, options.speed)
        putBoolean(CONTINUE, options.continueAfter)
        putInt(ITERATION, iteration)
    }

    fun fromBundle(bundle: Bundle?): QuranAyahOptions? = bundle?.let {
        QuranAyahPlaybackPolicy.options(it.getInt(REPEATS, 1), it.getFloat(SPEED, 1f), it.getBoolean(CONTINUE, false))
    }
}
