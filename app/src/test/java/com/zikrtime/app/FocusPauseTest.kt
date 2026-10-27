package com.zikrtime.app

import com.zikrtime.app.screentime.*
import com.zikrtime.app.prayer.Prayer
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FocusPauseTest {
    @Test fun sessionNeverExpandsDailyAllowanceOrIgnoresPrayerLock() {
        assertEquals(45_000L,PausePolicy.budget(15,45_000))
        assertEquals(0L,PausePolicy.budget(5,-1))
        val prayer=BlockReason.PrayerTime(Prayer.FAJR,LocalDate.of(2026,10,27),Instant.EPOCH.plusSeconds(600))
        assertFalse(PausePolicy.needsPause(true,true,null,0,100,100,0,prayer))
        assertFalse(PausePolicy.needsPause(true,true,null,0,100,100,0,BlockReason.LimitReached(30,30)))
    }
    @Test fun validSessionSurvivesProcessRestoreAndSwitching() {
        val restored=PauseSession(startedAt=1_000,budgetMillis=300_000,consumedMillis=100_000)
        // A partial system history after a restart must not replenish already consumed time.
        assertEquals(200_000L,PausePolicy.remaining(restored,10_000,500_000))
        assertFalse(PausePolicy.needsPause(true,true,restored,120_000,500_000,450_000,2_000,BlockReason.None))
        assertTrue(PausePolicy.needsPause(true,true,restored,300_000,600_000,450_000,2_000,BlockReason.None))
        assertEquals(0L,PausePolicy.remaining(restored,0,900))
        assertFalse(PausePolicy.needsPause(false,true,null,0,100,100,0,BlockReason.None))
    }
    @Test fun softExpiryRemindsWithoutRepeatedWindowUntilNextVisit() {
        val s=PauseSession(1_000,60_000,60_000,reminderSent=true)
        assertFalse(PausePolicy.needsPause(true,false,s,60_000,100_000,2_000,2_000,BlockReason.None))
        assertTrue(PausePolicy.needsPause(true,false,s,60_000,110_000,105_000,2_000,BlockReason.None))
        assertTrue(PausePolicy.needsPause(true,true,s,60_000,100_000,2_000,2_000,BlockReason.None))
    }
    @Test fun deniedReminderIsHandledWithoutRepeatedPauseInSameVisit() {
        val restored=PauseSession(1_000,60_000,60_000,reminderSent=false,expiryHandled=true)
        assertFalse(restored.reminderSent)
        for(now in listOf(100_000L,103_000L,106_000L)) {
            assertFalse(PausePolicy.needsPause(true,false,restored,60_000,now,2_000,2_000,BlockReason.None))
        }
        assertTrue(PausePolicy.needsPause(true,false,restored,60_000,120_000,110_000,2_000,BlockReason.None))
        assertTrue(PausePolicy.needsPause(true,true,restored,60_000,120_000,2_000,2_000,BlockReason.None))
    }
    @Test fun foregroundUnionExcludesLockedScreenBackgroundAndShutdown() {
        val events=listOf(
            UsageEventLite("social","A",true,0),
            UsageEventLite("social","B",true,500), // overlapping activities count once
            UsageEventLite("social","A",false,1_000),
            UsageEventLite("","",false,2_000,screenOn=false),
            UsageEventLite("","",false,2_100,unlocked=false),
            UsageEventLite("","",false,5_000,screenOn=true), // lit lock screen does not count
            UsageEventLite("","",false,6_000,unlocked=true),
            UsageEventLite("social","B",false,7_000),
            UsageEventLite("social","A",true,9_000),
            UsageEventLite("","",false,10_000,reset=true),
        )
        assertEquals(4_000L,UsageMath.foregroundMillis(events,0,20_000))
        assertTrue(UsageMath.openPackages(events).isEmpty())
        assertEquals(2_500L,UsageMath.foregroundMillis(events,1_500,20_000))
        assertEquals(0L,UsageMath.foregroundMillis(events,20_000,10_000))
        assertTrue(UsageMath.openPackages(events.take(6)).isEmpty())
    }
}
