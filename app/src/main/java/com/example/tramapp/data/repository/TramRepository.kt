package com.example.tramapp.data.repository

import com.example.tramapp.data.local.dao.*
import com.example.tramapp.data.local.entity.*
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.GolemioService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class NearbyInfo(val lineNames: Set<String>, val stopNames: Set<String>, val stopIds: Set<String>)

@Singleton
class TramRepository @Inject constructor(
    private val apiService: GolemioService,
    private val stationDao: StationDao,
    private val departureDao: com.example.tramapp.data.local.dao.DepartureDao,
    private val tripRouteDao: TripRouteDao,
    private val lineDirectionDao: com.example.tramapp.data.local.dao.LineDirectionDao
) {
    private val tripFetchMutex = Mutex()
    private val ongoingTripFetches = mutableMapOf<String, Deferred<List<String>>>()

    private val throttleUtil = com.example.tramapp.utils.ThrottleUtil()
    val throttleUntil: StateFlow<Long> = throttleUtil.throttleUntil

    private val _apiQueryCount = MutableStateFlow(0)
    val apiQueryCount: StateFlow<Int> = _apiQueryCount.asStateFlow()

    private suspend fun checkThrottle() {
        throttleUtil.checkThrottle()
    }

    private suspend fun handleThrottle(e: Exception) {
        throttleUtil.handleThrottle(e)
    }

    private var lastRequestTime = 0L

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var retryCount = 0
        while (true) {
            checkThrottle()
            
            // Rate Limiting: Ensure at least 300ms between requests to avoid bursts
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < 300) {
                delay(300 - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()

            try {
                _apiQueryCount.value++ // Increment debug counter
                return block()
            } catch (e: Exception) {
                handleThrottle(e)
                if (retryCount >= 2 || (e is retrofit2.HttpException && e.code() != 429)) throw e
                retryCount++
                delay(2000L * retryCount) // Longer backoff for 429s
            }
        }
    }
    val allStations: Flow<List<StationEntity>> = stationDao.getAllStations()

    suspend fun refreshNearbyStations(lat: Double, lng: Double, radius: Int): List<String> {
        val existing = stationDao.getAllStations().first()
        val recentNearby = existing.filter { s: com.example.tramapp.data.local.entity.StationEntity ->
            val dLat = s.latitude - lat
            val dLng = s.longitude - lng
            val distSq = dLat * dLat + dLng * dLng
            distSq < 0.000001 && (System.currentTimeMillis() - s.lastUpdate < 24 * 60 * 60 * 1000)
        }
        
        if (recentNearby.isNotEmpty()) {
            return recentNearby.map { s: com.example.tramapp.data.local.entity.StationEntity -> s.id }
        }

        try {
            val response = withRetry { apiService.getStops("$lat,$lng", limit = 100) }
            val stationEntities = response.features
                .filter { it.properties.locationType == 0 }
                .map { feature ->
                    val platformLabel = feature.properties.platformCode?.let { " [$it]" } ?: ""
                    StationEntity(
                        id = feature.properties.stopId,
                        name = feature.properties.stopName + platformLabel,
                        latitude = feature.geometry.coordinates[1],
                        longitude = feature.geometry.coordinates[0],
                        lastUpdate = System.currentTimeMillis()
                    )
                }

            if (stationEntities.isNotEmpty()) {
                stationDao.insertStations(stationEntities)
            }
            return stationEntities.map { it.id }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun getDepartures(stopId: String): List<DepartureItem> {
        return try {
            val response = withRetry { apiService.getDepartures(stopId) }
            val tramOnly = response.departures.filter { it.route.type == 0 }
            if (tramOnly.isNotEmpty()) {
                saveDeparturesToCache(stopId, tramOnly)
            }
            tramOnly
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveDeparturesToCache(stopId: String, departures: List<DepartureItem>) {
        departureDao.deleteDeparturesForStop(stopId)
        val entities = departures.map { item ->
            com.example.tramapp.data.local.entity.DepartureEntity(
                stopId = stopId,
                routeShortName = item.route.shortName,
                routeType = item.route.type,
                headsign = item.trip.headsign,
                arrivalTime = item.arrival.predicted ?: item.arrival.scheduled,
                isPredicted = item.arrival.predicted != null,
                tripId = item.trip.tripId
            )
        }
        departureDao.insertDepartures(entities)
    }

    suspend fun getCachedDepartures(stopId: String): List<DepartureItem> {
        val entities = departureDao.getDeparturesForStop(stopId)
        return entities.map { entity ->
            DepartureItem(
                route = com.example.tramapp.data.remote.RouteInfo(entity.routeShortName, entity.routeType),
                trip = com.example.tramapp.data.remote.TripInfo(entity.headsign, entity.tripId),
                arrival = com.example.tramapp.data.remote.TimestampInfo(entity.arrivalTime, if (entity.isPredicted) entity.arrivalTime else null),
                stop = com.example.tramapp.data.remote.StopInfo(entity.stopId)
            )
        }
    }

    suspend fun toggleFavorite(stationId: String, isFavorite: Boolean) {
        stationDao.updateFavoriteStatus(stationId, isFavorite)
    }

    suspend fun getTripSequence(lineName: String, headsign: String, tripId: String): List<String> {
        val routeKey = "$lineName-$headsign"
        // 1. Check Room Cache by RouteKey
        val cached = tripRouteDao.getTripRoute(routeKey)
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 24 * 60 * 60 * 1000) {
            if (cached.stopIds == "EMPTY") return emptyList()
            // Support legacy format if it exists, or new format
            if (cached.stopIds.contains("||ID:")) {
                val parts = cached.stopIds.split("||ID:")
                return parts[1].split("|")
            }
            return cached.stopIds.split("|")
        }

        // 2. Network Fetch (Atomic per RouteKey)
        val deferred = tripFetchMutex.withLock {
            ongoingTripFetches[routeKey] ?: CoroutineScope(Dispatchers.IO).async {
                try {
                    val response = withRetry { apiService.getTripDetails(tripId) }
                    val sorted = response.stopTimes.sortedBy { it.stopSequence }
                    val ids = sorted.map { it.stopId }
                    
                    tripRouteDao.insertTripRoute(
                        TripRouteEntity(
                            routeKey = routeKey,
                            stopIds = ids.joinToString("|"),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    
                    if (ids.isEmpty()) {
                        // Cache the failure for 1 minute to prevent hammering the API
                        tripRouteDao.insertTripRoute(
                            TripRouteEntity(
                                routeKey = routeKey,
                                stopIds = "EMPTY",
                                timestamp = System.currentTimeMillis() - (24 * 60 * 60 * 1000 - 60 * 1000) // Expires in 1 minute
                            )
                        )
                    }
                    ids
                } catch (e: Exception) {
                    emptyList<String>()
                } finally {
                    tripFetchMutex.withLock { ongoingTripFetches.remove(routeKey) }
                }
            }.also { ongoingTripFetches[routeKey] = it }
        }

        return deferred.await()
    }

    suspend fun getNearbyInfo(lat: Double, lng: Double): NearbyInfo {
        // 1. Check local DB for recent stations in this area
        val existing = stationDao.getAllStations().first()
        val recentNearby = existing.filter { s: com.example.tramapp.data.local.entity.StationEntity ->
            val dLat = s.latitude - lat
            val dLng = s.longitude - lng
            val distSq = dLat * dLat + dLng * dLng
            distSq < 0.0001 && (System.currentTimeMillis() - s.lastUpdate < 24 * 60 * 60 * 1000)
        }

        if (recentNearby.isNotEmpty()) {
            return NearbyInfo(
                emptySet(), 
                recentNearby.map { it.name.replace(Regex("\\s*\\[.*]$"), "").trim() }.toSet(),
                recentNearby.map { it.id }.toSet()
            )
        }

        val stopNames = mutableSetOf<String>()
        val stopIds = mutableSetOf<String>()
        try {
            val response = withRetry { apiService.getStops("$lat,$lng", limit = 20) }
            val nearbyStops = response.features
                .filter { it.properties.locationType == 0 }
                .filter { stop ->
                    val dLat = stop.geometry.coordinates[1] - lat
                    val dLng = stop.geometry.coordinates[0] - lng
                    dLat * dLat + dLng * dLng <= 0.0001 
                }
 
            for (stop in nearbyStops) {
                val baseName = stop.properties.stopName.replace(Regex("\\s*\\[.*]$"), "").trim()
                stopNames.add(baseName)
                stopIds.add(stop.properties.stopId)
            }
        } catch (e: Exception) { /* skip */ }
        return NearbyInfo(emptySet(), stopNames, stopIds)
    }

    suspend fun getCachedDirection(stopId: String, lineName: String, headsign: String, destType: String): Boolean? {
        return lineDirectionDao.getDirection(stopId, lineName, headsign, destType)?.isBound
    }

    suspend fun saveDirection(stopId: String, lineName: String, headsign: String, destType: String, isBound: Boolean) {
        lineDirectionDao.insertDirection(
            com.example.tramapp.data.local.entity.LineDirectionEntity(
                stopId, lineName, headsign, destType, isBound, System.currentTimeMillis()
            )
        )
    }

    suspend fun getTripDetails(tripId: String, routeName: String, destination: String): com.example.tramapp.domain.TripDetails {
        val response = withRetry { apiService.getTripDetails(tripId) }
        val stations = response.stopTimes.map { 
            com.example.tramapp.domain.TripStation(id = it.stopId, name = it.stop?.stopName ?: "Station ${it.stopId}", sequence = it.stopSequence)
        }.sortedBy { it.sequence }
        val polyline = response.shapes.map { feature ->
            com.google.android.gms.maps.model.LatLng(feature.geometry.coordinates[1], feature.geometry.coordinates[0])
        }
        return com.example.tramapp.domain.TripDetails(tripId, routeName, destination, stations, polyline)
    }
}
