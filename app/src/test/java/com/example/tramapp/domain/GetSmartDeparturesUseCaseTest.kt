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
        coEvery { repository.getTripSequence(any(), any(), any()) } returns listOf(stationId, "U456Z1P")

        // Act
        val bounds = useCase.checkBounds(departure, "Kamenická", preferences, currentLat, currentLng)

        // Assert
        assertTrue("Should be work bound", bounds.second)
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
