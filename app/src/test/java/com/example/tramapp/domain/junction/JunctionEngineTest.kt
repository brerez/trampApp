package com.example.tramapp.domain.junction

import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.RouteInfo
import com.example.tramapp.data.remote.StopInfo
import com.example.tramapp.data.remote.TimestampInfo
import com.example.tramapp.data.remote.TripInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

// ===========================================================
// Fakes
// ===========================================================

class FakeDepartureSource(
    private val result: Map<String, List<DepartureItem>> = emptyMap(),
    private val shouldThrow: Boolean = false
) : JunctionDepartureSource {
    var callCount: Int = 0
    var lastIds: List<String> = emptyList()

    override suspend fun getJunctionDepartures(platformIds: List<String>): Map<String, List<DepartureItem>> {
        callCount++
        lastIds = platformIds
        if (shouldThrow) throw RuntimeException("Network error")
        return platformIds.associateWith { result[it].orEmpty() }
    }
}

/**
 * A [JunctionDepartureSource] whose result can be swapped between calls.
 */
class MutableFakeDepartureSource : JunctionDepartureSource {
    var nextResult: Map<String, List<DepartureItem>> = emptyMap()
    var nextThrow: Exception? = null
    val callCount = AtomicInteger(0)
    var lastIds: List<String> = emptyList()

    override suspend fun getJunctionDepartures(platformIds: List<String>): Map<String, List<DepartureItem>> {
        callCount.incrementAndGet()
        lastIds = platformIds
        nextThrow?.let { throw it }
        return platformIds.associateWith { nextResult[it].orEmpty() }
    }
}

class FakeNextStopLookup(
    private val cachedResult: Map<String, Map<String, TripNextStop>> = emptyMap(),
    private val resolvedResult: Map<String, Map<String, TripNextStop>> = emptyMap()
) : NextStopLookup {
    override suspend fun cachedAll(
        departuresByPlatform: Map<String, List<DepartureItem>>
    ): Map<String, Map<String, TripNextStop>> = cachedResult

    override suspend fun resolveAll(
        departuresByPlatform: Map<String, List<DepartureItem>>
    ): Map<String, Map<String, TripNextStop>> = resolvedResult
}

/**
 * A NextStopLookup whose resolveAll suspends on a [gate] deferred.
 * Call [gate].complete(Unit) to unblock resolution.
 * [resolvedResult] contains entries that are returned once gate fires.
 */
class DeferredNextStopLookup(
    private val gate: CompletableDeferred<Unit>,
    private val cachedResult: Map<String, Map<String, TripNextStop>> = emptyMap(),
    private val resolvedResult: Map<String, Map<String, TripNextStop>> = emptyMap()
) : NextStopLookup {
    override suspend fun cachedAll(
        departuresByPlatform: Map<String, List<DepartureItem>>
    ): Map<String, Map<String, TripNextStop>> = cachedResult

    override suspend fun resolveAll(
        departuresByPlatform: Map<String, List<DepartureItem>>
    ): Map<String, Map<String, TripNextStop>> {
        gate.await()
        return resolvedResult
    }
}

class FakeDestinationNodeSource : DestinationNodeSource {
    var nodes: DestinationNodes = DestinationNodes.NONE
    override suspend fun current(): DestinationNodes = nodes
}

// ===========================================================
// Helpers
// ===========================================================

private const val ENGINE_NOW_MS = 1_758_888_000_000L

private fun isoTime(offsetMinutes: Int): String {
    val epochMs = ENGINE_NOW_MS + offsetMinutes * 60_000L
    return java.time.Instant.ofEpochMilli(epochMs)
        .atOffset(java.time.ZoneOffset.UTC)
        .toString()
}

private fun makePlatformDep(
    tripId: String,
    platformStopId: String,
    minuteOffset: Int = 5
) = DepartureItem(
    route = RouteInfo("1", 0),
    trip = TripInfo(headsign = "End Stop", tripId = tripId),
    arrival = TimestampInfo(scheduled = isoTime(minuteOffset), predicted = null),
    stop = StopInfo(id = platformStopId)
)

private fun singlePlatformJunction(stopId: String = "U163Z1P", letter: String = "A") = Junction(
    nodeId = "U163",
    name = "Hradcanska",
    platforms = listOf(Platform(stopId, letter, GeoPoint(50.091, 14.393), true))
)

