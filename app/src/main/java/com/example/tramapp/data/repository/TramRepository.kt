package com.example.tramapp.data.repository

import com.example.tramapp.data.local.dao.StationDao
import com.example.tramapp.data.local.entity.StationEntity
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.GolemioService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TramRepository @Inject constructor(
    private val apiService: GolemioService,
    private val stationDao: StationDao,
    private val departureDao: com.example.tramapp.data.local.dao.DepartureDao
) {
    val allStations: Flow<List<StationEntity>> = stationDao.getAllStations()

    suspend fun refreshNearbyStations(lat: Double, lng: Double, radius: Int): List<String> {
        val response = apiService.getStops("$lat,$lng", limit = 20)
        val entities = response.features.filter { it.properties.locationType == 0 }.map { feature ->
            val platform = feature.properties.platformCode ?: ""
            val displayName = if (platform.isNotEmpty()) "${feature.properties.stopName} [$platform]" else feature.properties.stopName
            
            StationEntity(
                id = feature.properties.stopId,
                name = displayName,
                latitude = feature.geometry.coordinates[1],
                longitude = feature.geometry.coordinates[0],
                direction = platform,
                isFavorite = false
            )
        }
        stationDao.insertStations(entities)
        return entities.map { it.id }
    }

    suspend fun getDepartures(stopId: String): List<DepartureItem> {
        return try {
            val response = apiService.getDepartures(stopId)
            // Strictly filter for Trams (Type 0)
            val tramOnly = response.departures.filter { it.route.type == 0 }
            
            // Cache these results
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
                isPredicted = item.arrival.predicted != null
            )
        }
        departureDao.insertDepartures(entities)
    }

    suspend fun getCachedDepartures(stopId: String): List<DepartureItem> {
        val entities = departureDao.getDeparturesForStop(stopId)
        return entities.map { entity ->
            DepartureItem(
                route = com.example.tramapp.data.remote.RouteInfo(entity.routeShortName, entity.routeType),
                trip = com.example.tramapp.data.remote.TripInfo(entity.headsign),
                arrival = com.example.tramapp.data.remote.TimestampInfo(entity.arrivalTime, if (entity.isPredicted) entity.arrivalTime else null),
                stop = com.example.tramapp.data.remote.StopInfo(entity.stopId)
            )
        }
    }

    suspend fun toggleFavorite(stationId: String, isFavorite: Boolean) {
        stationDao.updateFavoriteStatus(stationId, isFavorite)
    }

    /**
     * Discovers which tram lines serve stations within ~750m of a given location.
     * Used to build the destination line cache for smart route detection.
     * Throttled to avoid hitting Golemio API rate limits (429).
     */
    suspend fun getLineNamesNearLocation(lat: Double, lng: Double): Set<String> {
        val lineNames = mutableSetOf<String>()
        try {
            val response = apiService.getStops("$lat,$lng", limit = 10)
            val nearbyStops = response.features
                .filter { it.properties.locationType == 0 }
                .filter { stop ->
                    val dLat = stop.geometry.coordinates[1] - lat
                    val dLng = stop.geometry.coordinates[0] - lng
                    dLat * dLat + dLng * dLng <= 0.00005  // ~750m
                }
                .take(4) // Limit to 4 stops to reduce API calls

            for (stop in nearbyStops) {
                try {
                    kotlinx.coroutines.delay(500) // Throttle to avoid 429
                    val deps = apiService.getDepartures(stop.properties.stopId, limit = 20, minutesAfter = 180)
                    for (dep in deps.departures) {
                        if (dep.route.type == 0) {
                            lineNames.add(dep.route.shortName)
                        }
                    }
                } catch (e: Exception) {
                    // Skip this stop if departures fail
                }
            }
        } catch (e: Exception) {
            // Return whatever we collected
        }
        return lineNames
    }
}
