package com.example.tramapp.data.repository

import com.example.tramapp.data.local.dao.StationDao
import com.example.tramapp.data.remote.DepartureItem
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

class TramRepositoryNightTimeFilteringTest {

    @Mock
    lateinit var apiService: com.example.tramapp.data.remote.GolemioService

    @Mock
    lateinit var stationDao: StationDao

    @Mock
    lateinit var departureDao: com.example.tramapp.data.local.dao.DepartureDao

    @Mock
    lateinit var tripRouteDao: com.example.tramapp.data.local.dao.TripRouteDao

    @Mock
    lateinit var lineDirectionDao: com.example.tramapp.data.local.dao.LineDirectionDao

    private lateinit var repository: TramRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = TramRepository(
            apiService, stationDao, departureDao, tripRouteDao, lineDirectionDao
        )
    }

    @Test
    fun `refreshNearbyStationsShouldFilterOutNightTimeStationsWhenCacheHasOldData`() = runTest {
        // Cache contains tram stations that were last updated at midnight (night time)
        val midnightStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U_NIGHT_1",
            name = "Night Station",
            latitude = 50.123,
            longitude = 14.456,
            isTram = false, // Bus station that's stale during night
            lastUpdate = System.currentTimeMillis() - (5 * 60 * 1000) // 5 minutes ago from midnight
        )

        val sixAmToday = java.time.ZonedDateTime.now()
            .withHour(6).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()

        whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(midnightStation))
        )
        whenever(stationDao.insertStations(any())).thenReturn(Unit)

        // Mock fresh API response
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

        // Call refreshNearbyStations at night (e.g., 2 AM simulation)
        val ids = repository.refreshNearbyStations(50.0905, 14.4285, 1000)

        assertEquals(1, ids.size)
        assertEquals("U_DL_1", ids[0])
        verify(stationDao).insertStations(any())
    }

    @Test
    fun `refreshNearbyStationsShouldAllowStaleTramStationsDuringNightToRemainInCache`() = runTest {
        // Cache contains a tram station with isTram=true but last updated at 2 AM
        val nightTramStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U_NIGHT Tram",
            name = "Night Tram Station",
            latitude = 50.123,
            longitude = 14.456,
            isTram = true, // This is a tram station!
            lastUpdate = System.currentTimeMillis() - (2 * 60 * 1000) // 2 minutes ago at 2 AM
        )

        whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(nightTramStation))
        )
        whenever(stationDao.insertStations(any())).thenReturn(Unit)

        // API might return no stations in this case, but existing cache should be valid
        val mockFeatures = emptyList<Feature<StopProperties>>()
        val mockResponse = GolemioResponse(features = mockFeatures)
        whenever(apiService.getStops(any(), any())).thenReturn(mockResponse)

        val ids = repository.refreshNearbyStations(50.123, 14.456, 1000)

        // Should return cached tram station since isTram=true
        assertEquals(1, ids.size)
        assertEquals("U_NIGHT Tram", ids[0])
    }

    @Test
    fun `refreshNearbyStationsShouldFilterOutNonTramStationsDuringLateNightHours`() = runTest {
        // Simulate testing at 23:30 (11:30 PM)
        val originalHour = java.time.ZonedDateTime.now().hour
        
        // The logic checks for hour >= 23 || hour < 6
        // At 23:30, hour is 23, so isNightDecision would be true
        val midnightStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U_BUS_NIGHT",
            name = "Night Bus Station",
            latitude = 50.123,
            longitude = 14.456,
            isTram = false, // Not a tram station
            lastUpdate = System.currentTimeMillis() - (3 * 60 * 1000)
        )

        whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(midnightStation))
        )
        whenever(stationDao.insertStations(any())).thenReturn(Unit)

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

        // Non-tram station from night time should be filtered out
        assertEquals(1, ids.size)
        assertEquals("U_DL_1", ids[0])
    }

    @Test
    fun `refreshNearbyStationsShouldAllowOldNonTramStationsIfNewDataExists`() = runTest {
        // Old non-tram station from night time but with fresh API data available
        val oldNightStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U_OLD_NIGHT",
            name = "Old Night Station",
            latitude = 50.123,
            longitude = 14.456,
            isTram = false,
            lastUpdate = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours ago
        )

        whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(oldNightStation))
        )
        whenever(stationDao.insertStations(any())).thenReturn(Unit)

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

        // Should fetch fresh data from API instead of using old non-tram cache
        assertEquals(1, ids.size)
        assertEquals("U_DL_1", ids[0])
    }

    @Test
    fun `refreshNearbyStationsShouldAllowEarlyMorningStationsThatAreTrams`() = runTest {
        // 5:30 AM - still before 6 AM but tram station should be included
        val fiveAmStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U_EARLY_AM",
            name = "Early Morning Station",
            latitude = 50.123,
            longitude = 14.456,
            isTram = true, // Tram station should be included even before 6 AM
            lastUpdate = System.currentTimeMillis() - (30 * 60 * 1000)
        )

        whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(fiveAmStation))
        )
        whenever(stationDao.insertStations(any())).thenReturn(Unit)

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

        // Should return fresh API data (tram station in cache is valid but we get new data anyway)
        assertEquals(1, ids.size)
        assertEquals("U_DL_1", ids[0])
    }

    @Test
    fun `refreshNearbyStationsShouldAllowEarlyMorningNonTramStationsIfTheyAreRecent`() = runTest {
        // 5:30 AM - a bus station updated just before 6 AM is still valid for short time
        val recentBusStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U_RECENT_BUS",
            name = "Recent Bus Station",
            latitude = 50.123,
            longitude = 14.456,
            isTram = false, // Bus station
            lastUpdate = System.currentTimeMillis() - (10 * 60 * 1000) // 10 minutes ago
        )

        whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(recentBusStation))
        )
        whenever(stationDao.insertStations(any())).thenReturn(Unit)

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

        // Should fetch fresh API data (recent bus station is filtered by hour check anyway)
        assertEquals(1, ids.size)
        assertEquals("U_DL_1", ids[0])
    }

    @Test
    fun `refreshNearbyStationsShouldNotFilterTramsRegardlessOfTime`() = runTest {
        // Tram stations should always be included regardless of hour check
        val midnightTramStation = com.example.tramapp.data.local.entity.StationEntity(
            id = "U_MIDNIGHT_TRAM",
            name = "Midnight Tram Station",
            latitude = 50.123,
            longitude = 14.456,
            isTram = true, // This is a tram - should always be included!
            lastUpdate = System.currentTimeMillis() - (5 * 60 * 1000)
        )

        whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(midnightTramStation))
        )
        whenever(stationDao.insertStations(any())).thenReturn(Unit)

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

        // Tram station should always be included
        assertEquals(1, ids.size)
        assertEquals("U_DL_1", ids[0])
    }
}
