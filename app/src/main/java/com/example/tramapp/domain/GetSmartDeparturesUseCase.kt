package com.example.tramapp.domain

import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.repository.TramRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.*

data class SmartDeparture(
    val item: DepartureItem,
    val isHomeBound: Boolean = false,
    val isWorkBound: Boolean = false,
    val isSchoolBound: Boolean = false
)

/**
 * Determines which departures are heading toward the user's saved destinations.
 *
 * Uses a two-step check:
 * 1. **Line cache** (built from stations near each destination): is this line known to serve
 *    a station near the destination?
 * 2. **Direction check** (dot product): is the tram's terminal in roughly the same direction
 *    as the destination from the user's current location?
 *
 * The cache is populated/refreshed by [DestinationLineCacheUseCase] and stored in DataStore.
 */
class GetSmartDeparturesUseCase @Inject constructor(
    private val repository: TramRepository
) {
    // In-memory cache of headsign coordinates (populated lazily)
    private val headsignCoords = mutableMapOf<String, Pair<Double, Double>?>()

    suspend fun execute(
        stopId: String,
        currentLat: Double,
        currentLng: Double,
        preferences: UserPreferences?
    ): List<SmartDeparture> {
        var departures = repository.getDepartures(stopId)

        // Fallback to cache if real-time fails or is empty
        if (departures.isEmpty()) {
            val cached = repository.getCachedDepartures(stopId)
            val now = java.time.OffsetDateTime.now()
            departures = cached.filter { item ->
                try {
                    val arrivalTime = java.time.OffsetDateTime.parse(item.arrival.predicted ?: item.arrival.scheduled)
                    arrivalTime.isAfter(now)
                } catch (e: Exception) {
                    false
                }
            }
        }

        if (preferences == null) return departures.map { SmartDeparture(it) }

        // Pre-resolve headsign coordinates from local station DB
        val allStations = repository.allStations.first()
        for (dep in departures) {
            val hs = dep.trip.headsign
            if (!headsignCoords.containsKey(hs)) {
                val match = allStations.find { it.name.contains(hs, ignoreCase = true) }
                headsignCoords[hs] = match?.let { Pair(it.latitude, it.longitude) }
            }
        }

        return departures.map { departure ->
            val lineName = departure.route.shortName
            val terminalCoords = headsignCoords[departure.trip.headsign]

            val isHomeBound = checkBound(
                lineName, terminalCoords, preferences.homeLines,
                currentLat, currentLng, preferences.homeLat, preferences.homeLng
            )
            val isWorkBound = checkBound(
                lineName, terminalCoords, preferences.workLines,
                currentLat, currentLng, preferences.workLat, preferences.workLng
            )
            val isSchoolBound = checkBound(
                lineName, terminalCoords, preferences.schoolLines,
                currentLat, currentLng, preferences.schoolLat, preferences.schoolLng
            )

            SmartDeparture(departure, isHomeBound, isWorkBound, isSchoolBound)
        }
    }

    /**
     * Two-step check:
     * 1. Is this line in the destination's cached line set?
     * 2. Is the tram heading roughly TOWARD the destination? (dot product > 0)
     */
    private fun checkBound(
        lineName: String,
        terminalCoords: Pair<Double, Double>?,
        destinationLines: Set<String>,
        currentLat: Double, currentLng: Double,
        destLat: Double?, destLng: Double?
    ): Boolean {
        if (destLat == null || destLng == null) return false
        if (destinationLines.isEmpty()) return false
        if (!destinationLines.contains(lineName)) return false

        // If user is already within ~750m of the destination, don't mark anything
        // (you're already there — no point labeling trams as "toward home" when at home)
        val dLat = currentLat - destLat
        val dLng = currentLng - destLng
        if (dLat * dLat + dLng * dLng < 0.00005) return false  // ~750m

        // Line is in cache → now check direction
        if (terminalCoords == null) {
            // Can't verify direction without terminal coords; trust the cache
            return true
        }

        return isHeadingToward(
            currentLat, currentLng,
            terminalCoords.first, terminalCoords.second,
            destLat, destLng
        )
    }

    /**
     * Direction check using cosine similarity.
     * Returns true if the angle between (user→terminal) and (user→destination) is < ~72°.
     * Also rejects trams whose terminal is very close (< 1km), since they're about to end.
     */
    private fun isHeadingToward(
        userLat: Double, userLng: Double,
        terminalLat: Double, terminalLng: Double,
        destLat: Double, destLng: Double
    ): Boolean {
        // Scale longitude by cos(lat) so that lat/lng deltas are comparable
        val lngScale = cos(userLat * PI / 180.0)

        // Vector: user → terminal
        val dxTerm = (terminalLng - userLng) * lngScale
        val dyTerm = terminalLat - userLat

        // Skip trams about to terminate (terminal < ~1km away)
        // 1km ≈ 0.009° lat, so squared threshold ~0.00008
        val termDistSq = dxTerm * dxTerm + dyTerm * dyTerm
        if (termDistSq < 0.00008) return false

        // Vector: user → destination
        val dxDest = (destLng - userLng) * lngScale
        val dyDest = destLat - userLat

        val dot = dxDest * dxTerm + dyDest * dyTerm
        val magDest = sqrt(dxDest * dxDest + dyDest * dyDest)
        val magTerm = sqrt(dxTerm * dxTerm + dyTerm * dyTerm)

        if (magDest == 0.0 || magTerm == 0.0) return false

        val cosSim = dot / (magDest * magTerm)
        // Require cosine > 0.3 (angle < ~72°)
        return cosSim > 0.3
    }
}
