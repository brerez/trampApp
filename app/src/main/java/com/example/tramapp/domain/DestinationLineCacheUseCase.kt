package com.example.tramapp.domain

import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.local.datastore.UserPreferencesManager
import com.example.tramapp.data.repository.TramRepository
import javax.inject.Inject

/**
 * Builds/refreshes the cached set of tram line names that serve stations
 * near each saved destination (home/work/school).
 *
 * The cache is stored in DataStore and invalidated when:
 * - The destination location is changed by the user
 * - The cache is older than 7 days
 *
 * This should be called from the ViewModel on startup (in background).
 * It does NOT block the departure display.
 */
class DestinationLineCacheUseCase @Inject constructor(
    private val repository: TramRepository,
    private val preferencesManager: UserPreferencesManager
) {
    suspend fun refreshIfNeeded(preferences: UserPreferences) {
        // Home
        if (preferences.homeLat != null && preferences.homeLng != null) {
            if (preferences.homeLines.isEmpty() || preferencesManager.isCacheStale(preferences.homeLinesTimestamp)) {
                val lines = repository.getLineNamesNearLocation(preferences.homeLat, preferences.homeLng)
                if (lines.isNotEmpty()) {
                    preferencesManager.updateDestinationLines("home", lines)
                }
                kotlinx.coroutines.delay(2000) // Spread API load
            }
        }

        // Work
        if (preferences.workLat != null && preferences.workLng != null) {
            if (preferences.workLines.isEmpty() || preferencesManager.isCacheStale(preferences.workLinesTimestamp)) {
                val lines = repository.getLineNamesNearLocation(preferences.workLat, preferences.workLng)
                if (lines.isNotEmpty()) {
                    preferencesManager.updateDestinationLines("work", lines)
                }
                kotlinx.coroutines.delay(2000)
            }
        }

        // School
        if (preferences.schoolLat != null && preferences.schoolLng != null) {
            if (preferences.schoolLines.isEmpty() || preferencesManager.isCacheStale(preferences.schoolLinesTimestamp)) {
                val lines = repository.getLineNamesNearLocation(preferences.schoolLat, preferences.schoolLng)
                if (lines.isNotEmpty()) {
                    preferencesManager.updateDestinationLines("school", lines)
                }
            }
        }
    }
}
