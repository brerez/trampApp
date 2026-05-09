package com.example.tramapp.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThrottleUtil(private val timeProvider: () -> Long = { System.currentTimeMillis() }) {
    private val _throttleUntil = MutableStateFlow(0L)
    val throttleUntil: StateFlow<Long> = _throttleUntil.asStateFlow()
    
    private val throttleMutex = Mutex()
    
    private val callTimestamps = mutableListOf<Long>()
 
    suspend fun waitForRateLimit() {
        var waitTime = 0L
        synchronized(callTimestamps) {
            val now = timeProvider()
            // Remove timestamps older than 8 seconds
            callTimestamps.removeAll { it < now - 8000 }
            
            if (callTimestamps.size >= 19) {
                // Wait until the oldest call is outside the 8 second window
                val oldestCall = callTimestamps.first()
                waitTime = oldestCall + 8000 - now
            }
            
            val scheduledTime = if (waitTime > 0) now + waitTime else now
            callTimestamps.add(scheduledTime)
        }
        
        if (waitTime > 0) {
            delay(waitTime)
        }
        
        // Also check for 429 throttle
        checkThrottle()
    }

    fun waitForRateLimitBlocking() {
        var waitTime = 0L
        synchronized(callTimestamps) {
            val now = timeProvider()
            // Remove timestamps older than 8 seconds
            callTimestamps.removeAll { it < now - 8000 }
            
            if (callTimestamps.size >= 19) {
                val oldestCall = callTimestamps.first()
                waitTime = oldestCall + 8000 - now
            }
            
            val scheduledTime = if (waitTime > 0) now + waitTime else now
            callTimestamps.add(scheduledTime)
        }
        
        if (waitTime > 0) {
            Thread.sleep(waitTime)
        }
        
        // Also check for 429 throttle
        val now = timeProvider()
        if (now < _throttleUntil.value) {
            val throttleWait = _throttleUntil.value - now
            Thread.sleep(throttleWait)
        }
    }

    suspend fun checkThrottle() {
        val now = timeProvider()
        if (now < _throttleUntil.value) {
            val waitTime = _throttleUntil.value - now
            delay(waitTime)
        }
    }

    suspend fun handleThrottle(e: Exception) {
        if (e is retrofit2.HttpException && e.code() == 429) {
            throttleMutex.withLock {
                _throttleUntil.value = timeProvider() + 10000
            }
        }
    }
}