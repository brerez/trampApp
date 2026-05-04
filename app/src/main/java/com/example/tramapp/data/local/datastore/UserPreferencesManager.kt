package com.example.tramapp.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val homeLat: Double?,
    val homeLng: Double?,
    val homeAddress: String?,
    val workLat: Double?,
    val workLng: Double?,
    val workAddress: String?,
    val schoolLat: Double?,
    val schoolLng: Double?,
    val schoolAddress: String?,
    val lastLat: Double?,
    val lastLng: Double?,
    val isManualStartup: Boolean,
    val displayRadius: Int,
    val maxStations: Int,
    // Cached line names serving stations near each destination
    val homeLines: Set<String>,
    val workLines: Set<String>,
    val schoolLines: Set<String>,
    val homeLinesTimestamp: Long,
    val workLinesTimestamp: Long,
    val schoolLinesTimestamp: Long
)

@Singleton
class UserPreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        private val HOME_LAT = doublePreferencesKey("home_lat")
        private val HOME_LNG = doublePreferencesKey("home_lng")
        private val HOME_ADDRESS = stringPreferencesKey("home_address")
        private val WORK_LAT = doublePreferencesKey("work_lat")
        private val WORK_LNG = doublePreferencesKey("work_lng")
        private val WORK_ADDRESS = stringPreferencesKey("work_address")
        private val SCHOOL_LAT = doublePreferencesKey("school_lat")
        private val SCHOOL_LNG = doublePreferencesKey("school_lng")
        private val SCHOOL_ADDRESS = stringPreferencesKey("school_address")
        private val LAST_LAT = doublePreferencesKey("last_lat")
        private val LAST_LNG = doublePreferencesKey("last_lng")
        private val IS_MANUAL_STARTUP = booleanPreferencesKey("is_manual_startup")
        private val DISPLAY_RADIUS = intPreferencesKey("display_radius")
        private val MAX_STATIONS = intPreferencesKey("max_stations")
        // Cached destination lines (comma-separated short names)
        private val HOME_LINES = stringPreferencesKey("home_lines")
        private val WORK_LINES = stringPreferencesKey("work_lines")
        private val SCHOOL_LINES = stringPreferencesKey("school_lines")
        private val HOME_LINES_TS = longPreferencesKey("home_lines_ts")
        private val WORK_LINES_TS = longPreferencesKey("work_lines_ts")
        private val SCHOOL_LINES_TS = longPreferencesKey("school_lines_ts")

        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data.map { preferences ->
        UserPreferences(
            homeLat = preferences[HOME_LAT],
            homeLng = preferences[HOME_LNG],
            homeAddress = preferences[HOME_ADDRESS],
            workLat = preferences[WORK_LAT],
            workLng = preferences[WORK_LNG],
            workAddress = preferences[WORK_ADDRESS],
            schoolLat = preferences[SCHOOL_LAT],
            schoolLng = preferences[SCHOOL_LNG],
            schoolAddress = preferences[SCHOOL_ADDRESS],
            lastLat = preferences[LAST_LAT],
            lastLng = preferences[LAST_LNG],
            isManualStartup = preferences[IS_MANUAL_STARTUP] ?: false,
            displayRadius = preferences[DISPLAY_RADIUS] ?: 750,
            maxStations = preferences[MAX_STATIONS] ?: 4,
            homeLines = preferences[HOME_LINES]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            workLines = preferences[WORK_LINES]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            schoolLines = preferences[SCHOOL_LINES]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            homeLinesTimestamp = preferences[HOME_LINES_TS] ?: 0L,
            workLinesTimestamp = preferences[WORK_LINES_TS] ?: 0L,
            schoolLinesTimestamp = preferences[SCHOOL_LINES_TS] ?: 0L
        )
    }

    fun isCacheStale(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > CACHE_TTL_MS
    }

    suspend fun updateIsManualStartup(isManual: Boolean) {
        dataStore.edit { it[IS_MANUAL_STARTUP] = isManual }
    }

    suspend fun updateLastLocation(lat: Double, lng: Double) {
        dataStore.edit { it[LAST_LAT] = lat; it[LAST_LNG] = lng }
    }

    suspend fun updateHomeLocation(lat: Double, lng: Double, address: String) {
        dataStore.edit {
            it[HOME_LAT] = lat; it[HOME_LNG] = lng; it[HOME_ADDRESS] = address
            // Invalidate line cache when location changes
            it.remove(HOME_LINES); it.remove(HOME_LINES_TS)
        }
    }

    suspend fun updateWorkLocation(lat: Double, lng: Double, address: String) {
        dataStore.edit {
            it[WORK_LAT] = lat; it[WORK_LNG] = lng; it[WORK_ADDRESS] = address
            it.remove(WORK_LINES); it.remove(WORK_LINES_TS)
        }
    }

    suspend fun updateSchoolLocation(lat: Double, lng: Double, address: String) {
        dataStore.edit {
            it[SCHOOL_LAT] = lat; it[SCHOOL_LNG] = lng; it[SCHOOL_ADDRESS] = address
            it.remove(SCHOOL_LINES); it.remove(SCHOOL_LINES_TS)
        }
    }

    suspend fun updateDestinationLines(type: String, lines: Set<String>) {
        dataStore.edit { prefs ->
            val csv = lines.joinToString(",")
            val now = System.currentTimeMillis()
            when (type) {
                "home" -> { prefs[HOME_LINES] = csv; prefs[HOME_LINES_TS] = now }
                "work" -> { prefs[WORK_LINES] = csv; prefs[WORK_LINES_TS] = now }
                "school" -> { prefs[SCHOOL_LINES] = csv; prefs[SCHOOL_LINES_TS] = now }
            }
        }
    }

    suspend fun updateDisplayRadius(radiusMeters: Int) {
        dataStore.edit { it[DISPLAY_RADIUS] = radiusMeters }
    }

    suspend fun updateMaxStations(count: Int) {
        dataStore.edit { it[MAX_STATIONS] = count }
    }
}
