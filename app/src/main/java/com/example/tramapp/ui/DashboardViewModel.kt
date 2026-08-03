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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Test seam: gates the unbounded `while (autoRefreshEnabled) { delay(...); ... }`
     * background loops started below/in [init]. Production default is `true`. Unit tests
     * MUST set this to `false` immediately after construction (before the first dispatcher
     * advance) — otherwise these intentionally-infinite loops keep the shared
     * `TestCoroutineScheduler` non-idle forever, and `runTest`'s implicit final
     * advance-to-idle check hangs. Checked at the top of each loop iteration so an in-flight
     * `delay` still completes cleanly but the loop exits before scheduling the next one.
     */
    var autoRefreshEnabled: Boolean = true

    val currentLocation: StateFlow<LatLng> = locationStateManager.activeLocation
    val isManualLocation: StateFlow<Boolean> = locationStateManager.isManual

    private val _currentNearbyStationIds = MutableStateFlow<Set<String>>(emptySet())

    private val _rawStationDepartures = MutableStateFlow<Map<String, List<SmartDeparture>>>(emptyMap())

    val favorites: StateFlow<Set<String>> = preferencesManager.userPreferences.map { it.favorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favoritesFirst: StateFlow<Boolean> = preferencesManager.userPreferences.map { it.favoritesFirst }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentTime: StateFlow<java.time.OffsetDateTime> = flow {
        while (autoRefreshEnabled) {
            emit(java.time.OffsetDateTime.now())
            kotlinx.coroutines.delay(10000) // Tick every 10 seconds
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), java.time.OffsetDateTime.now())

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

    private val _visibleStationCount = MutableStateFlow(3)
    val visibleStationCount: StateFlow<Int> = _visibleStationCount.asStateFlow()

    val visibleStations: StateFlow<List<StationEntity>> = combine(
        repository.allStations,
        currentLocation,
        _currentNearbyStationIds,
        visibleStationCount,
        favorites,
        favoritesFirst,
        preferencesManager.userPreferences
    ) { arr: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        val all = arr[0] as List<StationEntity>
        val loc = arr[1] as LatLng
        val ids = arr[2] as Set<String>
        val count = arr[3] as Int
        val favs = arr[4] as Set<String>
        val favsFirst = arr[5] as Boolean
        val prefs = arr[6] as com.example.tramapp.data.local.datastore.UserPreferences

        val maxDist = prefs.displayRadius.toFloat()
        val nearbyIds = if (ids.isNotEmpty()) {
            ids
        } else {
            all.filter {
                val dLat = (it.latitude - loc.latitude) * 111000.0
                val dLng = (it.longitude - loc.longitude) * 71000.0
                (dLat * dLat + dLng * dLng) <= (maxDist * maxDist).toDouble()
            }.map { it.id }.toSet()
        }

        val sorted = all.filter { it.id in nearbyIds }
            .sortedWith(Comparator { a, b ->
                val aFav = if (favsFirst) favs.contains(a.name) else false
                val bFav = if (favsFirst) favs.contains(b.name) else false
                if (aFav != bFav) return@Comparator if (aFav) -1 else 1
                val aDist = (a.latitude - loc.latitude).let { it * it } +
                            (a.longitude - loc.longitude).let { it * it }
                val bDist = (b.latitude - loc.latitude).let { it * it } +
                            (b.longitude - loc.longitude).let { it * it }
                aDist.compareTo(bDist)
            })
        // `count` is a number of distinct physical stations (matches the "Load N more
        // stations" affordance and the header's station count), not raw platform rows — a
        // plain take(count) on ungrouped platforms could burn the whole cap on 2-3 platforms
        // of a single station, silently hiding other nearby stations entirely.
        selectStationsByName(sorted, count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasMoreStations: StateFlow<Boolean> = combine(
        repository.allStations,
        currentLocation,
        _currentNearbyStationIds,
        visibleStationCount
    ) { all, loc, ids, count ->
        val maxDist = 2000f
        val nearbyIds = if (ids.isNotEmpty()) ids else {
            all.filter {
                val dLat = (it.latitude - loc.latitude) * 111000
                val dLng = (it.longitude - loc.longitude) * 71000
                (dLat * dLat + dLng * dLng) <= (maxDist * maxDist)
            }.mapTo(mutableSetOf()) { it.id }
        }
        val uniqueNames = all.filter { it.id in nearbyIds }
            .map { it.name.replace(Regex("\\s*\\[.*]$"), "").trim() }
            .toSet()
        count < uniqueNames.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val nearbyStations: StateFlow<List<StationEntity>> = visibleStations

    private val _status = MutableStateFlow("Initializing...")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _loadingStations = MutableStateFlow<Set<String>>(emptySet())
    val loadingStations: StateFlow<Set<String>> = _loadingStations.asStateFlow()

    // U4 (R1-R4, R24): explicit per-platform-id UI state, keyed by StationEntity.id.
    private val _stationStates = MutableStateFlow<Map<String, StationUiState>>(emptyMap())
    val stationStates: StateFlow<Map<String, StationUiState>> = _stationStates.asStateFlow()

    /**
     * Derived, ordered station groups (platforms sharing a base name). A group is promoted
     * to [StationRow.isReady] once any platform is [StationUiState.Ready] with departures;
     * it then holds a stable position (partitioned, not re-sorted) even while sibling
     * platforms are still resolving or re-fetching. Fully-[StationUiState.Empty] groups are
     * excluded. Order preserves [visibleStations]' favorites-first/distance sort as a stable
     * pre-sort key (KTD2).
     */
    val visibleStationRows: StateFlow<List<StationRow>> = combine(
        visibleStations,
        _stationStates,
        currentLocation,
        favorites,
        favoritesFirst
    ) { stations, states, loc, favs, favsFirst ->
        if (stations.isEmpty()) return@combine emptyList()

        val stationById = stations.associateBy { it.id }
        val groupPlatformIds = LinkedHashMap<String, MutableList<String>>()
        for (station in stations) {
            val baseName = station.name.replace(Regex("\\s*\\[.*]$"), "").trim()
            groupPlatformIds.getOrPut(baseName) { mutableListOf() }.add(station.id)
        }

        val readyGroups = mutableListOf<StationRow>()
        val loadingGroups = mutableListOf<StationRow>()

        for ((baseName, platformIds) in groupPlatformIds) {
            val platformDepartures = mutableListOf<Pair<String, List<SmartDeparture>>>()
            var anyReadyWithDeps = false
            var anyUnresolved = false

            for (platformId in platformIds) {
                val label = Regex("\\[(.*)]$").find(stationById[platformId]?.name ?: "")
                    ?.groupValues?.get(1) ?: platformId
                when (val state = states[platformId]) {
                    is StationUiState.Ready -> {
                        if (state.departures.isNotEmpty()) anyReadyWithDeps = true
                        // U6 (R5/R6): favorites are a pure overlay — a stable sort of already-
                        // Ready lines within this platform, never gating/reordering the row itself.
                        val departures = if (favsFirst) {
                            state.departures.sortedByDescending { favs.contains(it.item.route.shortName) }
                        } else {
                            state.departures
                        }
                        platformDepartures.add(label to departures)
                    }
                    StationUiState.Loading, null -> {
                        anyUnresolved = true
                        platformDepartures.add(label to emptyList())
                    }
                    StationUiState.Empty -> {
                        platformDepartures.add(label to emptyList())
                    }
                }
            }

            val distanceSq = platformIds.mapNotNull { stationById[it] }.minOfOrNull { station ->
                val dLat = station.latitude - loc.latitude
                val dLng = station.longitude - loc.longitude
                dLat * dLat + dLng * dLng
            } ?: Double.MAX_VALUE

            val row = StationRow(
                baseName = baseName,
                platformIds = platformIds.toList(),
                platformDepartures = platformDepartures,
                isReady = anyReadyWithDeps,
                distanceSq = distanceSq
            )

            when {
                anyReadyWithDeps -> readyGroups.add(row)
                anyUnresolved -> loadingGroups.add(row)
                // else: every platform settled Empty -> collapsed out entirely
            }
        }

        readyGroups + loadingGroups
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
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

    private val _lastUpdateTime = MutableStateFlow(System.currentTimeMillis())

    // U10 (R10, R11): a real AppStatus fed to the header's CompactStatusIndicator, replacing
    // the "Debug: N API calls" string. LOADING while any station is fetching, ERROR while
    // throttled, ONLINE otherwise. lastUpdateTime is set on each successful refreshStation.
    val appStatus: StateFlow<com.example.tramapp.ui.components.AppStatus> = combine(
        loadingStations,
        throttleMessage,
        _lastUpdateTime
    ) { loading, throttle, lastUpdate ->
        val connection = when {
            loading.isNotEmpty() -> com.example.tramapp.ui.components.ConnectionStatus.LOADING
            else -> com.example.tramapp.ui.components.ConnectionStatus.ONLINE
        }
        com.example.tramapp.ui.components.AppStatus(connection = connection, lastUpdateTime = lastUpdate)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.tramapp.ui.components.AppStatus()
    )

    private val _loadMoreChannel = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var tripFetchJob: kotlinx.coroutines.Job? = null
    private val stationJobs = mutableMapOf<String, Job>()
    private val enrichmentJobs = mutableMapOf<String, Job>()
    private var visibleStationsFetchJob: Job? = null
    private var manualRefreshInFlight = false
    private val enrichmentMutex = kotlinx.coroutines.sync.Mutex()

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

        // Step 6: Fetch departures for newly visible stations (progressive reveal)
        visibleStationsFetchJob = viewModelScope.launch {
            _loadMoreChannel.collect {
                val newStations = visibleStations.value
                val currentCount = _visibleStationCount.value
                val previouslyVisible = newStations.take(currentCount - 3)
                val newlyRevealed = newStations.drop(previouslyVisible.size).take(3)
                for (station in newlyRevealed) {
                    if (!_rawStationDepartures.value.containsKey(station.id)) {
                        refreshStation(station.id)
                    }
                }
            }
        }

        // Step 7: Periodic departures refresh every 60s
        viewModelScope.launch {
            while (autoRefreshEnabled) {
                kotlinx.coroutines.delay(60000)
                if (!autoRefreshEnabled) break
                val visible = visibleStations.value
                for (station in visible) {
                    refreshStation(station.id)
                    kotlinx.coroutines.delay(300) // Small delay between platform loads to avoid hitting API limit at once
                }
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
                if (stationIds.isEmpty() || manualRefreshInFlight) return@collectLatest
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

                // Must cover every currently-visible station, not a fixed subset — anything
                // left out here has no other prompt fetch path and would otherwise sit as a
                // spinning skeleton until the 60s periodic sweep (init step 7) gets to it.
                val groupsToLoad = selectStationsByName(sortedStations, _visibleStationCount.value)
                groupsToLoad.forEach { station ->
                    refreshStation(station.id)
                    kotlinx.coroutines.delay(500) // Increase delay between platform loads
                }
            }
        }

        // Periodic re-trigger every 30s to keep data fresh (increased from 15s)
        viewModelScope.launch(ioDispatcher) {
            while (autoRefreshEnabled) {
                kotlinx.coroutines.delay(30000)
                if (!autoRefreshEnabled) break
                val loc = currentLocation.value
                val prefs = preferencesManager.userPreferences.first()
                try {
                    val ids = repository.refreshNearbyStations(
                        loc.latitude, loc.longitude,
                        prefs.displayRadius
                    )
                    // Union, never replace: refreshNearbyStations' own cache-freshness check
                    // uses a much tighter radius than a full live scan, so a routine periodic
                    // rescan at an unchanged location can legitimately return a far smaller
                    // "still fresh" subset than what's already known — replacing the set with
                    // that subset silently dropped already-valid, currently-displayed stations
                    // out of view every ~30s.
                    if (ids.isNotEmpty()) _currentNearbyStationIds.value = _currentNearbyStationIds.value + ids.toSet()
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
            // U7 (R13): refreshNow() already comprehensively refreshes every visible station
            // itself (nearest-first, below). Suppress startDepartureLoop()'s reactive
            // bootstrap refresh — which also fires off the _currentNearbyStationIds write two
            // lines down — so the same platform isn't fetched twice in one manual refresh.
            manualRefreshInFlight = true
            try {
                val ids = repository.refreshNearbyStations(loc.latitude, loc.longitude, 1500)
                // Union, not replace — see the periodic-rescan comment in startDepartureLoop():
                // a manual refresh must never make already-visible stations disappear because
                // this call's cache-freshness check happened to return a narrower set.
                if (ids.isNotEmpty()) _currentNearbyStationIds.value = _currentNearbyStationIds.value + ids.toSet()

                // Refresh all currently visible stations' departures immediately, nearest-first
                // ([visibleStations] is itself distance-sorted).
                val visible = visibleStations.value
                for (station in visible) {
                    refreshStation(station.id)
                    kotlinx.coroutines.delay(300)
                }

                _status.value = "Updated"
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            } finally {
                manualRefreshInFlight = false
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
        // U7 (R13): reserve the loading slot synchronously, before scheduling the coroutine.
        // Two same-tick callers (e.g. refreshNow()'s explicit loop and startDepartureLoop()'s
        // reactive re-trigger, both fired off one _currentNearbyStationIds update) would
        // otherwise both pass this guard before either sets it, doubling the API call.
        if (_loadingStations.value.contains(stationId)) return
        _loadingStations.value += stationId
        // Only demote to Loading (which renders a bare skeleton, replacing the whole card) if
        // there's nothing to show yet. A periodic background refresh of an already-Ready
        // station must not blow away its visible departures for the ~1-2s round trip — the UI
        // already surfaces "refetching" via `loadingStations`/`isRefetching` without hiding the
        // existing data. Without this guard, every routine 30-60s auto-refresh briefly wiped
        // every visible station back to a skeleton, collapsing the whole list.
        if (_stationStates.value[stationId] !is StationUiState.Ready) {
            _stationStates.value = _stationStates.value + (stationId to StationUiState.Loading)
        }

        stationJobs[stationId]?.cancel()
        stationJobs[stationId] = viewModelScope.launch {
            try {
                val prefs = preferencesManager.userPreferences.first()
                val stationName = repository.allStations.first().find { it.id == stationId }?.name ?: "Unknown"

                // PRIORITY 1: Get trams immediately (non-blocking)
                val deps = getSmartDepartures.execute(stationId, prefs)
                val futureTrams = filterFutureTrams(deps).take(5)

                val currentMap = _rawStationDepartures.value.toMutableMap()
                currentMap[stationId] = futureTrams
                _rawStationDepartures.value = currentMap

                _stationStates.value = _stationStates.value + (stationId to
                    if (futureTrams.isNotEmpty()) StationUiState.Ready(futureTrams) else StationUiState.Empty)
                _lastUpdateTime.value = System.currentTimeMillis()

                // 3. Enrich with directional info (async, serial to avoid API limits)
                enrichStation(stationId, stationName, futureTrams, prefs)
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Failed to refresh station $stationId", e)
            } finally {
                _loadingStations.value -= stationId
                stationJobs.remove(stationId)
            }
        }
    }

    /** Future, in-service trams only (R1-R3): mirrors [loadCachedDepartures]'s filter. */
    private fun filterFutureTrams(deps: List<SmartDeparture>): List<SmartDeparture> {
        val now = java.time.OffsetDateTime.now()
        return deps.filter { dep ->
            try {
                val t = java.time.OffsetDateTime.parse(dep.item.arrival.predicted ?: dep.item.arrival.scheduled)
                t.isAfter(now) && dep.item.route.type == 0
            } catch (e: Exception) { false }
        }
    }

    private fun enrichStation(stationId: String, stationName: String, departures: List<SmartDeparture>, prefs: com.example.tramapp.data.local.datastore.UserPreferences) {
        enrichmentJobs[stationId]?.cancel()
        enrichmentJobs[stationId] = viewModelScope.launch {
            val loc = currentLocation.value ?: return@launch
            enrichmentMutex.withLock {
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

                    // Patch the Ready payload in place; never demotes/reorders (KTD3).
                    if (_stationStates.value[stationId] is StationUiState.Ready) {
                        _stationStates.value = _stationStates.value + (stationId to StationUiState.Ready(updatedDeps))
                    }
                }
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

    fun loadMoreStations() {
        viewModelScope.launch {
            val currentCount = _visibleStationCount.value
            _visibleStationCount.value = currentCount + 3
        }
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
