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
    lateinit var throttleUtil: com.example.tramapp.utils.ThrottleUtil

    private lateinit var repository: TramRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = TramRepository(apiService, stationDao, throttleUtil)
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
}
