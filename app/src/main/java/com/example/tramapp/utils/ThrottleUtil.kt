package com.example.tramapp.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThrottleUtil {
    private val _throttleUntil = MutableStateFlow(0L)
    val throttleUntil: StateFlow<Long> = _throttleUntil.asStateFlow()
    
    private val throttleMutex = Mutex()

    suspend fun checkThrottle() {
        val now = System.currentTimeMillis()
        if (now < _throttleUntil.value) {
            val waitTime = _throttleUntil.value - now
            println("🛑 API Throttled! Waiting ${waitTime}ms...")
            delay(waitTime)
        }
    }

    suspend fun handleThrottle(e: Exception) {
        if (e is retrofit2.HttpException && e.code() == 429) {
            throttleMutex.withLock {
                _throttleUntil.value = System.currentTimeMillis() + 10000
            }
        }
    }
}