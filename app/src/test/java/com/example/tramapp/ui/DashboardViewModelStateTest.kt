package com.example.tramapp.ui

import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.local.datastore.UserPreferencesManager
import com.example.tramapp.data.local.entity.StationEntity
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.RouteInfo
import com.example.tramapp.data.remote.StopInfo
import com.example.tramapp.data.remote.TimestampInfo
import com.example.tramapp.data.remote.TripInfo
import com.example.tramapp.data.repository.TramRepository
import com.example.tramapp.domain.DestinationLineCacheUseCase
import com.example.tramapp.domain.GetSmartDeparturesUseCase
import com.example.tramapp.domain.SmartDeparture
import com.example.tramapp.domain.location.LocationStateManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

/**
 * U4 (R1-R4, R24): promotion/collapse/stable-order correctness for
 * [DashboardViewModel.visibleStationRows].
 *
 * NOTE: [DashboardViewModel] starts unbounded `while (autoRefreshEnabled) { delay(...) }`
 * background loops in `init` (60s/30s refresh, 10s clock tick). Every test here MUST build
 * its ViewModel via [buildViewModel] (or set `autoRefreshEnabled = false` immediately after
 * a manual constructor call) *before* the first dispatcher advance — otherwise those loops
 * keep the shared `TestCoroutineScheduler` non-idle forever and `runTest`'s implicit final
 * advance-to-idle check hangs. Also avoid `advanceUntilIdle()`; use `runCurrent()` (and
 * bounded `advanceTimeBy()` when a short internal delay, e.g. enrichment's per-tram 150ms,
 * must be crossed) instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelStateTest {

    @Mock lateinit var repository: TramRepository
    @Mock lateinit var getSmartDepartures: GetSmartDeparturesUseCase
    @Mock lateinit var preferencesManager: UserPreferencesManager
    @Mock lateinit var destinationLineCache: DestinationLineCacheUseCase
    @Mock lateinit var fusedLocationClient: FusedLocationProviderClient
    @Mock lateinit var locationStateManager: LocationStateManager
    @Mock lateinit var context: android.content.Context

    private val testDispatcher = StandardTestDispatcher()

    private val defaultPrefs = UserPreferences(
        homeLat = null, homeLng = null, homeAddress = null,
        workLat = null, workLng = null, workAddress = null,
        schoolLat = null, schoolLng = null, schoolAddress = null,
        lastLat = null, lastLng = null, isManualStartup = true,
        displayRadius = 1500, maxStations = 2,
        homeLines = emptySet(), workLines = emptySet(), schoolLines = emptySet(),
        homeStopNames = emptySet(), workStopNames = emptySet(), schoolStopNames = emptySet(),
        homeStopIds = emptySet(), workStopIds = emptySet(), schoolStopIds = emptySet(),
        homeLinesTimestamp = 0, workLinesTimestamp = 0, schoolLinesTimestamp = 0,
        favorites = emptySet(), favoritesFirst = false
    )

    private val origin = LatLng(50.0755, 14.4378)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        whenever(locationStateManager.activeLocation).thenReturn(MutableStateFlow(origin))
        whenever(locationStateManager.isManual).thenReturn(MutableStateFlow(false))
        whenever(repository.apiQueryCount).thenReturn(MutableStateFlow(0))
        whenever(repository.allStations).thenReturn(flowOf(emptyList()))
        whenever(preferencesManager.userPreferences).thenReturn(flowOf(defaultPrefs))
        // init's loadCachedDepartures() calls this unconditionally for every visible station;
        // an unstubbed suspend fun returns null from Mockito, NPEing on the null List filter.
        runBlocking { whenever(repository.getCachedDepartures(any())).thenReturn(emptyList()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun station(id: String, baseName: String, platform: String, distanceOffset: Double) = StationEntity(
        id = id,
        name = "$baseName [$platform]",
        latitude = origin.latitude + distanceOffset,
        longitude = origin.longitude,
        lastUpdate = System.currentTimeMillis()
    )

    private fun futureDeparture(line: String, minutesFromNow: Long = 5): SmartDeparture {
        val futureTime = OffsetDateTime.now().plusMinutes(minutesFromNow).toString()
        return SmartDeparture(
            DepartureItem(
                route = RouteInfo(line, 0),
                trip = TripInfo("Dest $line"),
                arrival = TimestampInfo(futureTime, null),
                stop = StopInfo("stop-$line")
            )
        )
    }

    private fun buildViewModel(stations: List<StationEntity>): DashboardViewModel {
        whenever(repository.allStations).thenReturn(flowOf(stations))
        return DashboardViewModel(
            repository, getSmartDepartures, preferencesManager,
            destinationLineCache, fusedLocationClient, locationStateManager, context
        ).apply { autoRefreshEnabled = false }
    }

    @Test
    fun `happy path promotes Loading to Ready and appears in ordered list`() = runTest(testDispatcher) {
        val s = station("A1", "Kamenicka", "A", 0.0001)
        val vm = buildViewModel(listOf(s))
        whenever(getSmartDepartures.execute("A1", defaultPrefs)).thenReturn(listOf(futureDeparture("9")))
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        vm.refreshStation("A1")
        testDispatcher.scheduler.runCurrent()

        val rows = vm.visibleStationRows.value
        job.cancel()

        assertEquals(1, rows.size)
        assertTrue(rows[0].isReady)
        assertEquals("Kamenicka", rows[0].baseName)
        assertEquals(1, rows[0].platformDepartures.single().second.size)
    }

    @Test
    fun `empty collapse excludes station with no future departures`() = runTest(testDispatcher) {
        val s = station("A1", "Kamenicka", "A", 0.0001)
        val vm = buildViewModel(listOf(s))
        whenever(getSmartDepartures.execute("A1", defaultPrefs)).thenReturn(emptyList())

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        vm.refreshStation("A1")
        testDispatcher.scheduler.runCurrent()

        val rows = vm.visibleStationRows.value
        job.cancel()

        assertTrue(rows.isEmpty())
        assertEquals(StationUiState.Empty, vm.stationStates.value["A1"])
    }

    @Test
    fun `stable order keeps fast Ready station in place while closer station resolves Empty`() = runTest(testDispatcher) {
        // "Far" resolves Ready first; "Near" (closer, listed first) resolves Empty later.
        val near = station("NEAR", "Near Station", "A", 0.0001)
        val far = station("FAR", "Far Station", "A", 0.0005)
        val vm = buildViewModel(listOf(near, far))

        whenever(getSmartDepartures.execute("FAR", defaultPrefs)).thenReturn(listOf(futureDeparture("9")))
        whenever(getSmartDepartures.execute("NEAR", defaultPrefs)).thenReturn(emptyList())
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }

        vm.refreshStation("FAR")
        testDispatcher.scheduler.runCurrent()
        // FAR is Ready; NEAR hasn't resolved yet -> still shown as Loading, after FAR.
        var rows = vm.visibleStationRows.value
        assertEquals(2, rows.size)
        assertEquals("Far Station", rows[0].baseName)
        assertTrue(rows[0].isReady)
        assertEquals("Near Station", rows[1].baseName)
        assertFalse(rows[1].isReady)

        vm.refreshStation("NEAR")
        testDispatcher.scheduler.runCurrent()
        // NEAR settles Empty -> collapsed; FAR (already Ready) keeps its position/identity.
        rows = vm.visibleStationRows.value
        job.cancel()

        assertEquals(1, rows.size)
        assertEquals("Far Station", rows[0].baseName)
        assertTrue(rows[0].isReady)
    }

    @Test
    fun `non-blocking enrichment patches highlights without demoting or reordering`() = runTest(testDispatcher) {
        val s = station("A1", "Kamenicka", "A", 0.0001)
        val vm = buildViewModel(listOf(s))
        whenever(getSmartDepartures.execute("A1", defaultPrefs)).thenReturn(listOf(futureDeparture("9")))

        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(true, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        vm.refreshStation("A1")
        testDispatcher.scheduler.runCurrent()
        // Cross enrichStation's internal 150ms per-tram delay (bounded — well short of the
        // 10s+ background loop intervals, so this can't wake the unbounded refresh loops).
        testDispatcher.scheduler.advanceTimeBy(200)
        testDispatcher.scheduler.runCurrent()

        val rows = vm.visibleStationRows.value
        job.cancel()

        assertEquals(1, rows.size)
        assertTrue(rows[0].isReady)
        assertEquals("Kamenicka", rows[0].baseName)
        assertTrue(rows[0].platformDepartures.single().second.single().isHomeBound)
        assertTrue(vm.stationStates.value["A1"] is StationUiState.Ready)
    }

    @Test
    fun `station leaving visible set leaves no stale entry in derived rows`() = runTest(testDispatcher) {
        val s = station("A1", "Kamenicka", "A", 0.0001)
        val allStations = MutableStateFlow(listOf(s))
        whenever(repository.allStations).thenReturn(allStations)
        val vm = DashboardViewModel(
            repository, getSmartDepartures, preferencesManager,
            destinationLineCache, fusedLocationClient, locationStateManager, context
        ).apply { autoRefreshEnabled = false }
        whenever(getSmartDepartures.execute("A1", defaultPrefs)).thenReturn(listOf(futureDeparture("9")))
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        vm.refreshStation("A1")
        testDispatcher.scheduler.runCurrent()

        assertTrue(vm.visibleStationRows.value.single().isReady)
        // stationStates retains A1 -> Ready internally, but once it leaves the visible set...
        allStations.value = emptyList()
        testDispatcher.scheduler.runCurrent()
        val rows = vm.visibleStationRows.value
        job.cancel()

        // ...the derived list must not resurrect a stale row for it.
        assertTrue(rows.isEmpty())
        assertTrue(vm.stationStates.value["A1"] is StationUiState.Ready)
    }

    @Test
    fun `ready station re-fetch does not flicker back to loading in derived rows`() = runTest(testDispatcher) {
        val s = station("A1", "Kamenicka", "A", 0.0001)
        val vm = buildViewModel(listOf(s))
        whenever(getSmartDepartures.execute("A1", defaultPrefs)).thenReturn(listOf(futureDeparture("9")))
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        vm.refreshStation("A1")
        testDispatcher.scheduler.runCurrent()
        assertTrue(vm.visibleStationRows.value.single().isReady)

        // Re-fetch cycle: refreshStation transitions Loading then Ready again.
        vm.refreshStation("A1")
        testDispatcher.scheduler.runCurrent()
        val rows = vm.visibleStationRows.value
        job.cancel()

        assertEquals(1, rows.size)
        assertTrue(rows[0].isReady)
    }

    // --- U6 (R5, R6): neutral Nearby default + non-blocking relevance overlay ---

    @Test
    fun `neutral default orders Ready stations by distance only, no clock-time influence`() = runTest(testDispatcher) {
        val near = station("NEAR", "Near Station", "A", 0.0001)
        val far = station("FAR", "Far Station", "A", 0.0005)
        val vm = buildViewModel(listOf(near, far))
        whenever(getSmartDepartures.execute("NEAR", defaultPrefs)).thenReturn(listOf(futureDeparture("9")))
        whenever(getSmartDepartures.execute("FAR", defaultPrefs)).thenReturn(listOf(futureDeparture("22")))
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        vm.refreshStation("NEAR")
        vm.refreshStation("FAR")
        testDispatcher.scheduler.runCurrent()
        val rows = vm.visibleStationRows.value
        job.cancel()

        assertEquals(2, rows.size)
        assertEquals("Near Station", rows[0].baseName)
        assertEquals("Far Station", rows[1].baseName)
    }

    @Test
    fun `favorites reorder lines within a station stably without moving station order`() = runTest(testDispatcher) {
        val prefsWithFavs = defaultPrefs.copy(favorites = setOf("9"), favoritesFirst = true)
        whenever(preferencesManager.userPreferences).thenReturn(flowOf(prefsWithFavs))
        val near = station("NEAR", "Near Station", "A", 0.0001)
        val far = station("FAR", "Far Station", "A", 0.0005)
        val vm = buildViewModel(listOf(near, far))
        whenever(getSmartDepartures.execute("NEAR", prefsWithFavs))
            .thenReturn(listOf(futureDeparture("22"), futureDeparture("9")))
        whenever(getSmartDepartures.execute("FAR", prefsWithFavs)).thenReturn(listOf(futureDeparture("22")))
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        vm.refreshStation("NEAR")
        vm.refreshStation("FAR")
        testDispatcher.scheduler.runCurrent()
        val rows = vm.visibleStationRows.value
        job.cancel()

        // Station order unaffected by favorites (still distance-first).
        assertEquals("Near Station", rows[0].baseName)
        assertEquals("Far Station", rows[1].baseName)
        // Within NEAR, favorited line "9" sorts before "22".
        val nearDeps = rows[0].platformDepartures.single().second
        assertEquals("9", nearDeps[0].item.route.shortName)
        assertEquals("22", nearDeps[1].item.route.shortName)
    }
}
