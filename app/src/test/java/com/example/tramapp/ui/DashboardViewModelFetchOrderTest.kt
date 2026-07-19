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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

/**
 * U7 (R12, R13): explicit nearest-first dispatch, cheap-before-expensive ordering, and no
 * added API-call volume versus baseline. See DashboardViewModelStateTest.kt's header note —
 * same `autoRefreshEnabled = false` / `runCurrent()`-not-`advanceUntilIdle()` constraints apply.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelFetchOrderTest {

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
        runBlocking { whenever(repository.getCachedDepartures(any())).thenReturn(emptyList()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun station(id: String, baseName: String, distanceOffset: Double) = StationEntity(
        id = id,
        name = "$baseName [A]",
        latitude = origin.latitude + distanceOffset,
        longitude = origin.longitude,
        lastUpdate = System.currentTimeMillis()
    )

    private fun futureDeparture(line: String): SmartDeparture {
        val futureTime = OffsetDateTime.now().plusMinutes(5).toString()
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
    fun `refreshNow dispatches stations nearest-first`() = runTest(testDispatcher) {
        val near = station("NEAR", "Near Station", 0.0001)
        val mid = station("MID", "Mid Station", 0.0003)
        val far = station("FAR", "Far Station", 0.0006)
        // visibleStationCount default (3) covers all three; feed them out of distance order
        // to prove the VM — not test setup — establishes nearest-first dispatch.
        val vm = buildViewModel(listOf(far, near, mid))
        whenever(repository.refreshNearbyStations(any(), any(), any()))
            .thenReturn(listOf("NEAR", "MID", "FAR"))
        whenever(getSmartDepartures.execute(any(), any())).thenReturn(listOf(futureDeparture("9")))
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        testDispatcher.scheduler.runCurrent()

        vm.refreshNow()
        testDispatcher.scheduler.advanceTimeBy(1000)
        testDispatcher.scheduler.runCurrent()
        job.cancel()

        val order = inOrder(getSmartDepartures)
        order.verify(getSmartDepartures).execute("NEAR", defaultPrefs)
        order.verify(getSmartDepartures).execute("MID", defaultPrefs)
        order.verify(getSmartDepartures).execute("FAR", defaultPrefs)
    }

    @Test
    fun `cheap departures call resolves before expensive bound-check is scheduled`() = runTest(testDispatcher) {
        val s = station("A1", "Kamenicka", 0.0001)
        val vm = buildViewModel(listOf(s))
        whenever(getSmartDepartures.execute("A1", defaultPrefs)).thenReturn(listOf(futureDeparture("9")))
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        vm.refreshStation("A1")
        testDispatcher.scheduler.runCurrent()
        job.cancel()

        val order = inOrder(getSmartDepartures)
        order.verify(getSmartDepartures).execute("A1", defaultPrefs)
        order.verify(getSmartDepartures, times(1)).checkBounds(any(), any(), any(), any(), any())
        // The row is already Ready (from the cheap call) once runCurrent() settles, regardless
        // of whether the bound-check has landed — i.e. it never gated the row.
        assertTrue(vm.stationStates.value["A1"] is StationUiState.Ready)
    }

    @Test
    fun `refresh cycle makes exactly one departures call per platform, no extra load`() = runTest(testDispatcher) {
        val near = station("NEAR", "Near Station", 0.0001)
        val far = station("FAR", "Far Station", 0.0006)
        val vm = buildViewModel(listOf(near, far))
        whenever(repository.refreshNearbyStations(any(), any(), any()))
            .thenReturn(listOf("NEAR", "FAR"))
        whenever(getSmartDepartures.execute(any(), any())).thenReturn(listOf(futureDeparture("9")))
        whenever(getSmartDepartures.checkBounds(any(), any(), any(), any(), any()))
            .thenReturn(Triple(false, false, false))

        val job = backgroundScope.launch { vm.visibleStationRows.collect {} }
        testDispatcher.scheduler.runCurrent()

        vm.refreshNow()
        testDispatcher.scheduler.advanceTimeBy(1000)
        testDispatcher.scheduler.runCurrent()
        job.cancel()

        verify(getSmartDepartures, times(1)).execute("NEAR", defaultPrefs)
        verify(getSmartDepartures, times(1)).execute("FAR", defaultPrefs)
    }
}
