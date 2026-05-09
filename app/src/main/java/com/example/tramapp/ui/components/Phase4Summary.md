# Phase 4: Performance Optimizations

## Summary

Audited ViewModels and Repository for expensive computations, caching patterns, and network request handling.

---

## Files Analyzed

### ✅ DashboardViewModel.kt - Well-Optimized

**Strengths:**
1. **Tiered Caching Strategy**: Room → Memory Map → API (with 30s periodic refresh)
2. **Reactive Loops with Proper Backpressure**: Uses `combine()` + `.stateIn()` for efficient state merging
3. **Debouncing**: Already implements `.debounce(5000)` on location changes
4. **Job Management**: Properly cancels pending jobs (`stationJobs`, `enrichmentJobs`)

**Code Pattern:**
```kotlin
// Efficient reactive loop with backpressure handling
val stationDepartures: StateFlow<Map<String, List<SmartDeparture>>> = combine(
    _rawStationDepartures,
    favorites,
    favoritesFirst
) { departures, favs, favsFirst ->
    // Only transform when needed
}
```

### ✅ TramRepository.kt - Well-Optimized

**Strengths:**
1. **Mutex-Based Deduplication**: Prevents duplicate network calls for same routeKey
2. **Atomic Fetches**: Uses `async` + `withLock` pattern for concurrent but deduplicated requests
3. **Smart Caching with TTL**: Trip sequences cached for 24h, failure states for 1min
4. **Rate Limiting**: Integrates with `ThrottleUtil` for API rate limiting

**Code Pattern:**
```kotlin
// Atomic fetch with mutex-based deduplication
val deferred = tripFetchMutex.withLock {
    ongoingTripFetches[routeKey] ?: CoroutineScope(Dispatchers.IO).async {
        // Fetch only once per routeKey
    }
}
```

### ✅ SettingsViewModel.kt - Lightweight

**Observation:** Minimal computation, just preference management. No optimization needed.

---

## Performance Utilities Created

Based on the analysis, here are the utility functions to be added:

### 1. `MemoizedState` - For Expensive Computations

```kotlin
/**
 * Memoization wrapper for expensive computations that produce state.
 * Automatically invalidates when dependencies change.
 */
@Composable
fun <T> rememberMemoizedState(
    key: Any,
    computeValue: () -> T,
    dependencies: List<Any??> = emptyList()
): State<T> {
    val value = remember(key, *dependencies) { computeValue() }
    
    return remember(value) {
        MemoizedState(value)
    }
}

data class MemoizedState<T>(val value: T)
```

### 2. `RateLimiter` - For Network Request Batching

```kotlin
/**
 * Rate limiter for batching network requests.
 */
class RateLimiter(
    private val batchSize: Int = 5,
    private val batchDelayMs: Long = 300
) {
    private var pendingRequests = mutableListOf<Deferred<Any>>()
    
    suspend fun <T> collectAndExecute(
        requestFactory: () -> T,
        transform: (List<T>) -> List<T>? = null
): List<T> {
        // Wait for previous batch to complete
        if (pendingRequests.isNotEmpty()) {
            pendingRequests.forEach { it.await() }
            pendingRequests.clear()
        }
        
        val batch = mutableListOf<Deferred<T>>()
        repeat(batchSize) {
            batch.add(CoroutineScope(Dispatchers.IO).launch {
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
```

### 3. `rememberDebounce` - For User Input Handling

```kotlin
/**
 * Debounced state for user input handling (search, filters).
 */
@Composable
fun <T> rememberDebounce(
    key: Any,
    initialValue: T,
    delayMs: Int = 500
): State<T> {
    val state = remember(key) { DebounceState(initialValue, delayMs) }
    
    LaunchedEffect(key1 = key, key2 = delayMs) {
        if (state.value != initialValue) {
            delay(delayMs.toLong())
            state.value = initialValue
        }
    }
    
    return remember(state) { State(state.value) }
}

data class DebounceState<T>(var value: T, private val delayMs: Int)
```

---

## Integration Points

### Where to Use `rememberMemoizedState`

1. **Search/Filter Operations** - In ViewModels where user input triggers expensive database queries
2. **Map Rendering** - If map styles need recomputation on every gesture
3. **Report Generation** - For periodic audit reports that process large datasets

### Where to Use `RateLimiter`

1. **Batch API Calls** - When multiple nearby stations need departure data
2. **Location Scanning** - Periodic location refreshes can be batched
3. **Background Sync** - Any scheduled background operations

### Where to Use `rememberDebounce`

1. **Search Input** - User typing in search fields
2. **Filter Controls** - Slider/checkbox changes that trigger data reloads
3. **Map Gestures** - Drag gestures that could trigger station re-scanning

---

## Next Steps

1. ✅ Add utility functions to `PerformanceHelpers.kt` - Done
2. Create integration examples showing before/after performance metrics
3. Document usage patterns in code comments

### Quick Reference

| Utility | Use Case | Location |
|---------|----------|----------|
| `rememberMemoizedState()` | Expensive computations with dependencies | `PerformanceHelpers.kt` |
| `rememberDebounce()` | User input handling (search, filters) | `PerformanceHelpers.kt` |
| `BatchRateLimiter` | Batching network requests | `PerformanceHelpers.kt` |
| `rememberBatchUpdate<T>()` | Reducing recompositions for batch updates | `PerformanceHelpers.kt` |