private fun buildEngine(
    departures: JunctionDepartureSource,
    nextStops: NextStopLookup = FakeNextStopLookup(),
    destinations: DestinationNodeSource = FakeDestinationNodeSource(),
    fakeClock: () -> Long = { ENGINE_NOW_MS }
): JunctionEngine = JunctionEngine(departures, nextStops, destinations).also { it.clock = fakeClock }

// ===========================================================
// Tests
// ===========================================================

class JunctionEngineTest {

    // ----------------------------------------------------------
    // Test 8: refresh emits structure (rows==null, LOADING) before rows (LIVE)
    // ----------------------------------------------------------

    @Test
    fun test8_refreshEmitsLoadingStructureBeforeLiveRows() = runTest {
        val junction = singlePlatformJunction()
        val depSource = FakeDepartureSource(
            result = mapOf("U163Z1P" to listOf(makePlatformDep("trip-1", "U163Z1P")))
        )

        val engine = buildEngine(depSource)

        // Collect snapshot flow values as they arrive. The engine performs its work
        // synchronously (no real suspension), so the collector must be attached and
        // running BEFORE show()/refresh() are invoked, or intermediate states (the
        // StateFlow is conflated) would be missed.
        val collected = mutableListOf<JunctionSnapshot?>()
        val collectJob = launch {
            engine.snapshot(junction.nodeId).collect { collected.add(it) }
        }
        runCurrent() // let the collector attach and receive the initial null value

        // show() emits LOADING structure
        engine.show(junction)
        runCurrent()
        // refresh() emits LIVE
        engine.refresh(junction)
        runCurrent()

        collectJob.cancel()

        val nonNull = collected.filterNotNull()
        assertTrue(
            "Should have at least one LOADING state (rows==null) before LIVE",
            nonNull.any { it.state == SnapshotState.LOADING && it.rows == null }
        )
        assertTrue(
            "Should have a LIVE state with rows",
            nonNull.any { it.state == SnapshotState.LIVE && it.rows != null }
        )
    }

    // ----------------------------------------------------------
    // Test 9: coalescing — two calls within 5s → ONE network call
    //         after 5s → second call; force=true bypasses
    // ----------------------------------------------------------

    @Test
    fun test9_twoRefreshesWithinCoalesceWindowCauseOneNetworkCall_afterWindowSecondCall_forceBypasses() = runTest {
        val junction = singlePlatformJunction()
        var fakeNow = ENGINE_NOW_MS
        val depSource = MutableFakeDepartureSource()
        val engine = buildEngine(depSource, fakeClock = { fakeNow })

        // First refresh
        engine.refresh(junction)
        assertEquals("First refresh must make one network call", 1, depSource.callCount.get())

        // Second refresh within COALESCE_MS (same clock time) → coalesced
        engine.refresh(junction)
        assertEquals("Second refresh within 5s must be coalesced (still 1 call)", 1, depSource.callCount.get())

        // Advance clock past COALESCE_MS
        fakeNow += JunctionEngine.COALESCE_MS + 1

        // Third refresh after 5s → new network call
        engine.refresh(junction)
        assertEquals("Refresh after COALESCE_MS must issue a second call", 2, depSource.callCount.get())

        // force=true bypasses coalescing regardless of timing
        engine.refresh(junction, force = true)
        assertEquals("force=true must bypass coalescing", 3, depSource.callCount.get())
    }

    // ----------------------------------------------------------
    // Test 10: network failure keeps previous rows, sets ERROR with lastErrorAtMs; never throws
    // ----------------------------------------------------------

