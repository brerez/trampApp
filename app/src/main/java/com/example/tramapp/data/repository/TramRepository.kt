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
    private val lineDirectionDao: com.example.tramapp.data.local.dao.LineDirectionDao,
    private val throttleUtil: com.example.tramapp.utils.ThrottleUtil
) {
    private val tripFetchMutex = Mutex()
    private val ongoingTripFetches = mutableMapOf<String, Deferred<List<Pair<String, String>>>>()
    val throttleUntil: StateFlow<Long> = throttleUtil.throttleUntil

    private val _apiQueryCount = MutableStateFlow(0)
    val apiQueryCount: StateFlow<Int> = _apiQueryCount.asStateFlow()

    private suspend fun checkThrottle() {
        throttleUtil.checkThrottle()
    }

    private suspend fun handleThrottle(e: Exception) {
        throttleUtil.handleThrottle(e)
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var retryCount = 0
        while (true) {
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
        val now = System.currentTimeMillis()
        val sixAmToday = java.time.ZonedDateTime.now()
            .withHour(6).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()

        val recentNearby = existing.filter { s: com.example.tramapp.data.local.entity.StationEntity ->
            val dLat = s.latitude - lat
            val dLng = s.longitude - lng
            val distSq = dLat * dLat + dLng * dLng
            
            val updateTime = java.time.Instant.ofEpochMilli(s.lastUpdate)
                .atZone(java.time.ZoneId.systemDefault())
            val hour = updateTime.hour
            val isNightDecision = hour >= 23 || hour < 6 // 11 PM to 6 AM
            
            val isStale = s.isTram == false && isNightDecision && s.lastUpdate < sixAmToday && now >= sixAmToday
            val isAllowed = s.isTram != false || isStale
            
            distSq < 0.000001 && (now - s.lastUpdate < 24 * 60 * 60 * 1000) && isAllowed
        }
        
        if (recentNearby.isNotEmpty()) {
            return recentNearby.map { s: com.example.tramapp.data.local.entity.StationEntity -> s.id }
        }

        try {
            val response = withRetry { apiService.getStops("$lat,$lng", limit = 1000) }
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
        val response = withRetry { apiService.getDepartures(stopId) }
        val tramOnly = response.departures.filter { it.route.type == 0 }
        if (tramOnly.isNotEmpty()) {
            saveDeparturesToCache(stopId, tramOnly)
            stationDao.updateIsTramStatus(stopId, true)
        } else if (response.departures.isNotEmpty()) {
            stationDao.updateIsTramStatus(stopId, false)
        }
        return tramOnly
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

    suspend fun getTripSequence(lineName: String, headsign: String, tripId: String): List<Pair<String, String>> {
        val routeKey = "$lineName-$headsign"
        // 1. Check Room Cache by RouteKey
        val cached = tripRouteDao.getTripRoute(routeKey)
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 24 * 60 * 60 * 1000) {
            if (cached.stopIds == "EMPTY") {
                return emptyList()
            }
            
            if (cached.stopIds.contains("||NAMES:")) {
                val parts = cached.stopIds.split("||NAMES:")
                val ids = parts[0].split("|")
                val names = parts[1].split("|")
                return ids.mapIndexed { index, id -> id to names.getOrElse(index) { "" } }
            }
            
            // If it's old cache without names, don't use it!
            // Fall through to network fetch to migrate the cache.
        }

        // 2. Network Fetch (Atomic per RouteKey)
        val deferred = tripFetchMutex.withLock {
            ongoingTripFetches[routeKey] ?: CoroutineScope(Dispatchers.IO).async {
                try {
                    val response = withRetry { apiService.getTripDetails(tripId) }
                    val sorted = response.stopTimes.sortedBy { it.stopSequence }
                    val ids = sorted.map { it.stopId }
                    
                    // Fetch names for all IDs to support fallback matching
                    val namesResponse = withRetry { apiService.getStopsByIds(ids) }
                    val nameMap = namesResponse.features.associate { it.properties.stopId to it.properties.stopName }
                    val names = ids.map { nameMap[it] ?: "" }
                    
                    val stopIdsString = ids.joinToString("|") + "||NAMES:" + names.joinToString("|")
                    
                    tripRouteDao.insertTripRoute(
                        TripRouteEntity(
                            routeKey = routeKey,
                            stopIds = stopIdsString,
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
                    ids.mapIndexed { index, id -> id to names.getOrElse(index) { "" } }
                } catch (e: Exception) {
                    emptyList<Pair<String, String>>()
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
        } catch (e: Exception) { 
            android.util.Log.w("TramRepository", "Failed to fetch nearby stops in getNearbyInfo", e)
        }
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

    fun getTripDetailsFlow(tripId: String, routeName: String, destination: String): kotlinx.coroutines.flow.Flow<com.example.tramapp.domain.TripDetails> = kotlinx.coroutines.flow.flow {
        val response = withRetry { apiService.getTripDetails(tripId) }
        val allStations = stationDao.getAllStations().first()
        
        val initialStations = response.stopTimes.map { 
            val cachedName = allStations.find { s -> s.id == it.stopId }?.name
            com.example.tramapp.domain.TripStation(
                id = it.stopId, 
                name = it.stop?.stopName ?: cachedName ?: "Station ${it.stopId}", 
                sequence = it.stopSequence
            )
        }.sortedBy { it.sequence }
        
        val polyline = response.shapes.map { feature ->
            com.google.android.gms.maps.model.LatLng(feature.geometry.coordinates[1], feature.geometry.coordinates[0])
        }
        
        val initialDetails = com.example.tramapp.domain.TripDetails(tripId, routeName, destination, initialStations, polyline)
        emit(initialDetails) // Emit initial state with IDs!
        
        // Find IDs that are missing names
        val missingIds = response.stopTimes
            .filter { it.stop?.stopName == null && allStations.none { s -> s.id == it.stopId } }
            .map { it.stopId }
            .distinct()
            
        if (missingIds.isNotEmpty()) {
            val missingNamesMap = mutableMapOf<String, String>()
            // Try to fetch missing stops by ID one by one
            missingIds.forEach { id ->
                try {
                    val stopsResponse = withRetry { apiService.getStopById(id) }
                    stopsResponse.features.firstOrNull()?.let { feature ->
                        val platformLabel = feature.properties.platformCode?.let { " [$it]" } ?: ""
                        val name = feature.properties.stopName + platformLabel
                        missingNamesMap[feature.properties.stopId] = name
                        
                        // Cache it in local database
                        stationDao.insertStations(
                            listOf(
                                com.example.tramapp.data.local.entity.StationEntity(
                                    id = feature.properties.stopId,
                                    name = name,
                                    latitude = feature.geometry.coordinates[1],
                                    longitude = feature.geometry.coordinates[0],
                                    lastUpdate = System.currentTimeMillis(),
                                    isTram = true
                                )
                            )
                        )
                    }
                } catch (e: Exception) { 
                    android.util.Log.w("TramRepository", "Failed to resolve name for missing stop in getTripStations", e)
                }
            }
            
            // Emit updated state!
            val updatedStations = response.stopTimes.map { 
                val cachedName = allStations.find { s -> s.id == it.stopId }?.name
                val fetchedName = missingNamesMap[it.stopId]
                com.example.tramapp.domain.TripStation(
                    id = it.stopId, 
                    name = it.stop?.stopName ?: cachedName ?: fetchedName ?: "Station ${it.stopId}", 
                    sequence = it.stopSequence
                )
            }.sortedBy { it.sequence }
            
            emit(initialDetails.copy(stations = updatedStations))
        }
    }
}
