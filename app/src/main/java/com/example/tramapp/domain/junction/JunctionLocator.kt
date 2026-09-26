package com.example.tramapp.domain.junction

import com.example.tramapp.data.repository.TramRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared junction discovery glue (U78 part 1). Wraps TramRepository + JunctionDirectory so both
 * the session service and the dashboard can ask "what junctions are near this point" without
 * duplicating the rediscovery/mapping logic.
 *
 * Rediscovery state (lastPoint/lastTime) is per-process, guarded by a mutex since the service
 * and the dashboard may call this concurrently.
 */
@Singleton
class JunctionLocator @Inject constructor(
    private val repository: TramRepository,
) {
    /** Test seam: override to control time. */
    var clock: () -> Long = System::currentTimeMillis

    private val mutex = Mutex()
    private var lastPoint: GeoPoint? = null
    private var lastTimeMs: Long? = null

    /**
     * Returns the junctions within/around [point]. Triggers a station refresh (one batched
     * `refreshNearbyStations` call) only when JunctionDirectory.shouldRediscover says the cache
     * is stale for this point; otherwise reads the existing Room cache. Stations whose isTram is
     * still null (unknown) are included by JunctionDirectory.build; only isTram == false is
     * excluded. Callers should re-invoke this after each JunctionEngine.refresh so that
     * newly-learned bus-only nodes drop out (isTram gets written by the engine's departure call).
     */
    suspend fun junctionsNear(point: GeoPoint, radiusM: Int): List<Junction> {
        val now = clock()
        mutex.withLock {
            if (JunctionDirectory.shouldRediscover(lastPoint, lastTimeMs, point, now)) {
                repository.refreshNearbyStations(point.lat, point.lng, radiusM)
                lastPoint = point
                lastTimeMs = now
            }
        }

        val stations = repository.allStations.first()
        val platformStops = stations
            .filter { !it.nodeId.isNullOrBlank() }
            .map { s ->
                PlatformStop(
                    stopId = s.id,
                    nodeId = s.nodeId!!,
                    name = s.name,
                    platformCode = s.platformCode,
                    lat = s.latitude,
                    lng = s.longitude,
                    isTram = s.isTram,
                )
            }

        return JunctionDirectory.build(platformStops)
    }
}