    @Test
    fun test10_networkFailureAfterSuccessKeesPreviousRowsSetsError() = runTest {
        val junction = singlePlatformJunction()
        var fakeNow = ENGINE_NOW_MS
        val depSource = MutableFakeDepartureSource()
        depSource.nextResult = mapOf("U163Z1P" to listOf(makePlatformDep("trip-1", "U163Z1P")))

        val engine = buildEngine(depSource, fakeClock = { fakeNow })

        // First refresh succeeds
        engine.refresh(junction)
        val liveSnapshot = engine.snapshot(junction.nodeId).first()
        assertNotNull("Should have rows after successful refresh", liveSnapshot?.rows)
        assertEquals(SnapshotState.LIVE, liveSnapshot?.state)
        val previousRows      = liveSnapshot?.rows
        val previousFetchedAt = liveSnapshot?.fetchedAtMs

        // Advance clock and make next call fail
        fakeNow += JunctionEngine.COALESCE_MS + 1
        depSource.nextThrow = RuntimeException("Network failure")

        // Refresh must NOT throw
        engine.refresh(junction)

        val errorSnapshot = engine.snapshot(junction.nodeId).first()
        assertEquals("State must be ERROR",            SnapshotState.ERROR, errorSnapshot?.state)
        assertEquals("Previous rows must be kept",     previousRows,        errorSnapshot?.rows)
        assertEquals("Previous fetchedAtMs must be kept", previousFetchedAt, errorSnapshot?.fetchedAtMs)
        assertNotNull("lastErrorAtMs must be set",     errorSnapshot?.lastErrorAtMs)
    }

    // ----------------------------------------------------------
    // Test 11: 6-platform junction → exactly ONE getJunctionDepartures call with all 6 ids (R19)
    // ----------------------------------------------------------

    @Test
    fun test11_sixPlatformJunctionIssuedExactlyOneNetworkCallWithAllSixIds() = runTest {
        val platformIds = (1..6).map { "U163Z${it}P" }
        val platforms = platformIds.mapIndexed { i, id ->
            Platform(id, "${'A' + i}", GeoPoint(50.0 + i, 14.0), true)
        }
        val junction = Junction("U163", "Hradcanska", platforms)

        val depSource = MutableFakeDepartureSource()
        val engine = buildEngine(depSource)

        engine.refresh(junction)

        assertEquals("Exactly 1 network call for 6-platform junction", 1, depSource.callCount.get())
        assertEquals("All 6 platform ids passed in one call", platformIds.toSet(), depSource.lastIds.toSet())
    }

    // ----------------------------------------------------------
    // Test 12: rows first appear with end-stop fallback, then re-emit once resolveAll returns
    // ----------------------------------------------------------

    @Test
    fun test12_rowsFirstWithFallbackThenReemittedAfterResolveAllReturns() = runTest {
        val junction = singlePlatformJunction()
        val gate = CompletableDeferred<Unit>()

        val dep = makePlatformDep("trip-resolve", "U163Z1P", minuteOffset = 5)
        val depMap = mapOf("U163Z1P" to listOf(dep))

        val resolvedStop = TripNextStop(
            tripId = "trip-resolve",
            platformStopId = "U163Z1P",
            nextStopId = "U200Z1P",
            nextStopName = "Resolved Next Stop",
            downstreamNodeIds = emptySet()
        )
        // cachedAll returns nothing; resolveAll suspends until gate completes
        val lookup = DeferredNextStopLookup(
            gate = gate,
            cachedResult = emptyMap(),
            resolvedResult = mapOf("U163Z1P" to mapOf("trip-resolve" to resolvedStop))
        )

        val depSource = FakeDepartureSource(result = depMap)
        val engine = buildEngine(depSource, nextStops = lookup)

        // Launch refresh concurrently so we can observe intermediate emission
        val refreshJob = async {
            engine.refresh(junction)
        }

        // Wait until we see a LIVE snapshot with rows (emitted after cachedAll, before resolveAll completes)
        withTimeout(5_000) {
            while (true) {
                val snap = engine.snapshot(junction.nodeId).first()
                if (snap?.state == SnapshotState.LIVE && snap.rows != null) break
                delay(10)
            }
        }

        // Verify intermediate state: fallback row (isResolved=false, headsign label)
        val firstLive = engine.snapshot(junction.nodeId).first()
        assertNotNull("Should have LIVE snapshot before resolveAll completes", firstLive)
        val firstRows = firstLive!!.rows!!
        assertTrue(
            "First emission should have at least one fallback row (isResolved=false)",
            firstRows.any { !it.isResolved }
        )

        // Unblock resolveAll → engine re-emits resolved rows
        gate.complete(Unit)
        refreshJob.await()

        val resolvedSnap = engine.snapshot(junction.nodeId).first()
        assertNotNull("Should still have LIVE snapshot after resolution", resolvedSnap)
        val resolvedRows = resolvedSnap!!.rows!!

        assertTrue(
            "After resolution, row should be isResolved=true with next stop label",
            resolvedRows.any { it.isResolved && it.label == "Resolved Next Stop" }
        )
    }
}
