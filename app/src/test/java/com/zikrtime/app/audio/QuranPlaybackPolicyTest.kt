package com.zikrtime.app.audio

import org.junit.Assert.*
import org.junit.Test

class QuranPlaybackPolicyTest {
    @Test fun invalidSavedSurahDoesNotBecomeAPlaybackRequest() {
        assertNull(QuranPlaybackPolicy.restore(0, 10_000L, 30_000L))
        assertNull(QuranPlaybackPolicy.restore(115, 10_000L, 30_000L))
    }

    @Test fun restoredStopPointIsWithinTheKnownRecording() {
        assertEquals(QuranPlaybackPosition(108, 30_000L, 30_000L), QuranPlaybackPolicy.restore(108, 40_000L, 30_000L))
        assertEquals(0L, QuranPlaybackPolicy.restore(108, -1L, -1L)!!.positionMs)
        assertEquals(12_000L, QuranPlaybackPolicy.restore(108, 12_000L, 0L)!!.positionMs)
    }

    @Test fun skipControlsCannotSeekBeforeStartOrAfterEnd() {
        assertEquals(0L, QuranPlaybackPolicy.offset(3_000L, -15_000L, 25_000L))
        assertEquals(25_000L, QuranPlaybackPolicy.offset(20_000L, 15_000L, 25_000L))
        assertEquals(20_000L, QuranPlaybackPolicy.offset(5_000L, 15_000L, 25_000L))
    }

    @Test fun corruptOrExtremeSeekInputsCannotOverflow() {
        assertEquals(25_000L, QuranPlaybackPolicy.offset(Long.MAX_VALUE, 15_000L, 25_000L))
        assertEquals(0L, QuranPlaybackPolicy.offset(50L, Long.MIN_VALUE, 0L))
        assertEquals(0L, QuranPlaybackPolicy.clamp(Long.MIN_VALUE, 25_000L))
    }
}
