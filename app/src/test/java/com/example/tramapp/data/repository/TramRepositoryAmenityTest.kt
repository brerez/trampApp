package com.example.tramapp.data.repository

import com.example.tramapp.data.local.dao.LineDirectionDao
import com.example.tramapp.data.local.dao.StationDao
import com.example.tramapp.data.local.dao.TripRouteDao
import com.example.tramapp.data.local.entity.DepartureEntity
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.DepartureResponse
import com.example.tramapp.data.remote.GolemioService
import com.example.tramapp.data.remote.RouteInfo
import com.example.tramapp.data.remote.StopInfo
import com.example.tramapp.data.remote.TimestampInfo
import com.example.tramapp.data.remote.TripInfo
import com.example.tramapp.utils.ThrottleUtil
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever

/**
 * U8 (R7-R9): amenity fields parse present/absent, round-trip through the Room cache
 * (including null), and survive the version-8→9 schema bump.
 */
class TramRepositoryAmenityTest {

    @Mock lateinit var apiService: GolemioService
    @Mock lateinit var stationDao: StationDao
    @Mock lateinit var departureDao: com.example.tramapp.data.local.dao.DepartureDao
    @Mock lateinit var tripRouteDao: TripRouteDao
    @Mock lateinit var lineDirectionDao: LineDirectionDao
    @Mock lateinit var throttleUtil: ThrottleUtil

    private lateinit var repository: TramRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = TramRepository(
            apiService, stationDao, departureDao, tripRouteDao, lineDirectionDao, throttleUtil
        )
    }

    private fun departureItem(
        isAccessible: Boolean?,
        isAirConditioned: Boolean?
    ) = DepartureItem(
        route = RouteInfo("9", 0),
        trip = TripInfo("Dest 9", "trip-1", isAccessible, isAirConditioned),
        arrival = TimestampInfo(java.time.OffsetDateTime.now().plusMinutes(5).toString(), null),
        stop = StopInfo("U1")
    )

    @Test
    fun `parse present amenity fields deserializes to non-null flags`() = runTest {
        whenever(apiService.getDepartures(any(), any(), any(), any(), any()))
            .thenReturn(DepartureResponse(listOf(departureItem(isAccessible = true, isAirConditioned = false))))

        val deps = repository.getDepartures("U1")

        assertEquals(true, deps.single().trip.isWheelchairAccessible)
        assertEquals(false, deps.single().trip.isAirConditioned)
    }

    @Test
    fun `parse absent amenity fields yields null, not a default false`() = runTest {
        whenever(apiService.getDepartures(any(), any(), any(), any(), any()))
            .thenReturn(DepartureResponse(listOf(departureItem(isAccessible = null, isAirConditioned = null))))

        val deps = repository.getDepartures("U1")

        assertNull(deps.single().trip.isWheelchairAccessible)
        assertNull(deps.single().trip.isAirConditioned)
    }

    @Test
    fun `cache round-trip preserves amenity flags including null`() = runTest {
        whenever(apiService.getDepartures(any(), any(), any(), any(), any())).thenReturn(
            DepartureResponse(listOf(
                departureItem(isAccessible = true, isAirConditioned = true),
                departureItem(isAccessible = null, isAirConditioned = false)
            ))
        )

        val captor = argumentCaptor<List<DepartureEntity>>()
        repository.getDepartures("U1")
        org.mockito.kotlin.verify(departureDao).insertDepartures(captor.capture())
        val savedEntities = captor.firstValue

        assertEquals(2, savedEntities.size)
        assertEquals(true, savedEntities[0].isAccessible)
        assertEquals(true, savedEntities[0].isAirConditioned)
        assertNull(savedEntities[1].isAccessible)
        assertEquals(false, savedEntities[1].isAirConditioned)

        // Reading back: getCachedDepartures maps DepartureEntity -> DepartureItem, preserving nulls.
        whenever(departureDao.getDeparturesForStop("U1")).thenReturn(savedEntities)
        val cached = repository.getCachedDepartures("U1")

        assertEquals(true, cached[0].trip.isWheelchairAccessible)
        assertEquals(true, cached[0].trip.isAirConditioned)
        assertNull(cached[1].trip.isWheelchairAccessible)
        assertEquals(false, cached[1].trip.isAirConditioned)
    }

    @Test
    fun `migration bump — DepartureEntity default-constructs with null amenity flags for pre-U8 rows`() {
        // Simulates a row written before U8 (no amenity columns) surviving the destructive
        // version 8->9 bump (this repo's established convention for schema changes — see
        // DatabaseModule's fallbackToDestructiveMigration; departures are an ephemeral cache,
        // not precious user data, so a rebuild-on-bump is the intended, low-risk path here).
        val legacyRow = DepartureEntity(
            stopId = "U1",
            routeShortName = "9",
            routeType = 0,
            headsign = "Dest 9",
            arrivalTime = java.time.OffsetDateTime.now().toString(),
            isPredicted = false,
            tripId = "trip-1"
            // isAccessible / isAirConditioned omitted -> default null
        )

        assertNull(legacyRow.isAccessible)
        assertNull(legacyRow.isAirConditioned)
    }
}
