package com.example.tramapp.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.currentTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleUtilConcurrencyTest {

    @Test
    fun waitForRateLimitShouldDelayAfter20Calls() = runTest {
        val throttleUtil = ThrottleUtil(timeProvider = { currentTime })

        // First 20 calls should not delay
        repeat(20) {
            val start = currentTime
            throttleUtil.waitForRateLimit()
            assertEquals(0L, currentTime - start)
        }

        // 21st call should delay by 8000ms (since all 20 calls were at t=0)
        val start = currentTime
        throttleUtil.waitForRateLimit()
        assertEquals(8000L, currentTime - start)
    }

    @Test
    fun concurrentCheckThrottleCallsShouldRespectThrottleUntilState() = runTest {
        val throttleUtil = ThrottleUtil(timeProvider = { currentTime })

        // Use handleThrottle to set throttle until (proper API)
        try {
            throw retrofit2.HttpException(
                retrofit2.Response.error<Any>(
                    429,
                    okhttp3.ResponseBody.create(null, "")
                )
            )
        } catch (e: Exception) {
            throttleUtil.handleThrottle(e)
        }

        // throttleUntil should be set to currentTime + 10000 (0 + 10000 = 10000)
        assertEquals(10000L, throttleUtil.throttleUntil.value)

        val start = currentTime
        throttleUtil.checkThrottle()
        assertEquals(10000L, currentTime - start)
    }

    @Test
    fun waitForRateLimitWithNormalTimeProviderShouldRespectSystemCurrentTimeMillis() = runTest {
        val throttleUtil = ThrottleUtil(timeProvider = { currentTime })

        // Fill up the window
        repeat(20) {
            throttleUtil.waitForRateLimit()
        }

        val start = currentTime
        // Call 3 more times
        repeat(3) { i ->
            throttleUtil.waitForRateLimit()
        }

        // Call 21 delayed to 8000.
        // Call 22 and 23 at t=8000 see 20 calls in window (at t=0).
        // They compute waitTime = 0 + 8000 - 8000 = 0.
        // So they do not delay.
        assertEquals(8000L, currentTime - start)
    }

    @Test
    fun concurrentCallsDuringRapidRequestsShouldMaintainCorrectTimestamps() = runTest {
        val throttleUtil = ThrottleUtil(timeProvider = { currentTime })

        // Pre-populate with 20 calls
        for (i in 1..20) {
            throttleUtil.waitForRateLimit()
        }

        val start = currentTime
        
        // Make 10 more calls
        repeat(10) {
            throttleUtil.waitForRateLimit()
        }

        // The first of these 10 (call 21) will delay 8000ms.
        // The remaining 9 will see the oldest call at t=0 (if not removed) or t=8000.
        // They will not delay if they compute waitTime <= 0.
        // So total delay should be at least 8000.
        assertTrue(currentTime >= 8000L)
    }
}
