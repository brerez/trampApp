package com.example.tramapp.glance

import kotlin.math.min

data class SessionSettings(
    val screenOnIntervalMs: Long = 20_000L,
    val screenOffIntervalMs: Long = 180_000L,
    val timeoutMs: Long? = 3_600_000L          // null = "Never"
)

/** Not thread-safe; the service calls it from one coroutine. */
class SessionScheduler(
    var settings: SessionSettings,
    private val startedAtMs: Long,
    screenOn: Boolean
) {
    var screenOn: Boolean = screenOn
        private set
    var lastRefreshAtMs: Long? = null
        private set

    /** Record a completed (or attempted) refresh. */
    fun onRefreshed(nowMs: Long) {
        lastRefreshAtMs = nowMs
    }

    /** Screen state change. Returns true if a refresh should start NOW: on screen-on, when there was no refresh yet
     *  or the last refresh is >= screenOnIntervalMs old (R17/AE3). Screen-off never returns true. */
    fun onScreenChanged(screenOn: Boolean, nowMs: Long): Boolean {
        this.screenOn = screenOn
        if (screenOn) {
            val last = lastRefreshAtMs
            if (last == null) return true
            if (nowMs - last >= settings.screenOnIntervalMs) return true
        }
        return false
    }

    /** When the next periodic refresh is due: lastRefresh + interval for the current screen state
     *  (startedAtMs if never refreshed). */
    fun nextRefreshAtMs(): Long {
        val last = lastRefreshAtMs ?: return startedAtMs
        val interval = if (screenOn) settings.screenOnIntervalMs else settings.screenOffIntervalMs
        return last + interval
    }

    fun isRefreshDue(nowMs: Long): Boolean {
        return nowMs >= nextRefreshAtMs()
    }

    /** startedAtMs + timeoutMs, or null for Never. */
    fun stopAtMs(): Long? {
        val timeout = settings.timeoutMs ?: return null
        return startedAtMs + timeout
    }

    fun shouldStop(nowMs: Long): Boolean {
        val stopAt = stopAtMs() ?: return false
        return nowMs >= stopAt
    }

    /** Earliest of nextRefreshAtMs() and stopAtMs(); what the service should sleep until. */
    fun nextWakeAtMs(): Long {
        val refreshAt = nextRefreshAtMs()
        val stopAt = stopAtMs()
        if (stopAt != null) {
            return min(refreshAt, stopAt)
        }
        return refreshAt
    }
}
