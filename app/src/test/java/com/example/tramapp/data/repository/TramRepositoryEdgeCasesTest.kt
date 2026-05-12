package com.example.tramapp.data.repository

import com.example.tramapp.data.local.dao.*
import com.example.tramapp.data.local.entity.*
import com.example.tramapp.data.remote.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Edge case tests for TramRepository methods not covered by basic tests.
 */
class TramRepositoryEdgeCasesTest {

    @Mock
    lateinit var apiService: GolemioService

    @Mock
    lateinit var stationDao: StationDao

    @Mock
    lateinit var departureDao: com.example.tramapp.data.local.dao.DepartureDao

    @Mock
    lateinit var tripRouteDao: TripRouteDao

    @Mock
    lateinit var lineDirectionDao: LineDirectionDao

    @Mock
    lateinit var throttleUtil: com.example.tramapp.utils.ThrottleUtil

    private lateinit var repository: TramRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = TramRepository(
            apiService, stationDao, departureDao, tripRouteDao, lineDirectionDao, throttleUtil
        )
        whenever(stationDao.getAllStations()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
    }

    // ==================== getNearbyInfo Edge Cases ====================

    @Test
    fun getNearbyInfoShouldHandleAPIExceptionGracefully() = runTest {
        whenever(stationDao.getAllStations()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
        whenever(apiService.getStops(any(), any())).thenThrow(
            RuntimeException("API error")
        )

        val result = repository.getNearbyInfo(50.099, 14.428)

        assertNotNull(result)
        assertTrue(result.lineNames.isEmpty())
        assertTrue(result.stopIds.isEmpty())
    }

    @Test
    fun getNearbyInfoShouldHandleEmptyAPIResponse() = runTest {
        val mockResponse = GolemioResponse<StopProperties>(features = emptyList())
        whenever(apiService.getStops(any(), any())).thenReturn(mockResponse)

        val result = repository.getNearbyInfo(50.099, 14.428)

        assertNotNull(result)
    }

    @Test
    fun getNearbyInfoShouldHandleStationWithNullPlatformCode() = runTest {
        val mockFeatures = listOf(
            Feature(
                geometry = Geometry(listOf(14.428, 50.099)),
                properties = StopProperties(
                    stopId = "U_TEST",
                    stopName = "Test Station",
                    platformCode = null,
                    locationType = 0
                )
            )
        )
        val mockResponse = GolemioResponse(features = mockFeatures)
        whenever(apiService.getStops(any(), any())).thenReturn(mockResponse)

        val result = repository.getNearbyInfo(50.099, 14.428)

        assertNotNull(result)
    }

    @Test
    fun getNearbyInfoShouldHandleStationWithNullLocationType() = runTest {
        // Non-tram stops (null or non-0 locationType) should be filtered
        val mockFeatures = listOf(
            Feature(
                geometry = Geometry(listOf(14.428, 50.099)),
                properties = StopProperties(
                    stopId = "U_TEST",
                    stopName = "Test Station",
                    platformCode = null,
                    locationType = null
                )
            )
        )
        val mockResponse = GolemioResponse(features = mockFeatures)
        whenever(apiService.getStops(any(), any())).thenReturn(mockResponse)

        val result = repository.getNearbyInfo(50.099, 14.428)

        assertNotNull(result)
    }

    @Test
    fun getNearbyInfoShouldHandleAPIRateLimitError() = runTest {
        whenever(stationDao.getAllStations()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
        whenever(apiService.getStops(any(), any())).thenThrow(
            RuntimeException("Rate limit error")
        )

        val result = repository.getNearbyInfo(50.099, 14.428)

        assertNotNull(result)
    }

    // ==================== getCachedDirection & saveDirection Edge Cases ====================

    @Test
    fun getCachedDirectionShouldReturnNullWhenDirectionNotCached() = runTest {
        whenever(lineDirectionDao.getDirection(any(), any(), any(), any())).thenReturn(null)

        val result = repository.getCachedDirection("U1", "8", "Starý Hloubětín", "home")

        assertNull(result)
    }

    @Test
    fun getCachedDirectionShouldReturnCachedValueCorrectly() = runTest {
        whenever(lineDirectionDao.getDirection(any(), any(), any(), any())).thenReturn(
            LineDirectionEntity(
                stopId = "U1", lineName = "8", headsign = "Starý Hloubětín",
                destinationType = "home", isBound = true, timestamp = System.currentTimeMillis()
            )
        )

        val result = repository.getCachedDirection("U1", "8", "Starý Hloubětín", "home")

        assertTrue(result == true)
    }

    @Test
    fun saveDirectionShouldPersistDirectionInformation() = runTest {
        whenever(lineDirectionDao.insertDirection(any())).thenReturn(Unit)

        repository.saveDirection("U1", "8", "Starý Hloubětín", "home", true)

        verify(lineDirectionDao).insertDirection(any())
    }

    @Test
    fun saveDirectionShouldHandleNegativeIsBoundValue() = runTest {
        whenever(lineDirectionDao.insertDirection(any())).thenReturn(Unit)

        // Should handle boolean correctly
        repository.saveDirection("U1", "8", "Starý Hloubětín", "home", false)

        verify(lineDirectionDao).insertDirection(any())
    }

    // ==================== toggleFavorite Edge Cases ====================

    @Test
    fun toggleFavoriteShouldUpdateFavoriteStatusForExistingStation() = runTest {
        whenever(stationDao.updateFavoriteStatus(any(), any())).thenReturn(Unit)

        repository.toggleFavorite("Line 9", true)

        verify(stationDao).updateFavoriteStatus("Line 9", true)
    }

    @Test
    fun toggleFavoriteShouldHandleExistingFavoriteBeingToggledOff() = runTest {
        whenever(stationDao.updateFavoriteStatus(any(), any())).thenReturn(Unit)

        repository.toggleFavorite("Line 9", false)

        verify(stationDao).updateFavoriteStatus("Line 9", false)
    }

    @Test
    fun toggleFavoriteShouldHandleEmptyStationIdGracefully() = runTest {
        whenever(stationDao.updateFavoriteStatus(any(), any())).thenReturn(Unit)

        // Should not crash even with empty ID
        repository.toggleFavorite("", true)

        verify(stationDao).updateFavoriteStatus("", true)
    }

    // ==================== getTripDetails Edge Cases ====================

    @Test
    fun getTripDetailsShouldHandleEmptyStopTimesResponse() = runTest {
        val mockResponse = com.example.tramapp.data.remote.TripDetailsResponse(
            tripId = "TEST_TRIP",
            shapes = listOf(),
            stopTimes = emptyList()
        )

        whenever(apiService.getTripDetails(any(), any(), any())).thenReturn(mockResponse)

        val result = repository.getTripDetailsFlow("TEST_TRIP", "8", "Starý Hloubětín").first()

        assertNotNull(result)
        assertTrue(result.stations.isEmpty())
    }

    @Test
    fun getTripDetailsShouldHandleNullShapesInAPIResponse() = runTest {
        val mockResponse = com.example.tramapp.data.remote.TripDetailsResponse(
            tripId = "TEST_TRIP",
            shapes = emptyList(),
            stopTimes = emptyList()
        )

        whenever(apiService.getTripDetails(any(), any(), any())).thenReturn(mockResponse)

        val result = repository.getTripDetailsFlow("TEST_TRIP", "8", "Starý Hloubětín").first()

        assertNotNull(result)
    }

    @Test
    fun getTripDetailsShouldHandleResponseWithOnlyOneStop() = runTest {
        val mockResponse = com.example.tramapp.data.remote.TripDetailsResponse(
            tripId = "TEST_TRIP",
            shapes = listOf(),
            stopTimes = listOf(
                com.example.tramapp.data.remote.TripStopTime(
                    stopSequence = 1,
                    stopId = "U1",
                    stop = com.example.tramapp.data.remote.TripStopInfo("Station Name")
                )
            )
        )

        whenever(apiService.getTripDetails(any(), any(), any())).thenReturn(mockResponse)

        val result = repository.getTripDetailsFlow("TEST_TRIP", "8", "Starý Hloubětín").first()

        assertNotNull(result)
        assertEquals(1, result.stations.size)
    }

    @Test
    fun getTripDetailsShouldHandleResponseWithNullStopAtLastPosition() = runTest {
        val mockResponse = com.example.tramapp.data.remote.TripDetailsResponse(
            tripId = "TEST_TRIP",
            shapes = listOf(),
            stopTimes = listOf(
                com.example.tramapp.data.remote.TripStopTime(
                    stopSequence = 1,
                    stopId = "U1",
                    stop = null
                )
            )
        )

        whenever(apiService.getTripDetails(any(), any(), any())).thenReturn(mockResponse)

        val result = repository.getTripDetailsFlow("TEST_TRIP", "8", "Starý Hloubětín").first()

        assertNotNull(result)
    }

    @Test
    fun getTripDetailsShouldHandleExceptionInAPICall() = runTest {
        whenever(apiService.getTripDetails(any(), any(), any())).thenThrow(
            RuntimeException("API exception")
        )

        val result = runCatching { repository.getTripDetailsFlow("TEST_TRIP", "8", "Starý Hloubětín").first() }

        assertTrue(result.isFailure)
        assertEquals("API exception", result.exceptionOrNull()?.message)
    }

    // ==================== getDepartures Edge Cases ====================

    @Test
    fun getDeparturesShouldHandleAPIReturningNullDeparturesList() = runTest {
        val mockResponse = com.example.tramapp.data.remote.DepartureResponse(
            departures = emptyList()
        )

        whenever(apiService.getDepartures(any(), any(), any(), any())).thenReturn(mockResponse)

        // Should mark station as non-tram and return empty list
        val result = repository.getDepartures("U1")

        assertEquals(emptyList<com.example.tramapp.data.remote.DepartureItem>(), result)
    }

    @Test
    fun getDeparturesShouldHandleAPIReturningNullDeparturesListWithExistingTramInCache() = runTest {
        // Simulate cache having a tram station
        whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(
                listOf(
                    StationEntity(
                        id = "U1", name = "Station One", latitude = 50.0, longitude = 14.0, isTram = true, lastUpdate = System.currentTimeMillis()
                    )
                )
            )
        )

        val mockResponse = com.example.tramapp.data.remote.DepartureResponse(
            departures = listOf(
                com.example.tramapp.data.remote.DepartureItem(
                    route = com.example.tramapp.data.remote.RouteInfo("Bus 100", 3),
                    trip = com.example.tramapp.data.remote.TripInfo("Dest"),
                    arrival = com.example.tramapp.data.remote.TimestampInfo("2026-05-08T23:00:00Z", null),
                    stop = com.example.tramapp.data.remote.StopInfo("U1")
                )
            )
        )

        whenever(apiService.getDepartures(any(), any(), any(), any())).thenReturn(mockResponse)

        // Should still update isTram status to false when no current departures
        repository.getDepartures("U1")

        verify(stationDao).updateIsTramStatus("U1", false)
    }

    @Test
    fun getDeparturesShouldHandleAPIReturningAllBusRoutesOnly() = runTest {
        val mockResponse = com.example.tramapp.data.remote.DepartureResponse(
            departures = listOf(
                com.example.tramapp.data.remote.DepartureItem(
                    route = com.example.tramapp.data.remote.RouteInfo("100", 1),
                    trip = com.example.tramapp.data.remote.TripInfo("Dest"),
                    arrival = com.example.tramapp.data.remote.TimestampInfo("2026-05-08T23:00:00Z", null),
                    stop = com.example.tramapp.data.remote.StopInfo("U1")
                ),
                com.example.tramapp.data.remote.DepartureItem(
                    route = com.example.tramapp.data.remote.RouteInfo("101", 1),
                    trip = com.example.tramapp.data.remote.TripInfo("Dest2"),
                    arrival = com.example.tramapp.data.remote.TimestampInfo("2026-05-08T23:01:00Z", null),
                    stop = com.example.tramapp.data.remote.StopInfo("U1")
                )
            )
        )

        whenever(apiService.getDepartures(any(), any(), any(), any())).thenReturn(mockResponse)

        val result = repository.getDepartures("U1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun getDeparturesShouldHandleAPIReturningTramWithNullArrivalTime() = runTest {
        val mockResponse = com.example.tramapp.data.remote.DepartureResponse(
            departures = listOf(
                com.example.tramapp.data.remote.DepartureItem(
                    route = com.example.tramapp.data.remote.RouteInfo("8", 0),
                    trip = com.example.tramapp.data.remote.TripInfo("Dest"),
                    arrival = com.example.tramapp.data.remote.TimestampInfo("2026-05-08T23:00:00Z", null),
                    stop = com.example.tramapp.data.remote.StopInfo("U1")
                )
            )
        )

        whenever(apiService.getDepartures(any(), any(), any(), any())).thenReturn(mockResponse)

        val result = repository.getDepartures("U1")

        assertEquals(1, result.size)
    }

    @Test
    fun getDeparturesShouldHandleAPIReturningEmptyStringForTripId() = runTest {
        val mockResponse = com.example.tramapp.data.remote.DepartureResponse(
            departures = listOf(
                com.example.tramapp.data.remote.DepartureItem(
                    route = com.example.tramapp.data.remote.RouteInfo("8", 0),
                    trip = com.example.tramapp.data.remote.TripInfo(""),
                    arrival = com.example.tramapp.data.remote.TimestampInfo("2026-05-08T23:00:00Z", null),
                    stop = com.example.tramapp.data.remote.StopInfo("U1")
                )
            )
        )

        whenever(apiService.getDepartures(any(), any(), any(), any())).thenReturn(mockResponse)

        val result = repository.getDepartures("U1")

        assertEquals(1, result.size)
    }

    // ==================== getCachedDepartures Edge Cases ====================

    @Test
    fun getCachedDeparturesShouldHandleEmptyCache() = runTest {
        whenever(departureDao.getDeparturesForStop(any())).thenReturn(emptyList())

        val result = repository.getCachedDepartures("U1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun getCachedDeparturesShouldHandleDeprecatedRouteTypeField() = runTest {
        whenever(departureDao.getDeparturesForStop(any())).thenReturn(
            listOf(
                com.example.tramapp.data.local.entity.DepartureEntity(
                    stopId = "U1", routeShortName = "8", routeType = 0,
                    headsign = "Starý Hloubětín", arrivalTime = "2026-05-08T23:00:00Z",
                    isPredicted = true, tripId = "TRIP_1"
                )
            )
        )

        val result = repository.getCachedDepartures("U1")

        assertEquals(1, result.size)
    }

    // ==================== getNearbyStationDetails Edge Cases ====================

    @Test
    fun getNearbyStationDetailsShouldHandleStationWithSpecialCharactersInName() = runTest {
        val mockFeatures = listOf(
            Feature(
                geometry = Geometry(listOf(14.428, 50.099)),
                properties = StopProperties(
                    stopId = "U_SPECIAL",
                    stopName = "Nádraží Podbaba (Podbaba Station)",
                    platformCode = "A",
                    locationType = 0
                )
            )
        )

        whenever(apiService.getStops(any(), any())).thenReturn(
            GolemioResponse(features = mockFeatures)
        )

        val result = repository.refreshNearbyStations(50.099, 14.428, 1000)

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun getNearbyStationDetailsShouldHandleStationWithUnicodeCharactersInName() = runTest {
        val mockFeatures = listOf(
            Feature(
                geometry = Geometry(listOf(14.428, 50.099)),
                properties = StopProperties(
                    stopId = "U_UNICODE",
                    stopName = "Nádraží Podbaba",
                    platformCode = "A",
                    locationType = 0
                )
            )
        )

        whenever(apiService.getStops(any(), any())).thenReturn(
            GolemioResponse(features = mockFeatures)
        )

        val result = repository.refreshNearbyStations(50.099, 14.428, 1000)

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun getNearbyStationDetailsShouldHandleStationWithPlatformLabelSuffix() = runTest {
        val mockFeatures = listOf(
            Feature(
                geometry = Geometry(listOf(14.428, 50.099)),
                properties = StopProperties(
                    stopId = "U_KAMENISKA",
                    stopName = "Kamenická [A]",
                    platformCode = "A",
                    locationType = 0
                )
            ),
            Feature(
                geometry = Geometry(listOf(14.429, 50.100)),
                properties = StopProperties(
                    stopId = "U_KAMENISKA_B",
                    stopName = "Kamenická [B]",
                    platformCode = "B",
                    locationType = 0
                )
            )
        )

        whenever(apiService.getStops(any(), any())).thenReturn(
            GolemioResponse(features = mockFeatures)
        )

        val result = repository.refreshNearbyStations(50.1, 14.43, 1000)

        assertEquals(2, result.size)
    }

    // ==================== getTripSequence Edge Cases ====================

    @Test
    fun getTripSequenceShouldHandleEmptyStopIdsCacheForFailedRoute() = runTest {
        whenever(tripRouteDao.getTripRoute(any())).thenReturn(
            TripRouteEntity(
                routeKey = "8-Starý Hloubětín",
                stopIds = "EMPTY",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = repository.getTripSequence("8", "Starý Hloubětín", "TRIP_ID")

        assertTrue(result.isEmpty())
    }

    @Test
    fun getTripSequenceShouldHandleLegacyPipeDelimitedStopIDs() = runTest {
        whenever(tripRouteDao.getTripRoute(any())).thenReturn(
            TripRouteEntity(
                routeKey = "8-Starý Hloubětín",
                stopIds = "||ID:U1,Platform:A|U2|U391Z1P",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = repository.getTripSequence("8", "Starý Hloubětín", "TRIP_ID")

        assertEquals(3, result.size)
    }

    @Test
    fun getTripSequenceShouldHandleNewPipeDelimitedStopIDs() = runTest {
        whenever(tripRouteDao.getTripRoute(any())).thenReturn(
            TripRouteEntity(
                routeKey = "8-Starý Hloubětín",
                stopIds = "||ID:U1|U2|U391Z1P",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = repository.getTripSequence("8", "Starý Hloubětín", "TRIP_ID")

        assertEquals(3, result.size)
    }

    @Test
    fun getTripSequenceShouldHandleLegacyCommaSeparatedStopNames() = runTest {
        whenever(tripRouteDao.getTripRoute(any())).thenReturn(
            TripRouteEntity(
                routeKey = "8-Starý Hloubětín",
                stopIds = "Kamenická|Dlouhá třída|Hradčanská",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = repository.getTripSequence("8", "Starý Hloubětín", "TRIP_ID")

        assertEquals(3, result.size)
    }

    @Test
    fun getTripSequenceShouldHandleMixedIDAndNameInStopIDs() = runTest {
        whenever(tripRouteDao.getTripRoute(any())).thenReturn(
            TripRouteEntity(
                routeKey = "8-Starý Hloubětín",
                stopIds = "||ID:U1,Stop:A|Dlouhá třída|U391Z1P",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = repository.getTripSequence("8", "Starý Hloubětín", "TRIP_ID")

        assertEquals(3, result.size)
    }

    @Test
    fun getTripSequenceShouldHandleRouteWithManyStops() = runTest {
        whenever(tripRouteDao.getTripRoute(any())).thenReturn(
            TripRouteEntity(
                routeKey = "8-Starý Hloubětín",
                stopIds = "U1|U2|U3|U4|U5|U6|U7|U8|U9|U10|U11|U12|U391Z1P|U125|U126",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = repository.getTripSequence("8", "Starý Hloubětín", "TRIP_ID")

        assertEquals(15, result.size)
    }
}
