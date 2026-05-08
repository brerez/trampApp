package com.example.tramapp.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleUtilTest {

    @Test
    fun `waitForRateLimit should delay after 20 calls`() = runTest {
        var mockTime = 1000L
        val throttleUtil = ThrottleUtil(timeProvider = { mockTime })
        
        // Make 20 calls immediately
        repeat(20) {
            throttleUtil.waitForRateLimit()
        }
        
        var finished = false
        val job = launch {
            throttleUtil.waitForRateLimit()
            finished = true
        }
        
        // Advance virtual time by 7 seconds. The delay was scheduled for 8 seconds.
        advanceTimeBy(7000)
        assertEquals(false, finished) // Still waiting at 7 seconds
        
        // Advance past the 8 second mark
        advanceTimeBy(1500)
        assertEquals(true, finished) // Should be done now
        
        job.cancel()
    }
}
