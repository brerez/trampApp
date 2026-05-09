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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TramRepository,
    private val getSmartDepartures: GetSmartDeparturesUseCase,
    private val preferencesManager: UserPreferencesManager,
    private val destinationLineCache: com.example.tramapp.domain.DestinationLineCacheUseCase,
    private val fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    private val locationStateManager: com.example.tramapp.domain.location.LocationStateManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

    val currentLocation: StateFlow<LatLng> = locationStateManager.activeLocation
    val isManualLocation: StateFlow<Boolean> = locationStateManager.isManual

    private val _currentNearbyStationIds = MutableStateFlow<Set<String>>(emptySet())

    private val _rawStationDepartures = MutableStateFlow<Map<String, List<SmartDeparture>>>(emptyMap())
    
    val favorites: StateFlow<Set<String>> = preferencesManager.userPreferences.map { it.favorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
        
    val favoritesFirst: StateFlow<Boolean> = preferencesManager.userPreferences.map { it.favoritesFirst }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val stationDepartures: StateFlow<Map<String, List<SmartDeparture>>> = combine(
        _rawStationDepartures,
        favorites,
        favoritesFirst
    ) { departures, favs, favsFirst ->
        if (!favsFirst) return@combine departures
        
        departures.mapValues { (_, deps) ->
            deps.sortedByDescending { favs.contains(it.item.route.shortName) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val nearbyStations: StateFlow<List<StationEntity>> = combine(
        repository.allStations,
        currentLocation,
        _currentNearbyStationIds,
        stationDepartures,
        preferencesManager.userPreferences
    ) { all, loc, ids, deps, prefs ->
        all.filter { station ->
            // Calculate distance approximation in meters
            val dLat = (station.latitude - loc.latitude) * 111000
            val dLng = (station.longitude - loc.longitude) * 71000
            val distSq = dLat * dLat + dLng * dLng
            val maxDistSq = prefs.displayRadius * prefs.displayRadius
            val isWithinRadius = distSq <= maxDistSq

            // Prioritize stations from the latest API call, but keep showing ones with loaded departures to avoid blank screen during refresh
            val shouldShow = if (ids.isNotEmpty()) {
                (ids.contains(station.id) || deps.containsKey(station.id)) && isWithinRadius
            } else {
                isWithinRadius
            }

            if (!shouldShow) return@filter false

            // Only show if it has trams (if we've loaded its departures)
            if (deps.containsKey(station.id)) {
                deps[station.id]?.isNotEmpty() == true
            } else {
                true // Show if not loaded yet
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = MutableStateFlow("Initializing...")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _loadingStations = MutableStateFlow<Set<String>>(emptySet())
    val loadingStations: StateFlow<Set<String>> = _loadingStations.asStateFlow()
    
    private val _selectedTripDetails = MutableStateFlow<com.example.tramapp.domain.TripDetails?>(null)
    val selectedTripDetails: StateFlow<com.example.tramapp.domain.TripDetails?> = _selectedTripDetails.asStateFlow()

    private val _showTripPopup = MutableStateFlow(false)
    val showTripPopup: StateFlow<Boolean> = _showTripPopup.asStateFlow()

    private val _isTripLoading = MutableStateFlow(false)
    val isTripLoading: StateFlow<Boolean> = _isTripLoading.asStateFlow()

    val apiQueryCount: StateFlow<Int> = repository.apiQueryCount

    val throttleMessage: StateFlow<String?> = repository.throttleUntil.flatMapLatest { until: Long ->
        flow {
            while (until > System.currentTimeMillis()) {
                val seconds = (until - System.currentTimeMillis() + 999) / 1000
                emit("API Throttled: Resuming in ${seconds}s")
                kotlinx.coroutines.delay(1000)
            }
            emit(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var tripFetchJob: kotlinx.coroutines.Job? = null
    private val stationJobs = mutableMapOf<String, Job>()
    private val enrichmentJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            // Step 1: Load cached location FIRST — before anything else runs
            val prefs = preferencesManager.userPreferences.first()
            if (prefs.lastLat != null && prefs.lastLng != null) {
                val latLng = LatLng(prefs.lastLat, prefs.lastLng)
                if (prefs.isManualStartup) {
                    locationStateManager.setUserSelectedLocation(latLng)
                } else {
                    locationStateManager.updateGpsLocation(latLng)
                }
            }

            // Step 2: Instantly show cached departures from last run (~50ms, no network needed)
            // Launched in parallel to avoid blocking network operations
            launch {
                loadCachedDepartures()
            }

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
            kotlinx.coroutines.delay(2000)
            try {
                val prefs = preferencesManager.userPreferences.first()
                destinationLineCache.refreshIfNeeded(prefs)
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Cache refresh failed", e)
            }
        }
    }

    /** Loads cached departures from Room and shows them immediately — no network call */
    private suspend fun loadCachedDepartures() {
        val loc = currentLocation.value
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
            _rawStationDepartures.value = cachedMap
            _status.value = "Showing cached data — refreshing..."
        }
    }

    /** Reacts to nearbyStations changes instead of polling every 15s */
    private fun startDepartureLoop() {
        viewModelScope.launch {
            _currentNearbyStationIds.collectLatest { stationIds ->
                if (stationIds.isEmpty()) return@collectLatest
                val loc = currentLocation.value
                val prefs = preferencesManager.userPreferences.first()

                // Sort the raw IDs by distance to find which ones to load
                val allStations = repository.allStations.first()
                val sortedStations = allStations
                    .filter { it.id in stationIds }
                    .sortedBy { station ->
                        val dLat = station.latitude - loc.latitude
                        val dLng = station.longitude - loc.longitude
                        dLat * dLat + dLng * dLng
                    }

                val groupsToLoad = selectStationsByName(sortedStations, 2) // Limit to top 2 stations
                groupsToLoad.take(6).forEach { station -> // Max 6 platforms total
                    refreshStation(station.id)
                    kotlinx.coroutines.delay(500) // Increase delay between platform loads
                }
            }
        }

        // Periodic re-trigger every 30s to keep data fresh (increased from 15s)
        viewModelScope.launch(ioDispatcher) {
            while (true) {
                kotlinx.coroutines.delay(30000)
                val loc = currentLocation.value
                val prefs = preferencesManager.userPreferences.first()
                try {
                    val ids = repository.refreshNearbyStations(
                        loc.latitude, loc.longitude,
                        prefs.displayRadius
                    )
                    if (ids.isNotEmpty()) _currentNearbyStationIds.value = ids.toSet()
                } catch (e: Exception) { 
                    android.util.Log.w("DashboardViewModel", "Failed to update nearby stations in background", e)
                }
            }
        }
    }

    private fun listenForLocationChanges() {
        // Automatically refresh when location changes (Debounced)
        viewModelScope.launch {
            currentLocation
                .debounce(2000) // Reduced from 5000 to make startup faster
                .distinctUntilChanged()
                .combine(preferencesManager.userPreferences) { loc, prefs -> loc to prefs }
                .collect { (loc, prefs) ->
                    // Load data even for the default location to avoid blank screen on clean start
                    _status.value = "Scanning [${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}]..."
                    try {
                        val ids = repository.refreshNearbyStations(
                            loc.latitude,
                            loc.longitude,
                            prefs.displayRadius
                        )
                        _currentNearbyStationIds.value = ids.toSet()
                        _status.value = "Scan complete"
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
        if (isManual) {
            locationStateManager.setUserSelectedLocation(latLng)
        } else {
            locationStateManager.updateGpsLocation(latLng)
        }
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
        
        // Try to get last location immediately for faster startup
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    updateLocation(LatLng(location.latitude, location.longitude), isManual = false)
                }
            }
        } catch (e: Exception) {
            // Ignore permission or initialization errors here
        }
        
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    val location = result.lastLocation
                    if (location != null) {
                        updateLocation(LatLng(location.latitude, location.longitude), isManual = false)
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            android.os.Looper.getMainLooper()
        )
        locationStateManager.revertToGps()
    }

    fun refreshStation(stationId: String) {
        stationJobs[stationId]?.cancel()
        stationJobs[stationId] = viewModelScope.launch {
            if (_loadingStations.value.contains(stationId)) return@launch
            _loadingStations.value += stationId
            try {
                val prefs = preferencesManager.userPreferences.first()
                val stationName = repository.allStations.first().find { it.id == stationId }?.name ?: "Unknown"

                // PRIORITY 1: Get trams immediately (non-blocking)
                val deps = getSmartDepartures.execute(stationId, prefs)
                
                val currentMap = _rawStationDepartures.value.toMutableMap()
                currentMap[stationId] = deps.take(5)
                _rawStationDepartures.value = currentMap
                
                // Show toast only if ALL platforms for this station are empty
                val allStations = repository.allStations.first()
                val currentStation = allStations.find { it.id == stationId }
                val baseName = currentStation?.name?.replace(Regex("\\s*\\[.*]$"), "")?.trim() ?: "Unknown"
                
                val siblingPlatforms = allStations.filter { 
                    it.name.replace(Regex("\\s*\\[.*]$"), "").trim() == baseName 
                }
                
                val allEmpty = siblingPlatforms.isNotEmpty() && siblingPlatforms.all { platform ->
                    val platformDeps = currentMap[platform.id]
                    platformDeps != null && platformDeps.isEmpty()
                }
                
                if (allEmpty) {
                    android.widget.Toast.makeText(context, "Station $baseName has no trams, removing from list", android.widget.Toast.LENGTH_SHORT).show()
                }

                // 3. Enrich with directional info (async, serial to avoid API limits)
                enrichStation(stationId, stationName, deps.take(5), prefs)
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Failed to refresh station $stationId", e)
            } finally {
                _loadingStations.value -= stationId
                stationJobs.remove(stationId)
            }
        }
    }

    private fun enrichStation(stationId: String, stationName: String, departures: List<SmartDeparture>, prefs: com.example.tramapp.data.local.datastore.UserPreferences) {
        enrichmentJobs[stationId]?.cancel()
        enrichmentJobs[stationId] = viewModelScope.launch {
            val loc = currentLocation.value ?: return@launch
            val updatedDeps = departures.toMutableList()
            var anyChanged = false
            for (i in updatedDeps.indices) {
                ensureActive()
                val smartDep = updatedDeps[i]
                try {
                    val bounds = getSmartDepartures.checkBounds(smartDep.item, stationName, prefs, loc.latitude, loc.longitude)
                    if (bounds.first != smartDep.isHomeBound || bounds.second != smartDep.isWorkBound || bounds.third != smartDep.isSchoolBound) {
                        updatedDeps[i] = smartDep.copy(
                            isHomeBound = bounds.first,
                            isWorkBound = bounds.second,
                            isSchoolBound = bounds.third
                        )
                        anyChanged = true
                    }
                    // Small delay between trams
                    kotlinx.coroutines.delay(150)
                } catch (e: Exception) { 
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w("DashboardViewModel", "Failed to check bounds for departure", e)
                }
            }

            if (anyChanged) {
                val currentMap = _rawStationDepartures.value.toMutableMap()
                currentMap[stationId] = updatedDeps
                _rawStationDepartures.value = currentMap
            }
            enrichmentJobs.remove(stationId)
        }
    }

    fun refreshStationGroup(platformIds: List<String>) {
        platformIds.forEach { refreshStation(it) }
    }

    fun selectTram(tripId: String, routeName: String, destination: String) {
        _showTripPopup.value = true
        _isTripLoading.value = true
        _selectedTripDetails.value = null
        
        tripFetchJob?.cancel()
        tripFetchJob = viewModelScope.launch {
            try {
                repository.getTripDetailsFlow(tripId, routeName, destination).collect { details ->
                    _selectedTripDetails.value = details
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isTripLoading.value = false
            }
        }
    }

    fun dismissTripPopup() {
        _showTripPopup.value = false
        tripFetchJob?.cancel()
        _isTripLoading.value = false
        // We keep _selectedTripDetails for a smooth exit animation if needed, 
        // or clear it immediately. Let's clear it to be safe.
        _selectedTripDetails.value = null
    }

    fun toggleFavorite(line: String) {
        viewModelScope.launch {
            preferencesManager.toggleFavorite(line)
        }
    }

    fun updateFavoritesFirst(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updateFavoritesFirst(enabled)
        }
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
