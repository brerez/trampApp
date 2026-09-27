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

    // A (line, headsign) route's stop sequence is shared by every trip on it and by every
    // platform it passes, so one fetch serves them all. Trip ids change every few minutes;
    // caching per trip id re-fetched each new trip and blew through the API rate limit.
    private data class CachedSequence(val stops: List<TripStop>, val fetchedAt: Long)
    private val sequenceMutex = Mutex()
    private val sequences = mutableMapOf<String, CachedSequence>()
    private val inFlight = mutableMapOf<String, Deferred<List<TripStop>?>>()

    private fun routeKeyOf(dep: DepartureItem): String =
        "route:${dep.route.shortName}|${dep.trip.headsign}"

    /**
     * Only already-resolved routes (DB, not expired). Never touches the network.
     * Key = tripId; every trip on a resolved (line, headsign) route at this platform shares the
     * route's result.
     */
    suspend fun cached(platformStopId: String, departures: List<DepartureItem>): Map<String, TripNextStop> {
        val withTrip = departures.filter { it.trip.tripId != null }
        if (withTrip.isEmpty()) return emptyMap()
        val routeKeys = withTrip.map { routeKeyOf(it) }.distinct()
        val minFetchedAt = clock() - TTL_MS
        val rows = dao.get(platformStopId, routeKeys, minFetchedAt).associateBy { it.tripId }
        return withTrip.mapNotNull { dep ->
            val row = rows[routeKeyOf(dep)] ?: return@mapNotNull null
            val tripId = dep.trip.tripId!!
            tripId to TripNextStop(
                tripId = tripId,
                platformStopId = row.platformStopId,
                nextStopId = row.nextStopId,
                nextStopName = row.nextStopName,
                downstreamNodeIds = if (row.downstreamNodeIds.isBlank()) emptySet()
                    else row.downstreamNodeIds.split(",").filter { it.isNotBlank() }.toSet()
            )
        }.toMap()
    }

    /**
     * cached + newly resolved. Departures with null trip id are ignored.
     * Key = tripId.
     */
    suspend fun resolve(platformStopId: String, departures: List<DepartureItem>): Map<String, TripNextStop> {
        val alreadyCached = cached(platformStopId, departures)
        val unresolved = departures.filter { dep ->
            dep.trip.tripId != null && dep.trip.tripId !in alreadyCached
        }
        if (unresolved.isEmpty()) return alreadyCached

        coroutineScope {
            unresolved.groupBy { routeKeyOf(it) }.map { (routeKey, groupDeps) ->
                async {
                    val stops = sequenceFor(routeKey, groupDeps.first().trip.tripId!!)
                    if (stops != null) store(platformStopId, routeKey, stops)
                }
            }.awaitAll()
        }

        return cached(platformStopId, departures)
    }

    /** The route's stop sequence: memory cache, else one fetch shared by concurrent callers.
     *  Null on failure (not cached, so a later refresh retries). */
    private suspend fun sequenceFor(routeKey: String, representativeTripId: String): List<TripStop>? {
        val deferred = coroutineScope {
            sequenceMutex.withLock {
                val hit = sequences[routeKey]
                if (hit != null && clock() - hit.fetchedAt < TTL_MS) return@coroutineScope null
                inFlight[routeKey] ?: async {
                    try {
                        fetchSemaphore.withPermit {
                            source.tripStops(representativeTripId).sortedBy { it.sequence }
                        }.also { stops ->
                            if (stops.isNotEmpty()) {
                                sequenceMutex.withLock { sequences[routeKey] = CachedSequence(stops, clock()) }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    } finally {
                        sequenceMutex.withLock { inFlight.remove(routeKey) }
                    }
                }.also { inFlight[routeKey] = it }
            }
        }
        if (deferred == null) return sequenceMutex.withLock { sequences[routeKey]?.stops }
        return deferred.await()
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
     * Compute this platform's next stop from the route's sequence and persist it under the
     * route key. Writes nothing when the platform is the terminus or not on the route.
     */
    private suspend fun store(platformStopId: String, routeKey: String, sorted: List<TripStop>) {
        if (sorted.isEmpty()) return

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
        val nextStopName = nextStop.name ?: try {
            source.stopNames(listOf(nextStop.stopId))[nextStop.stopId]
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: nextStop.stopId

        val downstreamNodeIds = downstreamStops
            .map { JunctionDirectory.nodeIdOf(it.stopId) }
            .filter { it.isNotBlank() }
            .toSet()
            .joinToString(",")

        dao.insertAll(
            listOf(
                TripNextStopEntity(
                    tripId = routeKey,
                    platformStopId = platformStopId,
                    nextStopId = nextStop.stopId,
                    nextStopName = nextStopName,
                    downstreamNodeIds = downstreamNodeIds,
                    fetchedAt = clock()
                )
            )
        )
    }
}
