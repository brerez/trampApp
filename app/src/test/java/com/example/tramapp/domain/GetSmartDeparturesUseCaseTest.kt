package com.example.tramapp.domain

import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.remote.*
import com.example.tramapp.data.repository.TramRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetSmartDeparturesUseCaseTest {

    private val repository = mockk<TramRepository>()
    private val useCase = GetSmartDeparturesUseCase(repository)

    @Test
    fun `execute should return departures immediately`() = runBlocking {
        val stationId = "U123"
        val departures = listOf(createDeparture("8", "A", "trip1", stationId))
        coEvery { repository.getDepartures(stationId) } returns departures

        val result = useCase.execute(stationId, null)

        assertTrue(result.size == 1)
        assertFalse(result[0].isWorkBound)
    }

    @Test
    fun `checkBounds should correctly identify work bound`() = runBlocking {
        mockkStatic(android.location.Location::class)
        every { android.location.Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val results = arg<FloatArray>(4)
            results[0] = 1000f // Distance > 500m
        }
        // Arrange
        val stationId = "U123Z1P"
        val krizikovaName = "Křižíkova"
        val currentLat = 50.0996
        val currentLng = 14.4289

        val preferences = mockk<UserPreferences> {
            coEvery { homeLat } returns null
            coEvery { homeLng } returns null
            coEvery { workLat } returns 50.0906
            coEvery { workLng } returns 14.4533
            coEvery { schoolLat } returns null
            coEvery { schoolLng } returns null
            coEvery { homeStopNames } returns emptySet()
            coEvery { workStopNames } returns setOf(krizikovaName)
            coEvery { schoolStopNames } returns emptySet()
            coEvery { homeStopIds } returns emptySet()
            coEvery { workStopIds } returns setOf("U456Z1P")
            coEvery { schoolStopIds } returns emptySet()
        }

        val tripId = "trip_8_east"
        val departure = createDeparture("8", "Starý Hloubětín", tripId, stationId)

        coEvery { repository.getCachedDirection(any(), any(), any(), any()) } returns null
        coEvery { repository.saveDirection(any(), any(), any(), any(), any()) } just Runs
        coEvery { repository.getTripSequence(any(), any(), any()) } returns listOf(stationId to "Kamenická", "U456Z1P" to "Křižíkova")

        // Act
        val bounds = useCase.checkBounds(departure, "Kamenická", preferences, currentLat, currentLng)

        // Assert
        assertTrue("Should be work bound", bounds.second)
    }

    @Test
    fun `checkBounds should fail for Letenske namesti when IDs mismatch and fallback to name fails`() = runBlocking {
        // Mock Location
        mockkStatic(android.location.Location::class)
        every { android.location.Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val results = arg<FloatArray>(4)
            results[0] = 1000f // Distance > 500m
        }
        
        val currentStopId = "U123Z1P" // Letenské náměstí
        val destStopId = "U456Z1P" // Riverside
        
        val preferences = mockk<UserPreferences> {
            coEvery { homeLat } returns null
            coEvery { homeLng } returns null
            coEvery { workLat } returns null
            coEvery { workLng } returns null
            coEvery { schoolLat } returns 50.1296
            coEvery { schoolLng } returns 14.3987
            coEvery { homeStopNames } returns emptySet()
            coEvery { workStopNames } returns emptySet()
            coEvery { schoolStopNames } returns setOf("Hradčanská")
            coEvery { homeStopIds } returns emptySet()
            coEvery { workStopIds } returns emptySet()
            coEvery { schoolStopIds } returns setOf(destStopId)
        }
        
        val departure = createDeparture("25", "Bílá Hora", "trip25", currentStopId)
        
        coEvery { repository.getCachedDirection(any(), any(), any(), any()) } returns null
        coEvery { repository.saveDirection(any(), any(), any(), any(), any()) } just Runs
        
        // Simulate API returning IDs without 'U' and different format!
        coEvery { repository.getTripSequence(any(), any(), any()) } returns listOf("123" to "Letenské náměstí", "456" to "Hradčanská")
        
        // Mock allStations StateFlow
        val stationList = listOf(
            com.example.tramapp.data.local.entity.StationEntity(currentStopId, "Letenské náměstí [A]", 50.1, 14.4, null, false, 0, true),
            com.example.tramapp.data.local.entity.StationEntity(destStopId, "Hradčanská [A]", 50.2, 14.3, null, false, 0, true)
        )
        val allStationsFlow = kotlinx.coroutines.flow.MutableStateFlow(stationList)
        every { repository.allStations } returns allStationsFlow
        
        val bounds = useCase.checkBounds(departure, "Letenské náměstí", preferences, 50.0996, 14.4289)
        
        assertTrue("Should be school bound", bounds.third)
    }

    private fun createDeparture(line: String, headsign: String, tripId: String, stopId: String): DepartureItem {
        return DepartureItem(
            route = RouteInfo(line, 0),
            trip = TripInfo(headsign, tripId),
            arrival = TimestampInfo("2026-05-05T19:00:00Z", null),
            stop = StopInfo(stopId)
        )
    }
}
