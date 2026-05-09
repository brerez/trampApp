package com.example.tramapp.ui

import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.local.datastore.UserPreferencesManager
import com.example.tramapp.data.repository.TramRepository
import com.example.tramapp.domain.GetSmartDeparturesUseCase
import com.example.tramapp.domain.DestinationLineCacheUseCase
import com.google.android.gms.location.FusedLocationProviderClient
import com.example.tramapp.domain.location.LocationStateManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @Mock
    lateinit var repository: TramRepository

    @Mock
    lateinit var getSmartDepartures: GetSmartDeparturesUseCase

    @Mock
    lateinit var preferencesManager: UserPreferencesManager

    @Mock
    lateinit var destinationLineCache: DestinationLineCacheUseCase

    @Mock
    lateinit var fusedLocationClient: FusedLocationProviderClient

    @Mock
    lateinit var locationStateManager: LocationStateManager

    @Mock
    lateinit var context: android.content.Context

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

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        // Mock defaults
        whenever(locationStateManager.activeLocation).thenReturn(MutableStateFlow(LatLng(50.0755, 14.4378)))
        whenever(locationStateManager.isManual).thenReturn(MutableStateFlow(false))
        whenever(repository.apiQueryCount).thenReturn(MutableStateFlow(0))
        whenever(repository.allStations).thenReturn(flowOf(emptyList()))
        whenever(preferencesManager.userPreferences).thenReturn(flowOf(defaultPrefs))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stationDepartures should sort favorites first when enabled`() = runTest {
        val prefsWithFavs = defaultPrefs.copy(
            favorites = setOf("9"),
            favoritesFirst = true
        )
        whenever(preferencesManager.userPreferences).thenReturn(flowOf(prefsWithFavs))
        
        val mockStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U1",
            name = "Kamenická",
            latitude = 50.0755,
            longitude = 14.4378,
            lastUpdate = System.currentTimeMillis()
        )
        whenever(repository.allStations).thenReturn(flowOf(listOf(mockStation)))
        
        val futureTime = OffsetDateTime.now().plusMinutes(5).toString()
        val mockDepartures = listOf(
            com.example.tramapp.data.remote.DepartureItem(
                route = com.example.tramapp.data.remote.RouteInfo("22", 0),
                trip = com.example.tramapp.data.remote.TripInfo("Dest 22"),
                arrival = com.example.tramapp.data.remote.TimestampInfo(futureTime, null),
                stop = com.example.tramapp.data.remote.StopInfo("U1")
            ),
            com.example.tramapp.data.remote.DepartureItem(
                route = com.example.tramapp.data.remote.RouteInfo("9", 0),
                trip = com.example.tramapp.data.remote.TripInfo("Dest 9"),
                arrival = com.example.tramapp.data.remote.TimestampInfo(futureTime, null),
                stop = com.example.tramapp.data.remote.StopInfo("U1")
            )
        )
        whenever(repository.getCachedDepartures("U1")).thenReturn(mockDepartures)
        
        val viewModel = DashboardViewModel(
            repository,
            getSmartDepartures,
            preferencesManager,
            destinationLineCache,
            fusedLocationClient,
            locationStateManager,
            context
        )
        
        // Subscribe to flow to trigger stateIn
        val job = backgroundScope.launch {
            viewModel.stationDepartures.collect {}
        }
        
        // Wait for coroutines to run
        testDispatcher.scheduler.advanceUntilIdle()
        
        val result = viewModel.stationDepartures.value
        job.cancel()
        val departuresForU1 = result["U1"]
        
        assertEquals(2, departuresForU1?.size)
        // Line 9 should be first because it is in favorites!
        assertEquals("9", departuresForU1?.get(0)?.item?.route?.shortName)
        assertEquals("22", departuresForU1?.get(1)?.item?.route?.shortName)
    }
}
