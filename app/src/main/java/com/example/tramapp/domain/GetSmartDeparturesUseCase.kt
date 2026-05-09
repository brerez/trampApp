package com.example.tramapp.domain

import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.repository.TramRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

data class SmartDeparture(
    val item: DepartureItem,
    var isHomeBound: Boolean = false,
    var isWorkBound: Boolean = false,
    var isSchoolBound: Boolean = false
)

@Singleton
class GetSmartDeparturesUseCase @Inject constructor(
    private val repository: TramRepository
) {
    private val boundCheckMutex = Mutex()
    private val ongoingBoundChecks = mutableMapOf<String, Deferred<Boolean>>()



    suspend fun execute(
        stationId: String,
        preferences: UserPreferences?
    ): List<SmartDeparture> {
        val departures = repository.getDepartures(stationId)
        return departures.map { SmartDeparture(it) }
    }

    suspend fun checkBounds(
        departure: DepartureItem,
        stationName: String,
        preferences: UserPreferences,
        currentLat: Double,
        currentLng: Double
    ): Triple<Boolean, Boolean, Boolean> = coroutineScope {
        val stopId = departure.stop.id
        val home = async { checkBoundAtomic(departure, stationName, preferences.homeStopNames, preferences.homeStopIds, currentLat, currentLng, preferences.homeLat, preferences.homeLng, "home") }
        val work = async { checkBoundAtomic(departure, stationName, preferences.workStopNames, preferences.workStopIds, currentLat, currentLng, preferences.workLat, preferences.workLng, "work") }
        val school = async { checkBoundAtomic(departure, stationName, preferences.schoolStopNames, preferences.schoolStopIds, currentLat, currentLng, preferences.schoolLat, preferences.schoolLng, "school") }

        Triple(home.await(), work.await(), school.await())
    }

    private suspend fun checkBoundAtomic(
        item: DepartureItem,
        stationName: String,
        destinationNames: Set<String>,
        destinationIds: Set<String>,
        currentLat: Double,
        currentLng: Double,
        destLat: Double?,
        destLng: Double?,
        destType: String
    ): Boolean {
        if (destLat == null || destLng == null || (destinationNames.isEmpty() && destinationIds.isEmpty())) return false
        
        val results = FloatArray(1)
        android.location.Location.distanceBetween(currentLat, currentLng, destLat, destLng, results)
        if (results[0] < 500f) {
            return false // User is already within 500m of this destination
        }
        
        val lineName = item.route.shortName
        val headsign = item.trip.headsign
        val tripId = item.trip.tripId

        val lockKey = "$stationName|$lineName|$headsign|$destType"
        return boundCheckMutex.withLock {
            val deferred = ongoingBoundChecks[lockKey]
            if (deferred != null) return deferred.await()

            val newDeferred = CoroutineScope(Dispatchers.IO).async {
                performCheck(stationName, item.stop.id, lineName, headsign, tripId, destinationNames, destinationIds, destType)
            }
            ongoingBoundChecks[lockKey] = newDeferred
            try {
                newDeferred.await()
            } finally {
                ongoingBoundChecks.remove(lockKey)
            }
        }
    }

    private suspend fun performCheck(
        stationName: String,
        currentStopId: String,
        lineName: String,
        headsign: String,
        tripId: String?,
        destinationNames: Set<String>,
        destinationIds: Set<String>,
        destType: String
    ): Boolean {
        if (tripId == null) return false
        
        val routePairs = repository.getTripSequence(lineName, headsign, tripId)
        
        if (routePairs.isEmpty()) return false

        val routeIds = routePairs.map { it.first }
        val routeNames = routePairs.map { it.second.replace(Regex("\\s*\\[.*]$"), "").trim() }

        // Matching by Stop ID with Name Fallback
        if (currentStopId.isNotEmpty() && destinationIds.isNotEmpty()) {
            fun getBaseStopId(id: String): String = id.split("Z")[0]
            
            val currentBaseId = getBaseStopId(currentStopId)
            val currentIndex = routeIds.indexOfFirst { getBaseStopId(it) == currentBaseId }
            
            val normalizedStationName = stationName.replace(Regex("\\s*\\[.*]$"), "").trim()
            val finalCurrentIndex = if (currentIndex != -1) currentIndex else routeNames.indexOfFirst { it == normalizedStationName }
            
            if (finalCurrentIndex != -1) {
                // Check if any destination is AFTER current index
                val hasDestinationAfter = destinationIds.any { destId ->
                    val destBaseId = getBaseStopId(destId)
                    val destIndex = routeIds.indexOfFirst { getBaseStopId(it) == destBaseId }
                    destIndex > finalCurrentIndex
                }
                
                val hasDestinationByNameAfter = if (hasDestinationAfter) true else destinationNames.any { destName ->
                    val normalizedDestName = destName.replace(Regex("\\s*\\[.*]$"), "").trim()
                    val destIndex = routeNames.indexOfFirst { it == normalizedDestName }
                    destIndex > finalCurrentIndex
                }
                
                val isBound = hasDestinationAfter || hasDestinationByNameAfter
                repository.saveDirection(stationName, lineName, headsign, destType, isBound)
                return isBound
            }
        }

        repository.saveDirection(stationName, lineName, headsign, destType, false)
        return false
    }
}
