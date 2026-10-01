package com.example.tramapp.ui

import android.annotation.SuppressLint
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.local.datastore.UserPreferencesManager
import com.example.tramapp.data.repository.TramRepository
import com.example.tramapp.domain.DestinationLineCacheUseCase
import com.example.tramapp.domain.junction.GeoPoint
import com.example.tramapp.domain.junction.Junction
import com.example.tramapp.domain.junction.JunctionEngine
import com.example.tramapp.domain.junction.JunctionLocator
import com.example.tramapp.domain.junction.JunctionSelection
import com.example.tramapp.domain.junction.JunctionSelector
import com.example.tramapp.domain.junction.JunctionSnapshot
import com.example.tramapp.domain.junction.LocationFix
import com.example.tramapp.domain.junction.RankedJunction
import com.example.tramapp.domain.location.LocationStateManager
import com.example.tramapp.glance.DeepLinkState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One junction card's worth of UI state: structure + live snapshot + pin flag (R4). */
data class JunctionCardState(
    val junction: Junction,
    val snapshot: JunctionSnapshot?,
    val isPinned: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TramRepository,
    private val preferencesManager: UserPreferencesManager,
    private val destinationLineCache: DestinationLineCacheUseCase,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationStateManager: LocationStateManager,
    private val junctionLocator: JunctionLocator,
    private val junctionEngine: JunctionEngine,
    private val deepLinkState: DeepLinkState,
) : ViewModel() {

    /**
     * Test seam: gates the unbounded `while (autoRefreshEnabled) { delay(...) }` background
     * refresh loop started in [init]. Unit tests MUST set this to `false` immediately after
     * construction (before the first dispatcher advance) — see docs/verification-harness.md.
     */
    var autoRefreshEnabled: Boolean = true

    companion object {
        private const val REFRESH_INTERVAL_MS = 20_000L
        private const val GPS_UPDATE_INTERVAL_MS = 10_000L
        private const val GPS_MIN_DISPLACEMENT_M = 25f
    }

    val currentLocation: StateFlow<LatLng> = locationStateManager.activeLocation
    val isManualLocation: StateFlow<Boolean> = locationStateManager.isManual

    val currentTime: StateFlow<java.time.OffsetDateTime> = kotlinx.coroutines.flow.flow {
        while (autoRefreshEnabled) {
            emit(java.time.OffsetDateTime.now())
            delay(10000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), java.time.OffsetDateTime.now())

    val favorites: StateFlow<Set<String>> = preferencesManager.userPreferences.map { it.favorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // R14 highlighting is per-row (from the junction model); favouritesFirst no longer reorders
    // rows (KTD10) but is kept as a preference for the star affordance's on/off default.
    val favoritesFirst: StateFlow<Boolean> = preferencesManager.userPreferences.map { it.favoritesFirst }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val selector = JunctionSelector()

    // Nearest-first junction order (R22/KTD10), with a deep-linked junction pinned to the front
    // and expanded (R4). Populated synchronously by [applySelection] — before any network call.
    private val _junctionOrder = MutableStateFlow<List<Junction>>(emptyList())
    val junctionOrder: StateFlow<List<Junction>> = _junctionOrder.asStateFlow()

    private val _pinnedNodeId = MutableStateFlow<String?>(null)
    val pinnedNodeId: StateFlow<String?> = _pinnedNodeId.asStateFlow()

    private val _noStopInRange = MutableStateFlow(false)
    val noStopInRange: StateFlow<Boolean> = _noStopInRange.asStateFlow()

    /** One combined snapshot per displayed junction, in [junctionOrder]'s order. */
    val junctionCards: StateFlow<List<JunctionCardState>> = combine(_junctionOrder, _pinnedNodeId) { order, pinned ->
        order to pinned
    }.flatMapLatest { (order, pinned) ->
        if (order.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(order.map { junctionEngine.snapshot(it.nodeId) }) { snapshots ->
                order.mapIndexed { index, junction ->
                    JunctionCardState(junction, snapshots[index], junction.nodeId == pinned)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appStatus: StateFlow<com.example.tramapp.ui.components.AppStatus> = combine(
        junctionCards,
        repository.throttleUntil
    ) { cards, throttleUntil ->
        val snapshots = cards.mapNotNull { it.snapshot }
        val connection = when {
            cards.isEmpty() && !_noStopInRange.value -> com.example.tramapp.ui.components.ConnectionStatus.LOADING
            snapshots.any { it.state == com.example.tramapp.domain.junction.SnapshotState.LOADING } ||
                cards.any { it.snapshot == null } ->
                com.example.tramapp.ui.components.ConnectionStatus.LOADING
            throttleUntil > System.currentTimeMillis() -> com.example.tramapp.ui.components.ConnectionStatus.ERROR
            snapshots.isNotEmpty() && snapshots.all { it.state == com.example.tramapp.domain.junction.SnapshotState.ERROR } ->
                com.example.tramapp.ui.components.ConnectionStatus.ERROR
            else -> com.example.tramapp.ui.components.ConnectionStatus.ONLINE
        }
        val lastUpdate = snapshots.mapNotNull { it.fetchedAtMs }.maxOrNull() ?: System.currentTimeMillis()
        com.example.tramapp.ui.components.AppStatus(connection = connection, lastUpdateTime = lastUpdate)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.tramapp.ui.components.AppStatus()
    )

    val throttleMessage: StateFlow<String?> = repository.throttleUntil.let { throttleFlow ->
        kotlinx.coroutines.flow.flow {
            throttleFlow.collect { until ->
                while (until > System.currentTimeMillis()) {
                    val seconds = (until - System.currentTimeMillis() + 999) / 1000
                    emit("API Throttled: Resuming in ${seconds}s")
                    delay(1000)
                }
                emit(null)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedTripDetails = MutableStateFlow<com.example.tramapp.domain.TripDetails?>(null)
    val selectedTripDetails: StateFlow<com.example.tramapp.domain.TripDetails?> = _selectedTripDetails.asStateFlow()

    private val _showTripPopup = MutableStateFlow(false)
    val showTripPopup: StateFlow<Boolean> = _showTripPopup.asStateFlow()

    private val _isTripLoading = MutableStateFlow(false)
    val isTripLoading: StateFlow<Boolean> = _isTripLoading.asStateFlow()

    val apiQueryCount: StateFlow<Int> = repository.apiQueryCount

    private var tripFetchJob: Job? = null
    private var locationCallback: LocationCallback? = null

    // Declared before init: init's coroutines run eagerly on Main.immediate and read these.
    private val startTimeMs = System.currentTimeMillis()
    private var firstRowsLogged = false

    private var lastSelection: JunctionSelection = JunctionSelection.NoFix

    init {
        viewModelScope.launch {
            // Step 1: an immediate fix from cached prefs so junction structure can render before
            // GPS/network — the selector accepts the very first fix regardless of accuracy.
            val prefs = preferencesManager.userPreferences.first()
            if (prefs.lastLat != null && prefs.lastLng != null) {
                if (prefs.isManualStartup) {
                    locationStateManager.setUserSelectedLocation(LatLng(prefs.lastLat, prefs.lastLng))
                } else {
                    locationStateManager.updateGpsLocation(LatLng(prefs.lastLat, prefs.lastLng))
                }
                onLocationFix(LocationFix(prefs.lastLat, prefs.lastLng, 50f, System.currentTimeMillis()))
            }

            if (!prefs.isManualStartup) {
                beginLocationUpdates()
            }
        }

        viewModelScope.launch {
            deepLinkState.junctionId.collect { nodeId ->
                if (nodeId != null) {
                    _pinnedNodeId.value = nodeId
                    deepLinkState.consume()
                    reapplyLastSelection()
                }
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            try {
                val prefs = preferencesManager.userPreferences.first()
                destinationLineCache.refreshIfNeeded(prefs)
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Cache refresh failed", e)
            }
        }

        // R17-alike: refresh every 20s while the app is visible (autoRefreshEnabled test seam).
        viewModelScope.launch {
            while (autoRefreshEnabled) {
                delay(REFRESH_INTERVAL_MS)
                if (!autoRefreshEnabled) break
                refreshAllVisible()
            }
        }

        // Success-criterion timing (see docs/plans .../U10 verification): ms from VM init to the
        // first snapshot with rows, i.e. live times visible somewhere on the dashboard.
        viewModelScope.launch {
            junctionCards.collect { cards ->
                if (!firstRowsLogged && cards.any { (it.snapshot?.rows?.size ?: 0) > 0 }) {
                    firstRowsLogged = true
                    android.util.Log.i(
                        "JunctionDashboard",
                        "First snapshot with rows after ${System.currentTimeMillis() - startTimeMs} ms"
                    )
                }
            }
        }
    }


    /** Public seam: production wires this from fused-location callbacks; tests call it directly
     *  to simulate "the first location fix" without mocking the Play Services Task API (R22/R23). */
    fun onLocationFix(fix: LocationFix) {
        viewModelScope.launch {
            val prefs = preferencesManager.userPreferences.first()
            selector.walkingRangeM = prefs.displayRadius.toDouble()
            val junctions = junctionLocator.junctionsNear(fix.point, prefs.displayRadius)
            selector.onFix(fix)
            val selection = selector.setJunctions(junctions)
            lastSelection = selection
            val refreshes = applySelection(selection, prefs.maxStations)
            // Refreshes learn which nodes are bus-only (isTram=false); drop them from the list
            // now rather than on the next GPS fix.
            refreshes.joinAll()
            val relearned = selector.setJunctions(junctionLocator.junctionsNear(fix.point, prefs.displayRadius))
            if (relearned is JunctionSelection.Selected && lastSelection === selection) {
                lastSelection = relearned
                applySelection(relearned, prefs.maxStations)
            }
        }
    }

    private fun reapplyLastSelection() {
        viewModelScope.launch {
            val prefs = preferencesManager.userPreferences.first()
            applySelection(lastSelection, prefs.maxStations)
        }
    }

    /** Orders the nearest [maxStations] junctions (pinned one first, R4), shows structure for
     *  every one of them synchronously (before any network call), then refreshes each
     *  concurrently through the shared engine — one batched call per junction, no delays
     *  between them (R19, R22, R23). */
    private fun applySelection(selection: JunctionSelection, maxStations: Int): List<Job> {
        return when (selection) {
            is JunctionSelection.Selected -> {
                _noStopInRange.value = false
                val order = buildDisplayOrder(selection.ranked, maxStations, _pinnedNodeId.value)
                _junctionOrder.value = order
                order.forEach { junctionEngine.show(it) }
                // The engine coalesces, so junctions refreshed within the last few seconds
                // cost nothing here.
                order.map { junction ->
                    viewModelScope.launch { junctionEngine.refresh(junction) }
                }
            }
            is JunctionSelection.NoneInRange -> {
                _noStopInRange.value = true
                _junctionOrder.value = emptyList()
                emptyList()
            }
            is JunctionSelection.NoFix -> {
                // keep whatever was last shown
                emptyList()
            }
        }
    }

    private fun buildDisplayOrder(ranked: List<RankedJunction>, maxStations: Int, pinned: String?): List<Junction> {
        val junctions = ranked.map { it.junction }
        if (pinned != null) {
            val pinnedJunction = junctions.find { it.nodeId == pinned }
            if (pinnedJunction != null) {
                val rest = junctions.filter { it.nodeId != pinned }
                return (listOf(pinnedJunction) + rest).take(maxStations)
            }
        }
        return junctions.take(maxStations)
    }

    private fun refreshAllVisible() {
        _junctionOrder.value.forEach { junction ->
            viewModelScope.launch { junctionEngine.refresh(junction) }
        }
    }

    fun refreshNow() {
        refreshAllVisible()
    }

    @SuppressLint("MissingPermission")
    private fun beginLocationUpdates() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    updateLocation(LatLng(location.latitude, location.longitude), isManual = false)
                    onLocationFix(LocationFix(location.latitude, location.longitude, location.accuracy, System.currentTimeMillis()))
                }
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_UPDATE_INTERVAL_MS)
                .setMinUpdateDistanceMeters(GPS_MIN_DISPLACEMENT_M)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    updateLocation(LatLng(loc.latitude, loc.longitude), isManual = false)
                    onLocationFix(LocationFix(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis()))
                }
            }
            locationCallback = callback
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            // No location permission yet / JVM test environment without Play Services — the
            // cached-prefs fix (init step 1) and manual location picking still work.
        }
    }

    fun updateLocation(latLng: LatLng, isManual: Boolean = true) {
        if (isManual) {
            locationStateManager.setUserSelectedLocation(latLng)
        } else {
            locationStateManager.updateGpsLocation(latLng)
        }
        viewModelScope.launch {
            preferencesManager.updateLastLocation(latLng.latitude, latLng.longitude)
            if (isManual) {
                preferencesManager.updateIsManualStartup(true)
                onLocationFix(LocationFix(latLng.latitude, latLng.longitude, 5f, System.currentTimeMillis()))
            }
        }
    }

    fun revertToGps() {
        viewModelScope.launch { preferencesManager.updateIsManualStartup(false) }
        beginLocationUpdates()
        locationStateManager.revertToGps()
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
}
