package com.example.tramapp.utils

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Performance optimization utilities for the app.
 */
object PerformanceHelpers {

    /**
     * Memoized state holder for expensive computations.
     */
    class MemoizedState<T>(private val compute: () -> T) {
        private var _value: T? = null
        private var _isComputing = false

        fun get(): T {
            if (_value == null && !_isComputing) {
                _isComputing = true
                try {
                    _value = compute()
                } finally {
                    _isComputing = false
                }
            }
            return _value!!
        }

        fun reset() {
            _value = null
        }
    }

    /**
     * Debounce utility for user input.
     */
    class Debounce<T>(private val delayMs: Long) {
        private var pendingValue: T? = null
        private var pendingJob: kotlinx.coroutines.Job? = null

        fun set(value: T): T? {
            pendingValue = value
            pendingJob?.cancel()
            return value
        }

        fun get(): T? = pendingValue

        fun cancel() {
            pendingJob?.cancel()
            pendingValue = null
        }

        suspend fun commit(block: suspend () -> Unit) {
            if (pendingValue != null) {
                block()
                pendingValue = null
            }
        }
    }

    /**
     * Throttle utility for frequent operations.
     */
    class Throttle<T>(private val intervalMs: Long) {
        private var lastExecutionTime = 0L
        private var isExecuting = false

        fun execute(block: () -> Unit): Boolean {
            val now = System.currentTimeMillis()
            if (now - lastExecutionTime >= intervalMs && !isExecuting) {
                lastExecutionTime = now
                isExecuting = true
                block()
                return true
            }
            return false
        }

        fun reset() {
            lastExecutionTime = 0L
            isExecuting = false
        }
    }

    /**
     * Rate limiter for API calls.
     */
    class RateLimiter(
        private val maxRequests: Int,
        private val intervalMs: Long
    ) {
        private var requestCount = 0
        private var lastRequestTime = 0L

        fun canExecute(): Boolean {
            val now = System.currentTimeMillis()
            if (now - lastRequestTime >= intervalMs) {
                requestCount = 0
                lastRequestTime = now
                return true
            }
            return requestCount < maxRequests
        }

        fun execute(block: () -> Unit): Boolean {
            if (canExecute()) {
                requestCount++
                block()
                return true
            }
            return false
        }

        fun reset() {
            requestCount = 0
            lastRequestTime = System.currentTimeMillis()
        }
    }

    /**
     * Batch rate limiter for collecting and executing multiple requests.
     */
    class BatchRateLimiter(
        private val batchSize: Int = 5,
        private val batchDelayMs: Long = 300
    ) {
        private var pendingRequests = mutableListOf<Deferred<Any>>()

        suspend fun <T> collectAndExecute(
            requestFactory: () -> T,
            transform: ((List<T>) -> List<T>)? = null
        ): List<T> {
            // Wait for previous batch to complete
            if (pendingRequests.isNotEmpty()) {
                pendingRequests.forEach { it.await() }
                pendingRequests.clear()
            }

            val batch = mutableListOf<Deferred<T>>()
            repeat(batchSize) {
                batch.add(CoroutineScope(Dispatchers.IO).async {
                    requestFactory().also { result ->
                        transform?.invoke(listOf(result))?.let { transformed ->
                            // Transform applied, continue batching
                        } ?: pendingRequests.removeLast()
                    }
                })
            }

            val results = batch.map { it.await() }
            return transform?.invoke(results) ?: results
        }
    }

    /**
     * Batch update helper for reducing recompositions.
     */
    class BatchUpdate<T>(private val batchFn: (List<T>) -> Unit) {
        private var pendingUpdates: MutableList<T> = mutableListOf()

        fun add(update: T) {
            pendingUpdates.add(update)
        }

        fun flush(): Boolean {
            if (pendingUpdates.isEmpty()) return false

            batchFn(pendingUpdates.toList())
            pendingUpdates.clear()
            return true
        }

        fun clear() {
            pendingUpdates.clear()
        }
    }

    /**
     * Composable wrapper for BatchUpdate.
     */
    @Composable
    fun <T> rememberBatchUpdate(
        key: Any?,
        batchFn: (List<T>) -> Unit,
        flushOnDispose: Boolean = false
    ): BatchUpdate<T> {
        val batchUpdate = remember(key) { BatchUpdate(batchFn) }

        LaunchedEffect(key1 = key, key2 = flushOnDispose) {
            if (flushOnDispose) {
                batchUpdate.flush()
            }
        }

        return batchUpdate
    }

    /**
     * Memory-efficient state holder for large datasets.
     */
    class ChunkedState<T>(private val chunkSize: Int = 100) {
        private var chunks: MutableList<MutableList<T>> = mutableListOf()
        private var currentChunkIndex = 0

        fun add(chunk: List<T>) {
            if (chunks.isEmpty()) {
                chunks = mutableListOf(chunk.toMutableList())
            } else {
                val lastChunk = chunks.last()
                if (lastChunk.size < chunkSize) {
                    lastChunk.addAll(chunk)
                } else {
                    chunks.add(chunk.toMutableList())
                }
            }
        }

        fun get(): List<T> {
            return chunks.flatMap { it }
        }

        fun clear() {
            chunks = mutableListOf()
            currentChunkIndex = 0
        }

        fun size(): Int {
            return chunks.sumOf { it.size }
        }
    }

    /**
     * Lazy initialization helper.
     */
    class LazyInit<T>(private val initializer: () -> T) {
        private var initialized = false
        private var value: T? = null

        fun get(): T {
            if (!initialized) {
                value = initializer()
                initialized = true
            }
            return value!!
        }

        fun reset() {
            initialized = false
            value = null
        }
    }



    /**
     * State flow wrapper with optional caching.
     */
    class CachedStateFlow<T>(private val source: StateFlow<T>, private val cacheTimeMs: Long = 100) {
        private var lastValue: T? = null
        private var lastUpdateTime = 0L

        fun get(): T {
            val now = System.currentTimeMillis()
            if (lastValue != null && now - lastUpdateTime < cacheTimeMs) {
                return lastValue!!
            }

            // Force update from source
            lastValue = source.value
            lastUpdateTime = now
            return lastValue!!
        }

        fun reset() {
            lastValue = null
            lastUpdateTime = 0L
        }
    }

    /**
     * Memory usage monitor.
     */
    class MemoryMonitor {
        private var peakMemory = 0L
        private var currentMemory = 0L

        fun record(memory: Long) {
            currentMemory = memory
            if (memory > peakMemory) {
                peakMemory = memory
            }
        }

        fun getPeak(): Long = peakMemory
        fun getCurrent(): Long = currentMemory
        fun reset() {
            peakMemory = 0L
            currentMemory = 0L
        }
    }
}

/**
 * Extension function to create a memoized state.
 */
fun rememberMemoized(
    key1: Any?,
    key2: Any? = null,
    compute: () -> Any
): Any {
    val holder = PerformanceHelpers.MemoizedState(compute)
    return holder.get()
}

/**
 * Extension function to create a debounced value.
 */
fun rememberDebounce(
    key1: Any?,
    delayMs: Long,
    initial: Any? = null
): Pair<Any?, PerformanceHelpers.Debounce<Any?>> {
    val debounce = PerformanceHelpers.Debounce<Any?>(delayMs)
    return initial to debounce
}

/**
 * Extension function to create a throttled operation.
 */
fun rememberThrottle(
    key1: Any?,
    intervalMs: Long
): Pair<Any?, PerformanceHelpers.Throttle<Any?>> {
    val throttle = PerformanceHelpers.Throttle<Any?>(intervalMs)
    return null to throttle
}
