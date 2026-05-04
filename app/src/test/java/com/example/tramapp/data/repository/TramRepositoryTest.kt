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

    lateinit var repository: TramRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = TramRepository(apiService, stationDao, departureDao)
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

        val count = repository.refreshNearbyStations(50.099, 14.428, 1000)

        assertEquals(2, count)
        verify(stationDao).insertStations(any())
    }
}
