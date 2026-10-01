package com.example.tramapp.data.repository

import com.example.tramapp.data.local.dao.StationDao
import com.example.tramapp.data.remote.GolemioService
import com.example.tramapp.utils.ThrottleUtil
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder

/**
 * U3 — junction batch fetch (`TramRepository.getJunctionDepartures`) using a real
 * Retrofit + Gson GolemioService pointed at a MockWebServer, so the actual HTTP
 * request shape (ids[] repeated, limit, minutesAfter) is verified, not just the
 * parsed result.
 */
class TramRepositoryBatchTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: GolemioService

    @Mock
    lateinit var stationDao: StationDao

    private lateinit var throttleUtil: ThrottleUtil

    lateinit var repository: TramRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(GolemioService::class.java)

        throttleUtil = ThrottleUtil()
        repository = TramRepository(apiService, stationDao, throttleUtil)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun requestedIds(path: String): List<String> {
        // path looks like: /pid/departureboards?ids%5B%5D=A&ids%5B%5D=B&limit=200...
        val query = path.substringAfter('?')
        return query.split('&')
            .map { it.split('=', limit = 2) }
            .filter { it[0] == "ids%5B%5D" || URLDecoder.decode(it[0], "UTF-8") == "ids[]" }
            .map { URLDecoder.decode(it.getOrElse(1) { "" }, "UTF-8") }
    }

    private fun queryParam(path: String, name: String): String? {
        val query = path.substringAfter('?')
        return query.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { URLDecoder.decode(it[0], "UTF-8") == name }
            ?.getOrNull(1)
    }

    @Test
    fun `returns departures keyed by platform id and sends one batched request`() = runTest {
        val platforms = listOf("U100Z1P", "U100Z2P", "U100Z3P", "U200Z1P", "U200Z2P", "U200Z3P")
        val body = """
            {
              "departures": [
                { "route": {"short_name":"1","type":0}, "trip": {"headsign":"A"}, "arrival_timestamp": {"scheduled":"2026-01-01T00:00:00Z","predicted":null}, "stop": {"id":"U100Z1P"} },
                { "route": {"short_name":"2","type":0}, "trip": {"headsign":"B"}, "arrival_timestamp": {"scheduled":"2026-01-01T00:01:00Z","predicted":null}, "stop": {"id":"U100Z2P"} },
                { "route": {"short_name":"9","type":3}, "trip": {"headsign":"C"}, "arrival_timestamp": {"scheduled":"2026-01-01T00:02:00Z","predicted":null}, "stop": {"id":"U200Z1P"} }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = repository.getJunctionDepartures(platforms)

        // Every requested id present as a key.
        assertEquals(platforms.toSet(), result.keys)
        assertEquals(1, result["U100Z1P"]!!.size)
        assertEquals(1, result["U100Z2P"]!!.size)
        assertTrue(result["U100Z3P"]!!.isEmpty())
        // Bus-only departure at U200Z1P is filtered out of the tram result.
        assertTrue(result["U200Z1P"]!!.isEmpty())
        assertTrue(result["U200Z2P"]!!.isEmpty())
        assertTrue(result["U200Z3P"]!!.isEmpty())

        assertEquals(1, server.requestCount)
        val recorded = server.takeRequest()
        assertEquals(platforms, requestedIds(recorded.path!!))
        assertEquals("200", queryParam(recorded.path!!, "limit"))
        assertEquals("60", queryParam(recorded.path!!, "minutesAfter"))
    }

    @Test
    fun `updates isTram true for platform with a tram and false for bus-only platform`() = runTest {
        val platforms = listOf("U300Z1P", "U300Z2P")
        val body = """
            {
              "departures": [
                { "route": {"short_name":"1","type":0}, "trip": {"headsign":"A"}, "arrival_timestamp": {"scheduled":"2026-01-01T00:00:00Z","predicted":null}, "stop": {"id":"U300Z1P"} },
                { "route": {"short_name":"9","type":3}, "trip": {"headsign":"C"}, "arrival_timestamp": {"scheduled":"2026-01-01T00:02:00Z","predicted":null}, "stop": {"id":"U300Z2P"} }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        repository.getJunctionDepartures(platforms)

        verify(stationDao).updateIsTramStatus("U300Z1P", true)
        verify(stationDao).updateIsTramStatus("U300Z2P", false)
    }

    @Test
    fun `parses missing optional fields as null and present ones to correct values`() = runTest {
        val platforms = listOf("U400Z1P", "U400Z2P")
        val body = """
            {
              "departures": [
                { "route": {"short_name":"1","type":0}, "trip": {"headsign":"A"}, "arrival_timestamp": {"scheduled":"2026-01-01T00:00:00Z","predicted":null}, "stop": {"id":"U400Z1P"} },
                {
                  "route": {"short_name":"2","type":0},
                  "trip": {"headsign":"B","is_canceled":true,"is_at_stop":true},
                  "arrival_timestamp": {"scheduled":"2026-01-01T00:01:00Z","predicted":null},
                  "stop": {"id":"U400Z2P","platform_code":"B"},
                  "delay": {"is_available":true,"minutes":3,"seconds":180},
                  "last_stop": {"id":"U999","name":"Terminus"}
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = repository.getJunctionDepartures(platforms)

        val first = result["U400Z1P"]!!.single()
        assertNull(first.delay)
        assertNull(first.lastStop)
        assertNull(first.trip.isCanceled)
        assertNull(first.trip.isAtStop)
        assertNull(first.stop.platformCode)

        val second = result["U400Z2P"]!!.single()
        assertEquals(true, second.trip.isCanceled)
        assertEquals(true, second.trip.isAtStop)
        assertEquals("B", second.stop.platformCode)
        assertEquals(true, second.delay?.isAvailable)
        assertEquals(3, second.delay?.minutes)
        assertEquals(180, second.delay?.seconds)
        assertEquals("U999", second.lastStop?.id)
        assertEquals("Terminus", second.lastStop?.name)
    }

    @Test
    fun `retries once after a 429 and returns the result from the retry`() = runTest {
        val platforms = listOf("U500Z1P")
        server.enqueue(MockResponse().setResponseCode(429))
        val body = """
            {
              "departures": [
                { "route": {"short_name":"1","type":0}, "trip": {"headsign":"A"}, "arrival_timestamp": {"scheduled":"2026-01-01T00:00:00Z","predicted":null}, "stop": {"id":"U500Z1P"} }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = repository.getJunctionDepartures(platforms)

        assertEquals(1, result["U500Z1P"]!!.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `quiet unclassified platforms are probed three days ahead and classified`() = runTest {
        fun station(id: String, isTram: Boolean?) = com.example.tramapp.data.local.entity.StationEntity(
            id = id, name = id, latitude = 50.0, longitude = 14.0, lastUpdate = 0L, isTram = isTram,
            nodeId = id.substringBefore('Z'), platformCode = null,
        )
        org.mockito.kotlin.whenever(stationDao.getAllStations()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(station("U400Z1P", null), station("U400Z2P", true)))
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"departures": []}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"departures": [
              { "route": {"short_name":"136","type":3}, "trip": {"headsign":"X"}, "arrival_timestamp": {"scheduled":"2026-01-01T05:00:00Z","predicted":null}, "stop": {"id":"U400Z1P"} }
            ]}
        """.trimIndent()))

        repository.getJunctionDepartures(listOf("U400Z1P", "U400Z2P"))

        assertEquals(2, server.requestCount)
        server.takeRequest()
        val probe = server.takeRequest()
        // Only the unclassified platform is probed; the known tram platform isn't.
        assertEquals(listOf("U400Z1P"), requestedIds(probe.path!!))
        assertEquals("4320", queryParam(probe.path!!, "minutesAfter"))
        verify(stationDao).updateIsTramStatus("U400Z1P", false)
    }
}
