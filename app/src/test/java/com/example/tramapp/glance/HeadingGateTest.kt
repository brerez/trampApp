package com.example.tramapp.glance

import org.junit.Assert.*
import org.junit.Test

class HeadingGateTest {
    @Test
    fun headingGateBasic() {
        val gate = HeadingGate(minChangeDeg = 30.0, minIntervalMs = 3000L)
        
        // First reading redraws
        assertTrue(gate.shouldRedraw(10.0, 1000L))
        assertEquals(10.0, gate.lastDrawnHeading)
        
        // A 20 deg change does not
        assertFalse(gate.shouldRedraw(30.0, 5000L))
        
        // A 35 deg change (after >= 3 s) does
        assertTrue(gate.shouldRedraw(45.0, 5000L))
        assertEquals(45.0, gate.lastDrawnHeading)
    }

    @Test
    fun headingGateInterval() {
        val gate = HeadingGate(minChangeDeg = 30.0, minIntervalMs = 3000L)
        gate.shouldRedraw(10.0, 1000L)
        
        // Two 35 deg changes 1 s apart redraw once (second blocked by the interval)
        assertTrue(gate.shouldRedraw(45.0, 5000L)) // 4s later, allowed
        assertFalse(gate.shouldRedraw(80.0, 6000L)) // 1s later, blocked by interval
    }

    @Test
    fun headingGateWrapAround() {
        val gate = HeadingGate(minChangeDeg = 30.0, minIntervalMs = 3000L)
        gate.shouldRedraw(350.0, 1000L)
        
        // Wrap-around: 350 -> 15 (25 deg) no redraw
        assertFalse(gate.shouldRedraw(15.0, 5000L))
        
        // Wrap-around: 350 -> 25 (35 deg) redraws
        assertTrue(gate.shouldRedraw(25.0, 5000L))
        assertEquals(25.0, gate.lastDrawnHeading)
        
        // reset() makes the next reading redraw
        gate.reset()
        assertTrue(gate.shouldRedraw(30.0, 6000L))
    }

    @Test
    fun updateLimiter() {
        val limiter = UpdateLimiter(minIntervalMs = 3000L)
        
        val t1 = 1000L
        assertTrue(limiter.tryAcquire(t1))
        
        // two acquires 1 s apart -> second false with nextAllowedAtMs = first + 3 s
        assertFalse(limiter.tryAcquire(t1 + 1000L))
        assertEquals(t1 + 3000L, limiter.nextAllowedAtMs())
        
        // at +3 s true
        assertTrue(limiter.tryAcquire(t1 + 3000L))
    }
}
