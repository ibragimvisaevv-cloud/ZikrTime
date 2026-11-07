package com.zikrtime.app.audio

import org.junit.Assert.*
import org.junit.Test

class QuranAudioPolicyTest {
    @Test fun partialOrMissingBytesAreNeverAdvertisedAsOffline() {
        assertFalse(QuranAudioPolicy.isOfflineReady(true, 1000, 999))
        assertFalse(QuranAudioPolicy.isOfflineReady(true, 1000, 0))
        assertFalse(QuranAudioPolicy.isOfflineReady(false, 1000, 1000))
        assertFalse(QuranAudioPolicy.isOfflineReady(true, -1, 1000))
        assertTrue(QuranAudioPolicy.isOfflineReady(true, 1000, 1000))
    }

    @Test fun downloadsMayExceedTheStreamingCacheBudget() {
        val longSurah = 120_641_555L
        assertTrue(longSurah > QuranAudioPolicy.TEMPORARY_CACHE_BYTES)
        assertTrue(QuranAudioPolicy.isOfflineReady(true, longSurah, longSurah))
    }

    @Test fun aShortServerResponseCannotBecomeAnOfflineSurah() {
        assertFalse(QuranAudioPolicy.isOfflineReady(true, 100, 100, expectedLength = 837734))
        assertFalse(QuranAudioPolicy.isOfflineReady(true, 837735, 837735, expectedLength = 837734))
        assertTrue(QuranAudioPolicy.isOfflineReady(true, 837734, 837734, expectedLength = 837734))
    }

    @Test fun spaceCheckReservesRoomForExistingQueueAndOtherAppDataWithoutOverflow() {
        val reserve = QuranAudioPolicy.FREE_SPACE_RESERVE
        assertTrue(QuranAudioPolicy.enoughSpace(reserve + 3000, 1000, 2000))
        assertFalse(QuranAudioPolicy.enoughSpace(reserve + 2999, 1000, 2000))
        assertFalse(QuranAudioPolicy.enoughSpace(reserve - 1, 0, 0))
        assertFalse(QuranAudioPolicy.enoughSpace(Long.MAX_VALUE, Long.MAX_VALUE, 1000))
        assertFalse(QuranAudioPolicy.enoughSpace(reserve + 1000, -1, 0))
    }

    @Test fun pendingStatesReserveSpaceButRemovedFailedAndCompleteDoNot() {
        assertTrue(QuranAudioPolicy.isPending(QuranAudioDownloadStatus.WAITING_FOR_WIFI))
        assertTrue(QuranAudioPolicy.isPending(QuranAudioDownloadStatus.DOWNLOADING))
        assertFalse(QuranAudioPolicy.isPending(QuranAudioDownloadStatus.FAILED))
        assertFalse(QuranAudioPolicy.isPending(QuranAudioDownloadStatus.COMPLETED))
        assertFalse(QuranAudioPolicy.isPending(QuranAudioDownloadStatus.REMOVING))
    }
}
