package com.example.tramapp.glance

import org.junit.Assert.*
import org.junit.Test

class SessionSchedulerTest {
    @Test
    fun screenChangedAE3() {
        val startedAt = 1000L
        val scheduler = SessionScheduler(SessionSettings(), startedAt, screenOn = false)
        
        // AE3: screen off for 2 min with last refresh 2 min ago -> onScreenChanged(true) returns true
        scheduler.onRefreshed(startedAt)
        val now = startedAt + 120_000L // 2 minutes later
        assertTrue(scheduler.onScreenChanged(true, now))
        
        // With last refresh 10 s ago -> false
        scheduler.onRefreshed(now)
        assertFalse(scheduler.onScreenChanged(true, now + 10_000L))
        
        // nextRefreshAtMs == last + 20 s
        assertEquals(now + 20_000L, scheduler.nextRefreshAtMs())
    }

    @Test
    fun intervals() {
        val startedAt = 1000L
        var settings = SessionSettings(screenOnIntervalMs = 20_000L, screenOffIntervalMs = 180_000L)
        val scheduler = SessionScheduler(settings, startedAt, screenOn = true)
        
        scheduler.onRefreshed(startedAt)
        
        // Screen on: next refresh 20 s after last
        assertEquals(startedAt + 20_000L, scheduler.nextRefreshAtMs())
        
        // Screen off: 3 min after last
        scheduler.onScreenChanged(false, startedAt)
        assertEquals(startedAt + 180_000L, scheduler.nextRefreshAtMs())
        
        // Custom intervals (10 s / 60 s, 1 min / 10 min) honoured, including settings replaced mid-session
        scheduler.settings = SessionSettings(screenOnIntervalMs = 10_000L, screenOffIntervalMs = 60_000L)
        assertEquals(startedAt + 60_000L, scheduler.nextRefreshAtMs()) // Still screen off
        scheduler.onScreenChanged(true, startedAt)
        assertEquals(startedAt + 10_000L, scheduler.nextRefreshAtMs()) // Screen on now
        
        scheduler.settings = SessionSettings(screenOnIntervalMs = 60_000L, screenOffIntervalMs = 600_000L)
        assertEquals(startedAt + 60_000L, scheduler.nextRefreshAtMs())
    }

    @Test
    fun ae4Timeout() {
        val startedAt = 1000L
        val schedulerNever = SessionScheduler(SessionSettings(timeoutMs = null), startedAt, screenOn = true)
        
        // timeout null ("Never") -> shouldStop false at +2 h (and +24 h)
        assertFalse(schedulerNever.shouldStop(startedAt + 2 * 3_600_000L))
        assertFalse(schedulerNever.shouldStop(startedAt + 24 * 3_600_000L))
        assertNull(schedulerNever.stopAtMs())
        
        // nextWakeAtMs is the refresh time
        assertEquals(schedulerNever.nextRefreshAtMs(), schedulerNever.nextWakeAtMs())
        
        val timeout30Min = 30 * 60_000L
        val scheduler30 = SessionScheduler(SessionSettings(timeoutMs = timeout30Min), startedAt, screenOn = true)
        
        // 30-minute timeout -> shouldStop false at 29:59, true at 30:00
        assertFalse(scheduler30.shouldStop(startedAt + timeout30Min - 1))
        assertTrue(scheduler30.shouldStop(startedAt + timeout30Min))
        
        // nextWakeAtMs = min(refresh, stop)
        assertEquals(minOf(scheduler30.nextRefreshAtMs(), scheduler30.stopAtMs()!!), scheduler30.nextWakeAtMs())
    }

    @Test
    fun neverRefreshed() {
        val startedAt = 1000L
        val scheduler = SessionScheduler(SessionSettings(), startedAt, screenOn = true)
        
        // Never refreshed: nextRefreshAtMs == startedAtMs (refresh immediately on start)
        assertEquals(startedAt, scheduler.nextRefreshAtMs())
        assertTrue(scheduler.isRefreshDue(startedAt))
    }
}
