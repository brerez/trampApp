package com.example.tramapp.domain.junction

import com.example.tramapp.data.remote.DepartureItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface JunctionDepartureSource {
    suspend fun getJunctionDepartures(platformIds: List<String>): Map<String, List<DepartureItem>>
}

interface NextStopLookup {
    suspend fun cachedAll(
        departuresByPlatform: Map<String, List<DepartureItem>>
    ): Map<String, Map<String, TripNextStop>>

    suspend fun resolveAll(
        departuresByPlatform: Map<String, List<DepartureItem>>
    ): Map<String, Map<String, TripNextStop>>
}

@Singleton
class JunctionEngine @Inject constructor(
    private val departures: JunctionDepartureSource,
    private val nextStops: NextStopLookup,
    private val destinations: DestinationNodeSource,
) {
    /** Test seam: override to control time. */
    var clock: () -> Long = System::currentTimeMillis

    companion object {
        const val COALESCE_MS = 5_000L
    }

    // Per-nodeId state flow, mutex, and last-refresh tracking.
    // Read from the notification service and the dashboard concurrently.
    private val flows = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<JunctionSnapshot?>>()
    private val refreshMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    // Only touched while holding that junction's refresh mutex.
    private val lastRefreshCompletedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastRefreshStartedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Hot per-junction snapshot flow; value null until the junction is first refreshed/shown.
     * Same instance per nodeId.
     */
    fun snapshot(nodeId: String): StateFlow<JunctionSnapshot?> {
        return getOrCreateFlow(nodeId).asStateFlow()
    }

    /**
     * Emit structure only (no network): if no snapshot yet → JunctionSnapshot(junction, null, null, LOADING);
     * otherwise update the junction structure keeping rows/state.
     */
    fun show(junction: Junction) {
        val flow = getOrCreateFlow(junction.nodeId)
        val current = flow.value
        flow.value = if (current == null) {
            JunctionSnapshot(
                junction = junction,
                rows = null,
                fetchedAtMs = null,
                state = SnapshotState.LOADING
            )
        } else {
            current.copy(junction = junction)
        }
    }

    /**
     * 1) show(junction).
     * 2) Coalesce: if a refresh for this nodeId completed or started less than COALESCE_MS ago
     *    (and force == false), return without a network call (an in-flight refresh is joined).
     * 3) ONE getJunctionDepartures call with all platform ids.
     * 4) Emit rows built with cachedAll() results (end-stop fallback for the rest), state LIVE,
     *    fetchedAtMs = now.
     * 5) resolveAll(); if anything new resolved, re-emit rows regrouped by next stop with highlights.
     * On exception in step 3: keep previous rows and fetchedAtMs, state ERROR, lastErrorAtMs = now;
     * do not throw (CancellationException must propagate).
     * Per-nodeId Mutex serialises refreshes.
     */
    suspend fun refresh(junction: Junction, force: Boolean = false) {
        show(junction)

        val nodeId = junction.nodeId
        val mutex = getOrCreateRefreshMutex(nodeId)

        mutex.withLock {
            if (!force) {
                val now = clock()
                val lastCompleted = lastRefreshCompletedAt[nodeId] ?: 0L
                val lastStarted = lastRefreshStartedAt[nodeId] ?: 0L
                if (now - maxOf(lastCompleted, lastStarted) < COALESCE_MS) {
                    return
                }
            }

            val now = clock()
            lastRefreshStartedAt[nodeId] = now

            val flow = getOrCreateFlow(nodeId)
            val platformIds = junction.platforms.map { it.stopId }

            // Step 3: ONE network call
            val departuresByPlatform: Map<String, List<DepartureItem>>
            try {
                departuresByPlatform = departures.getJunctionDepartures(platformIds)
            } catch (e: CancellationException) {
                lastRefreshCompletedAt[nodeId] = clock()
                throw e
            } catch (e: Exception) {
                val errorNow = clock()
                lastRefreshCompletedAt[nodeId] = errorNow
                val current = flow.value
                flow.value = JunctionSnapshot(
                    junction = junction,
                    rows = current?.rows,
                    fetchedAtMs = current?.fetchedAtMs,
                    state = SnapshotState.ERROR,
                    lastErrorAtMs = errorNow
                )
                return
            }

            // Step 4: emit with cachedAll results
            val fetchedAt = clock()
            val here = junction.platforms.firstOrNull()?.position
            fun DestinationNodes.forHere() = if (here == null) this else awayFrom(here)
            val destNodes = safely(DestinationNodes.NONE) { destinations.current() }.forHere()
            val cached = safely(emptyMap()) { nextStops.cachedAll(departuresByPlatform) }
            val cachedRows = JunctionRowBuilder.build(junction, departuresByPlatform, cached, destNodes)

            flow.value = JunctionSnapshot(
                junction = junction,
                rows = cachedRows,
                fetchedAtMs = fetchedAt,
                state = SnapshotState.LIVE,
                lastErrorAtMs = null
            )

            // Step 5: resolveAll; re-emit if anything changed. A resolver failure keeps the
            // live fallback rows; unresolved trips retry on the next refresh.
            val resolved = try {
                nextStops.resolveAll(departuresByPlatform)
            } catch (e: CancellationException) {
                lastRefreshCompletedAt[nodeId] = clock()
                throw e
            } catch (e: Exception) {
                lastRefreshCompletedAt[nodeId] = clock()
                return
            }

            // Check if resolution added anything new
            val anyNew = resolved.any { (platformId, tripMap) ->
                val cachedPlatform = cached[platformId].orEmpty()
                tripMap.keys.any { tripId -> tripId !in cachedPlatform }
            }

            if (anyNew) {
                val resolvedDestNodes = safely(destNodes) { destinations.current().forHere() }
                val resolvedRows = JunctionRowBuilder.build(junction, departuresByPlatform, resolved, resolvedDestNodes)
                flow.value = JunctionSnapshot(
                    junction = junction,
                    rows = resolvedRows,
                    fetchedAtMs = fetchedAt,
                    state = SnapshotState.LIVE,
                    lastErrorAtMs = null
                )
            }

            lastRefreshCompletedAt[nodeId] = clock()
        }
    }

    // ---------- private helpers ----------

    /** Local reads (DataStore, Room) must not fail a refresh whose departures already arrived. */
    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }

    private fun getOrCreateFlow(nodeId: String): MutableStateFlow<JunctionSnapshot?> =
        flows.getOrPut(nodeId) { MutableStateFlow(null) }

    private fun getOrCreateRefreshMutex(nodeId: String): Mutex =
        refreshMutexes.getOrPut(nodeId) { Mutex() }
}
