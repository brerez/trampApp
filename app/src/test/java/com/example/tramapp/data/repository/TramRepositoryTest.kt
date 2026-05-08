package com.example.tramapp.data.repository

import com.example.tramapp.data.local.dao.StationDao
import com.example.tramapp.data.remote.GolemioService
import com.example.tramapp.data.remote.GolemioResponse
import com.example.tramapp.data.remote.StopProperties
import com.example.tramapp.data.remote.Feature
import com.example.tramapp.data.remote.Geometry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TramRepositoryTest {

    @Mock
    lateinit var apiService: GolemioService

    @Mock
    lateinit var stationDao: StationDao

    @Mock
    lateinit var departureDao: com.example.tramapp.data.local.dao.DepartureDao

    @Mock
    lateinit var tripRouteDao: com.example.tramapp.data.local.dao.TripRouteDao

    @Mock
    lateinit var lineDirectionDao: com.example.tramapp.data.local.dao.LineDirectionDao

    lateinit var repository: TramRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = TramRepository(apiService, stationDao, departureDao, tripRouteDao, lineDirectionDao)
    }

    @Test
    fun `refreshNearbyStations should filter platforms and append direction to name`() = runTest {
        // Mock response
        val mockFeatures = listOf(
            Feature(
                geometry = Geometry(listOf(14.428, 50.099)),
                properties = StopProperties(
                    stopId = "U231Z1P",
                    stopName = "Kamenická",
                    platformCode = "A",
                    locationType = 0
                )
            ),
            Feature(
                geometry = Geometry(listOf(14.429, 50.100)),
                properties = StopProperties(
                    stopId = "U231Z2P",
                    stopName = "Kamenická",
                    platformCode = "B",
                    locationType = 0
                )
            )
        )
        val mockResponse = GolemioResponse(features = mockFeatures)

        whenever(apiService.getStops(any(), any())).thenReturn(mockResponse)
        whenever(stationDao.getAllStations()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))

        val ids = repository.refreshNearbyStations(50.099, 14.428, 1000)

        assertEquals(2, ids.size)
        verify(stationDao).insertStations(any())
    }

    @Test
    fun `testDlouhaTridaTerminals should return both terminals`() = runTest {
        val mockFeatures = listOf(
            Feature(
                geometry = Geometry(listOf(14.4285, 50.0905)),
                properties = StopProperties(
                    stopId = "U_DL_1",
                    stopName = "Dlouhá třída",
                    platformCode = "A",
                    locationType = 0
                )
            ),
            Feature(
                geometry = Geometry(listOf(14.4286, 50.0906)),
                properties = StopProperties(
                    stopId = "U_DL_2",
                    stopName = "Dlouhá třída",
                    platformCode = "B",
                    locationType = 0
                )
            )
        )
        val mockResponse = GolemioResponse(features = mockFeatures)

        whenever(apiService.getStops(any(), any())).thenReturn(mockResponse)
        whenever(stationDao.getAllStations()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))

        val ids = repository.refreshNearbyStations(50.0905, 14.4285, 1000)

        assertEquals(2, ids.size)
        assertEquals(listOf("U_DL_1", "U_DL_2"), ids)
    }

    @Test
    fun `refreshNearbyStations should fetch fresh data if cached station is far`() = runTest {
        // Cache has one station far away (approx 500m)
        val cachedStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U_FAR",
            name = "Far Station",
            latitude = 50.0950, // 0.0045 degrees away
            longitude = 14.4285,
            lastUpdate = System.currentTimeMillis()
        )
        whenever(stationDao.getAllStations()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(cachedStation)))

        // API returns Dlouha Trida
        val mockFeatures = listOf(
            Feature(
                geometry = Geometry(listOf(14.4285, 50.0905)),
                properties = StopProperties(
                    stopId = "U_DL_1",
                    stopName = "Dlouhá třída",
                    platformCode = "A",
                    locationType = 0
                )
            )
        )
        val mockResponse = GolemioResponse(features = mockFeatures)
        whenever(apiService.getStops(any(), any())).thenReturn(mockResponse)

        val ids = repository.refreshNearbyStations(50.0905, 14.4285, 1000)

        assertEquals(1, ids.size)
        assertEquals("U_DL_1", ids[0])
    }

    @Test
    fun `getDepartures should mark station as non-tram if it has departures but no trams`() = runTest {
        val stopId = "U123"
        val mockResponse = com.example.tramapp.data.remote.DepartureResponse(
            departures = listOf(
                com.example.tramapp.data.remote.DepartureItem(
                    route = com.example.tramapp.data.remote.RouteInfo("100", 3), // 3 = Bus
                    trip = com.example.tramapp.data.remote.TripInfo("Dest"),
                    arrival = com.example.tramapp.data.remote.TimestampInfo("2026-05-08T23:00:00Z", null),
                    stop = com.example.tramapp.data.remote.StopInfo(stopId)
                )
            )
        )
        whenever(apiService.getDepartures(stopId)).thenReturn(mockResponse)
        
        repository.getDepartures(stopId)
        
        // We expect it to update isTram to false!
        verify(stationDao).updateIsTramStatus(stopId, false)
    }
}
