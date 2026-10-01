package com.example.tramapp.glance

import kotlin.math.abs

/** Compass redraw gate (R15): redraw when the heading moved >= minChangeDeg from the last DRAWN heading
 *  AND at least minIntervalMs passed since the last redraw. The first reading always redraws.
 *  Angular difference wraps (350 -> 10 is 20 deg). */
class HeadingGate(val minChangeDeg: Double = 30.0, val minIntervalMs: Long = 3_000L) {
    var lastDrawnHeading: Double? = null
        private set
    private var lastDrawnAtMs: Long? = null

    fun shouldRedraw(headingDeg: Double, nowMs: Long): Boolean {
        val lastHeading = lastDrawnHeading
        val lastTime = lastDrawnAtMs
        
        if (lastHeading == null || lastTime == null) {
            lastDrawnHeading = headingDeg
            lastDrawnAtMs = nowMs
            return true
        }
        
        if (nowMs - lastTime < minIntervalMs) {
            return false
        }
        
        val diff = abs(headingDeg - lastHeading) % 360.0
        val change = if (diff > 180.0) 360.0 - diff else diff
        
        if (change >= minChangeDeg) {
            lastDrawnHeading = headingDeg
            lastDrawnAtMs = nowMs
            return true
        }
        
        return false
    }

    fun reset() {
        lastDrawnHeading = null
        lastDrawnAtMs = null
    }
}

/** Caps any notification update to one per minIntervalMs. `tryAcquire` returns true and records when allowed;
 *  otherwise returns false and `pendingAtMs` tells the caller when to retry (coalescing the skipped update). */
class UpdateLimiter(val minIntervalMs: Long = 3_000L) {
    private var lastAcquiredAtMs: Long? = null

    fun tryAcquire(nowMs: Long): Boolean {
        val last = lastAcquiredAtMs
        if (last == null || nowMs - last >= minIntervalMs) {
            lastAcquiredAtMs = nowMs
            return true
        }
        return false
    }

    fun nextAllowedAtMs(): Long {
        val last = lastAcquiredAtMs ?: return 0L
        return last + minIntervalMs
    }
}
