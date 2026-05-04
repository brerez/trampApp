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

        // Pre-resolve headsign coordinates from local station DB or hardcoded map
        val allStations = repository.allStations.first()
        val hardcodedTerminals = mapOf(
            "Starý Hloubětín" to Pair(50.1054, 14.5290),
            "Nádraží Hostivař" to Pair(50.0560, 14.5450),
            "Spojovací" to Pair(50.0920, 14.4920),
            "Lehovec" to Pair(50.1060, 14.5470),
            "Bílá Hora" to Pair(50.0760, 14.3250),
            "Dědina" to Pair(50.0900, 14.3000),
            "Sídliště Řepy" to Pair(50.0630, 14.3010),
            "Nádraží Podbaba" to Pair(50.1110, 14.3940),
            "Vozovna Kobylisy" to Pair(50.1380, 14.4600),
            "Sídliště Ďáblice" to Pair(50.1360, 14.4750),
            "Sídliště Barrandov" to Pair(50.0330, 14.3750),
            "Nádraží Braník" to Pair(50.0380, 14.4060),
            "Levského" to Pair(50.0070, 14.4370),
            "Ústřední dílny DP" to Pair(50.0730, 14.5370),
            "Černokostelecká" to Pair(50.0760, 14.4990),
            "Želivského" to Pair(50.0790, 14.4740),
            "Olšanské hřbitovy" to Pair(50.0800, 14.4630),
            "Kotlářka" to Pair(50.0710, 14.3680),
            "Sídliště Petřiny" to Pair(50.0860, 14.3410),
            "Divoká Šárka" to Pair(50.0950, 14.3240),
            "Výstaviště" to Pair(50.1060, 14.4320),
            "Vozovna Pankrác" to Pair(50.0570, 14.4400),
            "Spořilov" to Pair(50.0460, 14.4760),
            "Radlická" to Pair(50.0580, 14.3880),
            "Smíchovské nádraží" to Pair(50.0610, 14.4080),
            "Sídliště Modřany" to Pair(50.0050, 14.4320),
            "Kobylisy" to Pair(50.1240, 14.4530),
            "Březiněveská" to Pair(50.1270, 14.4560),
            "Slivenec" to Pair(50.0170, 14.3440),
            "Zličín" to Pair(50.0540, 14.2880)
        )

        for (dep in departures) {
            val hs = dep.trip.headsign
            if (!headsignCoords.containsKey(hs)) {
                // Check hardcoded first
                var coords = hardcodedTerminals.entries.find { it.key.equals(hs, ignoreCase = true) }?.value
                if (coords == null) {
                    // Fallback to local DB
                    val match = allStations.find { it.name.contains(hs, ignoreCase = true) }
                    coords = match?.let { Pair(it.latitude, it.longitude) }
                }
                headsignCoords[hs] = coords
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
            // Can't verify direction without terminal coords; it's safer to not mark it
            // than to mark both directions incorrectly.
            return false
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
