package com.example.tramapp.domain

import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.local.datastore.UserPreferencesManager
import com.example.tramapp.data.repository.TramRepository
import javax.inject.Inject

/**
 * Builds/refreshes the cached set of stop names that serve stations
 * near each saved destination.
 */
class DestinationLineCacheUseCase @Inject constructor(
    private val repository: TramRepository,
    private val preferencesManager: UserPreferencesManager
) {
    suspend fun refreshIfNeeded(preferences: UserPreferences) {
        // Home
        if (preferences.homeLat != null && preferences.homeLng != null) {
            if (preferences.homeStopNames.isEmpty() || preferences.homeStopIds.isEmpty() || preferencesManager.isCacheStale(preferences.homeLinesTimestamp)) {
                val info = repository.getNearbyInfo(preferences.homeLat, preferences.homeLng)
                if (info.stopNames.isNotEmpty()) {
                    preferencesManager.updateDestinationData("home", emptySet(), info.stopNames, info.stopIds)
                    kotlinx.coroutines.delay(1000)
                }
            }
        }

        // Work
        if (preferences.workLat != null && preferences.workLng != null) {
            if (preferences.workStopNames.isEmpty() || preferences.workStopIds.isEmpty() || preferencesManager.isCacheStale(preferences.workLinesTimestamp)) {
                val info = repository.getNearbyInfo(preferences.workLat, preferences.workLng)
                if (info.stopNames.isNotEmpty()) {
                    preferencesManager.updateDestinationData("work", emptySet(), info.stopNames, info.stopIds)
                    kotlinx.coroutines.delay(1000)
                }
            }
        }

        // School
        if (preferences.schoolLat != null && preferences.schoolLng != null) {
            if (preferences.schoolStopNames.isEmpty() || preferences.schoolStopIds.isEmpty() || preferencesManager.isCacheStale(preferences.schoolLinesTimestamp)) {
                val info = repository.getNearbyInfo(preferences.schoolLat, preferences.schoolLng)
                if (info.stopNames.isNotEmpty()) {
                    preferencesManager.updateDestinationData("school", emptySet(), info.stopNames, info.stopIds)
                }
            }
        }
    }
}
