package com.example.tramapp.data.repository

import com.example.tramapp.data.local.dao.*
import com.example.tramapp.data.local.entity.*
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.GolemioService
import com.example.tramapp.domain.junction.JunctionDepartureSource
import com.example.tramapp.domain.junction.JunctionStationSource
import com.example.tramapp.domain.junction.TripSequenceSource
import com.example.tramapp.domain.junction.TripStop
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
    private val throttleUtil: com.example.tramapp.utils.ThrottleUtil
) : TripSequenceSource, JunctionDepartureSource, JunctionStationSource {
    val throttleUntil: StateFlow<Long> = throttleUtil.throttleUntil

    private val _apiQueryCount = MutableStateFlow(0)
    val apiQueryCount: StateFlow<Int> = _apiQueryCount.asStateFlow()

    /** PID stop node id = stop id prefix before the first 'Z' (e.g. "U324Z1P" -> "U324"). */
    private fun nodeIdOf(stopId: String): String = stopId.substringBefore('Z')

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
    override val allStations: Flow<List<StationEntity>> = stationDao.getAllStations()

    override suspend fun refreshNearbyStations(lat: Double, lng: Double, radius: Int): List<String> {
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
                        lastUpdate = System.currentTimeMillis(),
                        nodeId = nodeIdOf(feature.properties.stopId),
                        platformCode = feature.properties.platformCode
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

    /** One batched call for all platforms of a junction. Returns tram-only departures keyed by platform stop id
     *  (every requested id present as a key, possibly empty list). Updates isTram per platform: true if the
     *  platform has any tram departure, false if it has departures but none are trams, unchanged if none at all.
     *  Uses the existing withRetry (throttle back-off + one retry on 429). Does NOT write the departure cache. */
    override suspend fun getJunctionDepartures(platformIds: List<String>): Map<String, List<DepartureItem>> {
        val response = withRetry { apiService.getDepartureBoards(platformIds) }
        val idSet = platformIds.toSet()
        val byPlatform: Map<String, List<DepartureItem>> = platformIds.associateWith { mutableListOf<DepartureItem>() }
        val grouped = byPlatform.mapValues { it.value as MutableList<DepartureItem> }
        for (item in response.departures) {
            val platformId = item.stop.id
            if (platformId !in idSet) continue
            grouped[platformId]?.add(item)
        }

        val result = mutableMapOf<String, List<DepartureItem>>()
        for (platformId in platformIds) {
            val all = grouped[platformId].orEmpty()
            val trams = all.filter { it.route.type == 0 }
            if (trams.isNotEmpty()) {
                stationDao.updateIsTramStatus(platformId, true)
            } else if (all.isNotEmpty()) {
                stationDao.updateIsTramStatus(platformId, false)
            }
            result[platformId] = trams
        }
        return result
    }


    suspend fun toggleFavorite(stationId: String, isFavorite: Boolean) {
        stationDao.updateFavoriteStatus(stationId, isFavorite)
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
            try { android.util.Log.w("TramRepository", "Failed to fetch nearby stops in getNearbyInfo", e) } catch (_: Exception) {}
        }
        return NearbyInfo(emptySet(), stopNames, stopIds)
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
                                    isTram = true,
                                    nodeId = nodeIdOf(feature.properties.stopId),
                                    platformCode = feature.properties.platformCode
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

    // ---- TripSequenceSource implementation ----

    override suspend fun tripStops(tripId: String): List<TripStop> {
        val response = withRetry {
            apiService.getTripDetails(tripId, includeStopTimes = true, includeShapes = false)
        }
        val allStations = stationDao.getAllStations().first()
        return response.stopTimes.map { st ->
            TripStop(
                stopId = st.stopId,
                sequence = st.stopSequence,
                name = st.stop?.stopName
                    ?: allStations.find { s -> s.id == st.stopId }
                        ?.name?.replace(Regex("\\s*\\[.*]$"), "")?.trim()
            )
        }
    }

    override suspend fun stopNames(stopIds: List<String>): Map<String, String> {
        if (stopIds.isEmpty()) return emptyMap()
        // Check station cache first
        val allStations = stationDao.getAllStations().first()
        val result = mutableMapOf<String, String>()
        val stillMissing = mutableListOf<String>()
        for (id in stopIds) {
            val cached = allStations.find { s -> s.id == id }
            if (cached != null) {
                result[id] = cached.name.replace(Regex("\\s*\\[.*]$"), "").trim()
            } else {
                stillMissing.add(id)
            }
        }
        if (stillMissing.isNotEmpty()) {
            try {
                val response = withRetry { apiService.getStopsByIds(stillMissing) }
                response.features.forEach { feature ->
                    result[feature.properties.stopId] = feature.properties.stopName
                }
            } catch (e: Exception) {
                // Best-effort; callers handle missing names gracefully
            }
        }
        return result
    }
}
