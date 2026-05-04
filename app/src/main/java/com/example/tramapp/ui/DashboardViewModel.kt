package com.example.tramapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tramapp.data.local.datastore.UserPreferencesManager
import com.example.tramapp.data.local.entity.StationEntity
import com.example.tramapp.data.repository.TramRepository
import com.example.tramapp.domain.GetSmartDeparturesUseCase
import com.example.tramapp.domain.SmartDeparture
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TramRepository,
    private val getSmartDepartures: GetSmartDeparturesUseCase,
    private val preferencesManager: UserPreferencesManager,
    private val destinationLineCache: com.example.tramapp.domain.DestinationLineCacheUseCase,
    private val fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
) : ViewModel() {

    private val _currentLocation = MutableStateFlow(LatLng(50.0755, 14.4378))
    val currentLocation: StateFlow<LatLng> = _currentLocation.asStateFlow()

    private val _isManualLocation = MutableStateFlow(false)
    val isManualLocation: StateFlow<Boolean> = _isManualLocation.asStateFlow()

    private val _currentNearbyStationIds = MutableStateFlow<Set<String>>(emptySet())

    val nearbyStations: StateFlow<List<StationEntity>> = combine(
        repository.allStations,
        currentLocation,
        _currentNearbyStationIds
    ) { all, loc, ids ->
        all.filter { station ->
            // Prioritize stations from the latest API call
            if (ids.isNotEmpty()) {
                ids.contains(station.id)
            } else {
                // Fallback to simple distance check (< 2km)
                val dLat = station.latitude - loc.latitude
                val dLng = station.longitude - loc.longitude
                (dLat * dLat + dLng * dLng) < 0.0004 // Approx 2km squared
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = MutableStateFlow("Initializing...")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _stationDepartures = MutableStateFlow<Map<String, List<SmartDeparture>>>(emptyMap())
    val stationDepartures: StateFlow<Map<String, List<SmartDeparture>>> = _stationDepartures.asStateFlow()

    private val _loadingStations = MutableStateFlow<Set<String>>(emptySet())
    val loadingStations: StateFlow<Set<String>> = _loadingStations.asStateFlow()

    init {
        viewModelScope.launch {
            // Step 1: Load cached location FIRST — before anything else runs
            val prefs = preferencesManager.userPreferences.first()
            if (prefs.lastLat != null && prefs.lastLng != null) {
                _currentLocation.value = LatLng(prefs.lastLat, prefs.lastLng)
                _isManualLocation.value = prefs.isManualStartup
            }

            // Step 2: Instantly show cached departures from last run (~50ms, no network needed)
            loadCachedDepartures()

            // Step 3: Kick off GPS only if not in manual mode
            if (!prefs.isManualStartup) {
                revertToGps()
            }

            // Step 4: Start reactive departure loop and debounced location listener
            startDepartureLoop()
            listenForLocationChanges()
        }

        // Step 5: Build/refresh destination line caches in background (non-blocking)
        // Delayed to let initial departure loading finish first and avoid API rate limits.
        viewModelScope.launch {
            kotlinx.coroutines.delay(10000) // Wait 10s for departures to load first
            try {
                val prefs = preferencesManager.userPreferences.first()
                destinationLineCache.refreshIfNeeded(prefs)
            } catch (e: Exception) {
                // Cache refresh failure is not critical
            }
        }
    }

    /** Loads cached departures from Room and shows them immediately — no network call */
    private suspend fun loadCachedDepartures() {
        val loc = _currentLocation.value
        val now = java.time.OffsetDateTime.now()

        val stationsSnapshot = repository.allStations.first()
        val nearbySnapshot = stationsSnapshot
            .filter { station ->
                val dLat = station.latitude - loc.latitude
                val dLng = station.longitude - loc.longitude
                (dLat * dLat + dLng * dLng) < 0.0004 // ~2km
            }
            .sortedBy { station ->
                val dLat = station.latitude - loc.latitude
                val dLng = station.longitude - loc.longitude
                dLat * dLat + dLng * dLng
            }

        val prefs = preferencesManager.userPreferences.first()
        val selectedStations = selectStationsByName(nearbySnapshot, prefs.maxStations)
        val cachedMap = mutableMapOf<String, List<SmartDeparture>>()
        for (station in selectedStations) {
            val cached = repository.getCachedDepartures(station.id)
            val futureTrams = cached.filter { item ->
                try {
                    val t = java.time.OffsetDateTime.parse(item.arrival.predicted ?: item.arrival.scheduled)
                    t.isAfter(now) && item.route.type == 0
                } catch (e: Exception) { false }
            }.take(5)
            if (futureTrams.isNotEmpty()) {
                cachedMap[station.id] = futureTrams.map { SmartDeparture(it) }
            }
        }

        if (cachedMap.isNotEmpty()) {
            _stationDepartures.value = cachedMap
            _status.value = "Showing cached data — refreshing..."
        }
    }

    /** Reacts to nearbyStations changes instead of polling every 15s */
    private fun startDepartureLoop() {
        // Reactive: fires immediately whenever the station list changes
        viewModelScope.launch {
            nearbyStations.collect { stations ->
                if (stations.isEmpty()) return@collect
                val loc = currentLocation.value
                val prefs = preferencesManager.userPreferences.first()

                val sortedStations = stations.sortedBy { station ->
                    val dLat = station.latitude - loc.latitude
                    val dLng = station.longitude - loc.longitude
                    dLat * dLat + dLng * dLng
                }

                // Only preload the first X groups (maxStations)
                val groupsToLoad = selectStationsByName(sortedStations, prefs.maxStations)
                groupsToLoad.forEach { station ->
                    refreshStation(station.id)
                    kotlinx.coroutines.delay(100)
                }
            }
        }

        // Periodic re-trigger every 15s to keep data fresh
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15000)
                val loc = currentLocation.value
                val prefs = preferencesManager.userPreferences.first()
                try {
                    val ids = repository.refreshNearbyStations(
                        loc.latitude, loc.longitude,
                        prefs.displayRadius.coerceAtLeast(1500)
                    )
                    if (ids.isNotEmpty()) _currentNearbyStationIds.value = ids.toSet()
                } catch (e: Exception) { /* ignore */ }
            }
        }
    }

    private fun listenForLocationChanges() {
        // Automatically refresh when location changes (Debounced)
        viewModelScope.launch {
            currentLocation
                .debounce(1500)
                .distinctUntilChanged()
                .combine(preferencesManager.userPreferences) { loc, prefs -> loc to prefs }
                .collect { (loc, prefs) ->
                    // Skip the hardcoded fallback default (Náměstí Míru) unless it is genuinely the cached location
                    val isUncachedDefault = loc.latitude == 50.0755 && loc.longitude == 14.4378
                            && !_isManualLocation.value && prefs.lastLat == null
                    if (isUncachedDefault) return@collect

                    _status.value = "Scanning [${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}]..."
                    try {
                        val ids = repository.refreshNearbyStations(
                            loc.latitude,
                            loc.longitude,
                            prefs.displayRadius.coerceAtLeast(1500)
                        )
                        _currentNearbyStationIds.value = ids.toSet()
                        _status.value = "Found ${ids.size} stations"
                    } catch (e: Exception) {
                        _status.value = "Error: ${e.message}"
                    }
                }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            val loc = currentLocation.value
            _status.value = "Refreshing..."
            try {
                val ids = repository.refreshNearbyStations(loc.latitude, loc.longitude, 1500)
                _currentNearbyStationIds.value = ids.toSet()
                _status.value = "Updated"
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            }
        }
    }

    fun updateLocation(latLng: LatLng, isManual: Boolean = true) {
        _currentLocation.value = latLng
        _isManualLocation.value = isManual
        // Cache this location and preference
        viewModelScope.launch {
            preferencesManager.updateLastLocation(latLng.latitude, latLng.longitude)
            if (isManual) {
                preferencesManager.updateIsManualStartup(true)
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun revertToGps() {
        // Reset manual startup flag when user explicitly wants GPS
        viewModelScope.launch {
            preferencesManager.updateIsManualStartup(false)
        }
        
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    result.lastLocation?.let {
                        updateLocation(LatLng(it.latitude, it.longitude), isManual = false)
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            android.os.Looper.getMainLooper()
        )
        _isManualLocation.value = false
    }

    fun refreshStation(stationId: String) {
        viewModelScope.launch {
            if (_loadingStations.value.contains(stationId)) return@launch
            _loadingStations.value += stationId
            try {
                val loc = currentLocation.value
                val prefs = preferencesManager.userPreferences.first()
                val deps = getSmartDepartures.execute(stationId, loc.latitude, loc.longitude, prefs)
                val currentMap = _stationDepartures.value.toMutableMap()
                if (deps.isNotEmpty()) {
                    currentMap[stationId] = deps.take(5)
                }
                _stationDepartures.value = currentMap
            } catch (e: Exception) {
                // Silently fail
            } finally {
                _loadingStations.value -= stationId
            }
        }
    }

    fun refreshStationGroup(platformIds: List<String>) {
        platformIds.forEach { refreshStation(it) }
    }

    /**
     * Groups stations by their base name (stripping platform suffix like [A], [B])
     * and returns all platforms for the closest [maxNames] unique station names.
     * E.g., maxNames=2 with "Kamenická [A]", "Kamenická [B]", "Strossmayerovo nám. [A]"
     * returns all 3 because that's 2 unique station names.
     */
    private fun selectStationsByName(
        sortedStations: List<StationEntity>,
        maxNames: Int
    ): List<StationEntity> {
        val seenNames = mutableSetOf<String>()
        val selected = mutableListOf<StationEntity>()
        for (station in sortedStations) {
            val baseName = station.name.replace(Regex("\\s*\\[.*]$"), "").trim()
            if (baseName !in seenNames) {
                if (seenNames.size >= maxNames) break
                seenNames.add(baseName)
            }
            selected.add(station)
        }
        return selected
    }
}
