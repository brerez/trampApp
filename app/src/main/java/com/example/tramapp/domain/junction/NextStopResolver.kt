package com.example.tramapp.domain.junction

import com.example.tramapp.data.local.dao.TripNextStopDao
import com.example.tramapp.data.local.entity.TripNextStopEntity
import com.example.tramapp.data.remote.DepartureItem
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

data class TripStop(val stopId: String, val sequence: Int, val name: String?)

/**
 * Abstraction over the network so the resolver is unit-testable.
 * TramRepository implements it using withRetry.
 */
interface TripSequenceSource {
    /** getTripDetails(tripId, includeStopTimes=true, includeShapes=false) */
    suspend fun tripStops(tripId: String): List<TripStop>

    /** one getStopsByIds call; cached station names first */
    suspend fun stopNames(stopIds: List<String>): Map<String, String>
}

data class TripNextStop(
    val tripId: String,
    val platformStopId: String,
    val nextStopId: String,
    val nextStopName: String,
    val downstreamNodeIds: Set<String>
)

@Singleton
class NextStopResolver @Inject constructor(
    private val source: TripSequenceSource,
    private val dao: TripNextStopDao,
) : NextStopLookup {
    /** Test seam: override to control time. */
    var clock: () -> Long = System::currentTimeMillis

    companion object {
        const val TTL_MS = 24L * 60 * 60 * 1000
        const val MAX_CONCURRENT_FETCHES = 4
    }

    // Semaphore to cap concurrent network fetches at 4
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    // Deduplication: per (representativeTripId) in-flight Deferred
    private val inFlightMutex = Mutex()
    private val inFlightMap = mutableMapOf<String, Deferred<Unit>>()

    /**
     * Only already-resolved trips (DB, not expired). Never touches the network.
     * Key = tripId.
     */
    suspend fun cached(platformStopId: String, departures: List<DepartureItem>): Map<String, TripNextStop> {
        val tripIds = departures.mapNotNull { it.trip.tripId }.distinct()
        if (tripIds.isEmpty()) return emptyMap()
        val minFetchedAt = clock() - TTL_MS
        val rows = dao.get(platformStopId, tripIds, minFetchedAt)
        return rows.associate { row ->
            row.tripId to TripNextStop(
                tripId = row.tripId,
                platformStopId = row.platformStopId,
                nextStopId = row.nextStopId,
                nextStopName = row.nextStopName,
                downstreamNodeIds = if (row.downstreamNodeIds.isBlank()) emptySet()
                    else row.downstreamNodeIds.split(",").filter { it.isNotBlank() }.toSet()
            )
        }
    }

    /**
     * cached + newly resolved. Departures with null trip id are ignored.
     * Key = tripId.
     */
    suspend fun resolve(platformStopId: String, departures: List<DepartureItem>): Map<String, TripNextStop> {
        // First, fetch anything already in cache
        val alreadyCached = cached(platformStopId, departures)
        val unresolvedDepartures = departures.filter { dep ->
            dep.trip.tripId != null && dep.trip.tripId !in alreadyCached
        }
        if (unresolvedDepartures.isEmpty()) return alreadyCached

        // Group unresolved by (lineShortName, headsign) to pick one representative per group
        val groups = unresolvedDepartures.groupBy { dep ->
            dep.route.shortName to dep.trip.headsign
        }

        coroutineScope {
            val asyncJobs = groups.mapNotNull { (_, groupDeps) ->
                // The representative trip is the first with a non-null tripId
                val representativeTrip = groupDeps.firstOrNull { it.trip.tripId != null }
                    ?: return@mapNotNull null
                val representativeTripId = representativeTrip.trip.tripId!!
                val allTripIdsInGroup = groupDeps.mapNotNull { it.trip.tripId }.distinct()

                async {
                    // Dedup: if same representativeTripId is already in flight, share the deferred
                    val deferred: Deferred<Unit> = inFlightMutex.withLock {
                        val existing = inFlightMap[representativeTripId]
                        if (existing != null) {
                            existing
                        } else {
                            val newDeferred: Deferred<Unit> = async {
                                fetchAndStore(platformStopId, representativeTripId, allTripIdsInGroup)
                            }
                            inFlightMap[representativeTripId] = newDeferred
                            newDeferred
                        }
                    }
                    try {
                        deferred.await()
                    } finally {
                        inFlightMutex.withLock {
                            // Only remove if still pointing to our deferred
                            if (inFlightMap[representativeTripId] === deferred) {
                                inFlightMap.remove(representativeTripId)
                            }
                        }
                    }
                }
            }
            asyncJobs.awaitAll()
        }

        return cached(platformStopId, departures)
    }

    /**
     * Resolve many platforms of a junction concurrently (still sharing the 4-fetch cap).
     * Key = platformStopId.
     */
    override suspend fun resolveAll(departuresByPlatform: Map<String, List<DepartureItem>>): Map<String, Map<String, TripNextStop>> {
        return coroutineScope {
            departuresByPlatform.map { (platformStopId, deps) ->
                async { platformStopId to resolve(platformStopId, deps) }
            }.awaitAll().toMap()
        }
    }

    override suspend fun cachedAll(departuresByPlatform: Map<String, List<DepartureItem>>): Map<String, Map<String, TripNextStop>> {
        return coroutineScope {
            departuresByPlatform.map { (platformStopId, deps) ->
                async { platformStopId to cached(platformStopId, deps) }
            }.awaitAll().toMap()
        }
    }

    /**
     * Fetch trip stop sequence for the representative trip, compute next-stop info,
     * and write results for all trips in the group.
     * Does NOT write anything if platform is terminus or not found.
     */
    private suspend fun fetchAndStore(
        platformStopId: String,
        representativeTripId: String,
        allTripIdsInGroup: List<String>
    ) {
        fetchSemaphore.withPermit {
            val stops = try {
                source.tripStops(representativeTripId)
            } catch (e: Exception) {
                // On failure: leave trips unresolved; a later call will retry
                return
            }

            if (stops.isEmpty()) return

            val sorted = stops.sortedBy { it.sequence }

            // Find the platform stop: exact stop id first, else first entry with same node id
            val platformNodeId = JunctionDirectory.nodeIdOf(platformStopId)
            var platformIdx = sorted.indexOfFirst { it.stopId == platformStopId }
            if (platformIdx == -1) {
                platformIdx = sorted.indexOfFirst { JunctionDirectory.nodeIdOf(it.stopId) == platformNodeId }
            }

            // Platform not found or is terminus (last stop) -> leave unresolved
            if (platformIdx == -1 || platformIdx >= sorted.size - 1) return

            val nextStop = sorted[platformIdx + 1]
            val downstreamStops = sorted.drop(platformIdx + 1)  // all stops after platform

            // Only the next stop is displayed; downstream stops need node ids, not names.
            val missingNameIds = if (nextStop.name == null) listOf(nextStop.stopId) else emptyList()

            // Batch-fetch missing names in one call (if any)
            val fetchedNames: Map<String, String> = if (missingNameIds.isNotEmpty()) {
                try {
                    source.stopNames(missingNameIds)
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }

            fun nameFor(stop: TripStop): String =
                stop.name ?: fetchedNames[stop.stopId] ?: stop.stopId

            val nextStopName = nameFor(nextStop)
            val nextStopId = nextStop.stopId
            val downstreamNodeIds = downstreamStops
                .map { JunctionDirectory.nodeIdOf(it.stopId) }
                .filter { it.isNotBlank() }
                .toSet()
                .joinToString(",")

            val now = clock()
            val entities = allTripIdsInGroup.map { tripId ->
                TripNextStopEntity(
                    tripId = tripId,
                    platformStopId = platformStopId,
                    nextStopId = nextStopId,
                    nextStopName = nextStopName,
                    downstreamNodeIds = downstreamNodeIds,
                    fetchedAt = now
                )
            }
            dao.insertAll(entities)
        }
    }
}
