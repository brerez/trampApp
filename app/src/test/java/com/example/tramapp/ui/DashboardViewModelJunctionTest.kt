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
import com.example.tramapp.domain.junction.DestinationNodeSource
import com.example.tramapp.domain.junction.DestinationNodes
import com.example.tramapp.domain.junction.JunctionDepartureSource
import com.example.tramapp.domain.junction.JunctionEngine
import com.example.tramapp.domain.junction.JunctionLocator
import com.example.tramapp.domain.junction.JunctionStationSource
import com.example.tramapp.domain.junction.LocationFix
import com.example.tramapp.domain.junction.NextStopLookup
import com.example.tramapp.domain.junction.TripNextStop
import com.example.tramapp.domain.location.LocationStateManager
import com.example.tramapp.glance.DeepLinkState
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

/**
 * U10 (R4, R22, R23; KTD7, KTD8, KTD10): [DashboardViewModel] on the junction model.
 *
 * Uses fakes for [JunctionDepartureSource]/[NextStopLookup]/[DestinationNodeSource] and
 * [JunctionStationSource] (introduced for this test, per the plan) with REAL [JunctionEngine]
 * and [JunctionLocator] instances — only the network/DB edges are faked, the junction domain
 * logic under test is real.
 *
 * Gotcha (see docs/verification-harness.md): must set `autoRefreshEnabled = false` right after
 * construction, and use `runCurrent()`/bounded `advanceTimeBy()`, never `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelJunctionTest {

    @Mock lateinit var repository: TramRepository
    @Mock lateinit var preferencesManager: UserPreferencesManager
    @Mock lateinit var destinationLineCache: DestinationLineCacheUseCase
    @Mock lateinit var fusedLocationClient: FusedLocationProviderClient

    private val testDispatcher = StandardTestDispatcher()
    private val locationStateManager = LocationStateManager()
    private val deepLinkState = DeepLinkState()

    private val defaultPrefs = UserPreferences(
        homeLat = null, homeLng = null, homeAddress = null,
        workLat = null, workLng = null, workAddress = null,
        schoolLat = null, schoolLng = null, schoolAddress = null,
        lastLat = null, lastLng = null, isManualStartup = true,
        displayRadius = 1500, maxStations = 4,
        homeLines = emptySet(), workLines = emptySet(), schoolLines = emptySet(),
        homeStopNames = emptySet(), workStopNames = emptySet(), schoolStopNames = emptySet(),
        homeStopIds = emptySet(), workStopIds = emptySet(), schoolStopIds = emptySet(),
        homeLinesTimestamp = 0, workLinesTimestamp = 0, schoolLinesTimestamp = 0,
        favorites = setOf("9"), favoritesFirst = false
    )

    private class FakeStationSource(stations: List<StationEntity>) : JunctionStationSource {
        private val flow = MutableStateFlow(stations)
        override val allStations = flow
        var refreshCount = 0
        override suspend fun refreshNearbyStations(lat: Double, lng: Double, radius: Int): List<String> {
            refreshCount++
            return flow.value.map { it.id }
        }
    }

    private class FakeDepartureSource : JunctionDepartureSource {
        val callCount = AtomicInteger(0)
        var responses: Map<String, List<DepartureItem>> = emptyMap()
        /** When set, getJunctionDepartures suspends until completed — lets a test observe the
         *  "structure before network completes" window. */
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getJunctionDepartures(platformIds: List<String>): Map<String, List<DepartureItem>> {
            callCount.incrementAndGet()
            gate?.await()
            return platformIds.associateWith { responses[it].orEmpty() }
        }
    }

    private class FakeNextStopLookup : NextStopLookup {
        override suspend fun cachedAll(departuresByPlatform: Map<String, List<DepartureItem>>) = emptyMap<String, Map<String, TripNextStop>>()
        override suspend fun resolveAll(departuresByPlatform: Map<String, List<DepartureItem>>) = emptyMap<String, Map<String, TripNextStop>>()
    }

    private class FakeDestinationNodeSource : DestinationNodeSource {
        override suspend fun current(): DestinationNodes = DestinationNodes.NONE
    }

    private val departureSource = FakeDepartureSource()

    private fun station(id: String, nodeId: String, name: String, platform: String, lat: Double, lng: Double) = StationEntity(
        id = id, name = name, latitude = lat, longitude = lng,
        lastUpdate = System.currentTimeMillis(), isTram = true, nodeId = nodeId, platformCode = platform
    )

    private fun departure(
        line: String,
        stopId: String,
        minutesFromNow: Long,
        headsign: String = "Dest $line",
        tripId: String = "$line-$stopId"
    ) = DepartureItem(
        route = RouteInfo(line, 0),
        trip = TripInfo(headsign = headsign, tripId = tripId),
        arrival = TimestampInfo(
            scheduled = java.time.OffsetDateTime.now().plusMinutes(minutesFromNow).toString(),
            predicted = java.time.OffsetDateTime.now().plusMinutes(minutesFromNow).toString()
        ),
        stop = StopInfo(id = stopId)
    )

    private fun buildViewModel(
        stations: List<StationEntity>,
        engine: JunctionEngine = JunctionEngine(departureSource, FakeNextStopLookup(), FakeDestinationNodeSource())
    ): DashboardViewModel {
        val locator = JunctionLocator(FakeStationSource(stations))
        return DashboardViewModel(
            repository, preferencesManager, destinationLineCache, fusedLocationClient,
            locationStateManager, locator, engine, deepLinkState
        ).apply { autoRefreshEnabled = false }
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(repository.apiQueryCount).thenReturn(MutableStateFlow(0))
        whenever(repository.throttleUntil).thenReturn(MutableStateFlow(0L))
        whenever(preferencesManager.userPreferences).thenReturn(flowOf(defaultPrefs))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `junction structure appears before any network call completes, with no departure times`() = runTest(testDispatcher) {
        departureSource.gate = CompletableDeferred()
        val stations = listOf(
            station("U1Z1P", "U1", "Kamenicka", "A", 50.10, 14.40),
            station("U1Z2P", "U1", "Kamenicka", "B", 50.1001, 14.40)
        )
        val vm = buildViewModel(stations)

        val job = backgroundScope.launch { vm.junctionCards.collect {} }
        vm.onLocationFix(LocationFix(50.10, 14.40, 10f, System.currentTimeMillis()))
        testDispatcher.scheduler.runCurrent()

        val cards = vm.junctionCards.value
        job.cancel()

        assertEquals(1, cards.size)
        assertEquals("U1", cards[0].junction.nodeId)
        // Structure is visible; rows are still null because the fake network call is gated open.
        assertNull(cards[0].snapshot?.rows)
    }

    @Test
    fun `first fix refreshes with no debounce, 4 junctions cause exactly 4 departure-board calls`() = runTest(testDispatcher) {
        val stations = (1..4).flatMap { i ->
            listOf(station("U${i}Z1P", "U$i", "Junction $i", "A", 50.10 + i * 0.001, 14.40))
        }
        val vm = buildViewModel(stations)

        val job = backgroundScope.launch { vm.junctionCards.collect {} }
        vm.onLocationFix(LocationFix(50.10, 14.40, 10f, System.currentTimeMillis()))
        testDispatcher.scheduler.runCurrent()
        job.cancel()

        assertEquals(4, vm.junctionOrder.value.size)
        assertEquals(4, departureSource.callCount.get())
    }

    @Test
    fun `R4 deep-linked junction id places it first and marks it pinned`() = runTest(testDispatcher) {
        val stations = listOf(
            station("NEARZ1P", "NEAR", "Nearest", "A", 50.10, 14.40),
            station("KAMZ1P", "KAM", "Kamenicka", "A", 50.11, 14.41)
        )
        deepLinkState.setJunctionId("KAM")
        val vm = buildViewModel(stations)
        testDispatcher.scheduler.runCurrent()

        val job = backgroundScope.launch { vm.junctionCards.collect {} }
        vm.onLocationFix(LocationFix(50.10, 14.40, 10f, System.currentTimeMillis()))
        testDispatcher.scheduler.runCurrent()

        val cards = vm.junctionCards.value
        job.cancel()

        assertEquals("KAM", cards.first().junction.nodeId)
        assertTrue(cards.first().isPinned)
    }

    @Test
    fun `favourite lines are surfaced without reordering rows`() = runTest(testDispatcher) {
        departureSource.responses = mapOf(
            "U1Z1P" to listOf(
                departure("22", "U1Z1P", minutesFromNow = 2, headsign = "Florenc"),
                departure("9", "U1Z1P", minutesFromNow = 5, headsign = "Florenc")
            )
        )
        val stations = listOf(station("U1Z1P", "U1", "Kamenicka", "A", 50.10, 14.40))
        val vm = buildViewModel(stations)

        val job = backgroundScope.launch { vm.junctionCards.collect {} }
        val favJob = backgroundScope.launch { vm.favorites.collect {} }
        vm.onLocationFix(LocationFix(50.10, 14.40, 10f, System.currentTimeMillis()))
        testDispatcher.scheduler.runCurrent()

        val rows = vm.junctionCards.value.first().snapshot?.rows
        job.cancel()
        favJob.cancel()

        assertTrue(vm.favorites.value.contains("9"))
        // Soonest-first: line 22 (2 min) stays first even though line 9 is the favourite.
        assertEquals("22", rows?.single()?.trams?.get(0)?.line)
        assertEquals("9", rows?.single()?.trams?.get(1)?.line)
    }
}
