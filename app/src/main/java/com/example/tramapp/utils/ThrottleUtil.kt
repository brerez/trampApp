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
    private val windowMutex = Mutex()

    suspend fun waitForRateLimit() {
        var waitTime = 0L
        windowMutex.withLock {
            val now = timeProvider()
            // Remove timestamps older than 8 seconds
            callTimestamps.removeAll { it < now - 8000 }
            
            if (callTimestamps.size >= 20) {
                // Wait until the oldest call is outside the 8 second window
                val oldestCall = callTimestamps.first()
                waitTime = oldestCall + 8000 - now
            }
            
            val scheduledTime = if (waitTime > 0) now + waitTime else now
            callTimestamps.add(scheduledTime)
        }
        
        if (waitTime > 0) {
            println("🛑 Rate limit approaching! Waiting ${waitTime}ms...")
            delay(waitTime)
        }
        
        // Also check for 429 throttle
        checkThrottle()
    }

    suspend fun checkThrottle() {
        val now = timeProvider()
        if (now < _throttleUntil.value) {
            val waitTime = _throttleUntil.value - now
            println("🛑 API Throttled! Waiting ${waitTime}ms...")
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