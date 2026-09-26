package com.example.tramapp.domain.junction

import com.example.tramapp.data.local.dao.TripNextStopDao
import com.example.tramapp.data.local.entity.TripNextStopEntity
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.RouteInfo
import com.example.tramapp.data.remote.StopInfo
import com.example.tramapp.data.remote.TimestampInfo
import com.example.tramapp.data.remote.TripInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

// ========================= FAKES =========================

class FakeTripSequenceSource(
    private val stops: Map<String, List<TripStop>>,
    private val names: Map<String, String> = emptyMap(),
    private val delayMs: Long = 0L
) : TripSequenceSource {
    val tripStopsCallCount = AtomicInteger(0)
    val stopNamesCallCount = AtomicInteger(0)
    var maxConcurrent = 0
    private val concurrentGauge = AtomicInteger(0)

    override suspend fun tripStops(tripId: String): List<TripStop> {
        val c = concurrentGauge.incrementAndGet()
        synchronized(this) { if (c > maxConcurrent) maxConcurrent = c }
        try {
            tripStopsCallCount.incrementAndGet()
            if (delayMs > 0) delay(delayMs)
            return stops[tripId] ?: throw IllegalArgumentException("Unknown trip: $tripId")
        } finally {
            concurrentGauge.decrementAndGet()
        }
    }

    override suspend fun stopNames(stopIds: List<String>): Map<String, String> {
        stopNamesCallCount.incrementAndGet()
        return stopIds.associateWith { names[it] ?: it }
    }
}

class FakeTripNextStopDao : TripNextStopDao {
    private val store = mutableListOf<TripNextStopEntity>()

    override suspend fun get(
        platformStopId: String,
        tripIds: List<String>,
        minFetchedAt: Long
    ): List<TripNextStopEntity> {
        return store.filter { row ->
            row.platformStopId == platformStopId &&
                    row.tripId in tripIds &&
                    row.fetchedAt >= minFetchedAt
        }
    }

    override suspend fun insertAll(rows: List<TripNextStopEntity>) {
        val keys = rows.map { it.tripId to it.platformStopId }.toSet()
        store.removeAll { (it.tripId to it.platformStopId) in keys }
        store.addAll(rows)
    }

    override suspend fun deleteOlderThan(threshold: Long) {
        store.removeAll { it.fetchedAt < threshold }
    }
}

// ========================= HELPERS =========================

private fun makeDeparture(
    tripId: String?,
    line: String,
    headsign: String,
    platformStopId: String = "U163Z2P"
) = DepartureItem(
    route = RouteInfo(shortName = line, type = 0),
    trip = TripInfo(headsign = headsign, tripId = tripId),
    arrival = TimestampInfo(scheduled = "2026-09-26T10:00:00Z", predicted = null),
    stop = StopInfo(id = platformStopId)
)

private fun makeStops(vararg pairs: Pair<String, String?>): List<TripStop> =
    pairs.mapIndexed { idx, (id, name) -> TripStop(stopId = id, sequence = idx + 1, name = name) }

// ========================= TESTS =========================

class NextStopResolverTest {

    private lateinit var dao: FakeTripNextStopDao
    private var fakeNow = 1_000_000L

    private fun makeResolver(source: TripSequenceSource): NextStopResolver {
        return NextStopResolver(source, dao).also { it.clock = { fakeNow } }
    }

    @Before
    fun setup() {
        dao = FakeTripNextStopDao()
        fakeNow = 1_000_000L
    }

    /**
     * AE1: Hradcanska platform B (id "U163Z2P") with lines 2, 1, 25 (next stop Prasny most)
     * and 8, 18, 26 (next stop Vitezne namesti).
     */
    @Test
    fun `AE1 two next-stop groups resolve to correct stop names`() = runTest {
        // Sequence through U163Z2P (Hradcanska B):
        // Group 1: lines 2, 1, 25 → next stop Prasny most (U156Z1P)
        // Group 2: lines 8, 18, 26 → next stop Vitezne namesti (U156Z2P)
        val stopsGroup1 = makeStops(
            "U163Z1P" to "Hradcanska",   // some stop before
            "U163Z2P" to "Hradcanska B", // platform
            "U156Z1P" to "Prasny most",   // next stop
            "U155Z1P" to "Downstream1"
        )
        val stopsGroup2 = makeStops(
            "U163Z1P" to "Hradcanska",
            "U163Z2P" to "Hradcanska B",
            "U156Z2P" to "Vitezne namesti",
            "U155Z2P" to "Downstream2"
        )
        val tripStopsMap = mapOf(
            "trip-2-A"  to stopsGroup1,
            "trip-8-A"  to stopsGroup2,
        )
        val source = FakeTripSequenceSource(tripStopsMap)
        val resolver = makeResolver(source)

        val departures = listOf(
            makeDeparture("trip-2-A",  "2",  "Sidliste Rehre"),
            makeDeparture("trip-1-A",  "1",  "Sidliste Rehre"),  // same group as 2
            makeDeparture("trip-25-A", "25", "Sidliste Rehre"),  // same group as 2
            makeDeparture("trip-8-A",  "8",  "Sídliště Ďáblice"),
            makeDeparture("trip-18-A", "18", "Sídliště Ďáblice"), // same group as 8
            makeDeparture("trip-26-A", "26", "Sídliště Ďáblice"), // same group as 8
        )

        // For groups to map correctly, trips in same group must share (line, headsign).
        // Let's actually restructure: group1 is (line=ANY, headsign="Sidliste Rehre"),
        // but the spec says groups by (platform_stop_id, line_short_name, headsign).
        // So lines 2, 1, 25 form separate groups. Let's use same headsign for same group.
        // Re-read spec: "group departures by (platform stop id, line short name, headsign)"
        // So 6 separate groups → 6 fetches? No — the spec says to pick ONE representative per group.
        // Lines 2, 1, 25 with same headsign would be 3 separate groups (different line names).
        // The test says they should resolve to the same next stop — which they will if trip sequences agree.
        // Let's map all trips to have the right sequences.

        val allTripStopsMap = mapOf(
            "trip-2-A"  to stopsGroup1,
            "trip-1-A"  to stopsGroup1,
            "trip-25-A" to stopsGroup1,
            "trip-8-A"  to stopsGroup2,
            "trip-18-A" to stopsGroup2,
            "trip-26-A" to stopsGroup2,
        )
        val source2 = FakeTripSequenceSource(allTripStopsMap)
        val resolver2 = makeResolver(source2)

        val result = resolver2.resolve("U163Z2P", departures)

        assertEquals("Prasny most", result["trip-2-A"]?.nextStopName)
        assertEquals("Prasny most", result["trip-1-A"]?.nextStopName)
        assertEquals("Prasny most", result["trip-25-A"]?.nextStopName)
        assertEquals("Vitezne namesti", result["trip-8-A"]?.nextStopName)
        assertEquals("Vitezne namesti", result["trip-18-A"]?.nextStopName)
        assertEquals("Vitezne namesti", result["trip-26-A"]?.nextStopName)
    }

    /**
     * Test 2: Six departures in 2 groups trigger exactly 2 tripStops() calls;
     * a second resolve for the same trips triggers none.
     */
    @Test
    fun `2 groups trigger exactly 2 fetches, second resolve triggers none`() = runTest {
        val stops = makeStops(
            "U001Z1P" to "Stop A",
            "U163Z2P" to "Platform",
            "U002Z1P" to "Next Stop",
            "U003Z1P" to "Downstream"
        )
        val allTripStops = mapOf(
            "trip-A1" to stops, "trip-A2" to stops, "trip-A3" to stops,
            "trip-B1" to stops, "trip-B2" to stops, "trip-B3" to stops
        )
        val source = FakeTripSequenceSource(allTripStops)
        val resolver = makeResolver(source)

        val departures = listOf(
            makeDeparture("trip-A1", "2", "Destination A"),
            makeDeparture("trip-A2", "2", "Destination A"),
            makeDeparture("trip-A3", "2", "Destination A"),
            makeDeparture("trip-B1", "8", "Destination B"),
            makeDeparture("trip-B2", "8", "Destination B"),
            makeDeparture("trip-B3", "8", "Destination B"),
        )

        resolver.resolve("U163Z2P", departures)
        assertEquals("Expected exactly 2 tripStops() calls for 2 groups", 2, source.tripStopsCallCount.get())

        // Second resolve — everything is cached
        resolver.resolve("U163Z2P", departures)
        assertEquals("No new tripStops() calls on second resolve", 2, source.tripStopsCallCount.get())
    }

    /**
     * AE2: line 12 trips with NEW ids whose sequence has a different next stop resolve to
     * the new stop, while older cached trips keep theirs.
     */
    @Test
    fun `AE2 new trip ids resolve to new stop while cached trips keep old result`() = runTest {
        val oldStops = makeStops(
            "U163Z2P" to "Platform",
            "OLD_NEXT_Z1P" to "Old Next",
            "U004Z1P" to "Downstream"
        )
        val newStops = makeStops(
            "U163Z2P" to "Platform",
            "NEW_NEXT_Z1P" to "New Next",
            "U005Z1P" to "Downstream2"
        )
        val source = FakeTripSequenceSource(
            mapOf("trip-old" to oldStops, "trip-new" to newStops)
        )
        val resolver = makeResolver(source)

        // Resolve old trip first
        resolver.resolve("U163Z2P", listOf(makeDeparture("trip-old", "12", "Terminus")))

        // Now resolve new trip (different id, different sequence)
        resolver.resolve("U163Z2P", listOf(makeDeparture("trip-new", "12", "Terminus")))

        val cachedAll = resolver.cached("U163Z2P", listOf(
            makeDeparture("trip-old", "12", "Terminus"),
            makeDeparture("trip-new", "12", "Terminus")
        ))
        assertEquals("Old Next", cachedAll["trip-old"]?.nextStopName)
        assertEquals("New Next", cachedAll["trip-new"]?.nextStopName)
    }

    /**
     * Test 4: Platform last in sequence (terminus): no entry for those trips; nothing written.
     */
    @Test
    fun `terminus platform produces no result and nothing is written`() = runTest {
        val stops = makeStops(
            "U001Z1P" to "Stop A",
            "U002Z1P" to "Stop B",
            "U163Z2P" to "Terminus"  // last stop = terminus
        )
        val source = FakeTripSequenceSource(mapOf("trip-term" to stops))
        val resolver = makeResolver(source)

        resolver.resolve("U163Z2P", listOf(makeDeparture("trip-term", "2", "Terminus")))

        val result = resolver.cached("U163Z2P", listOf(makeDeparture("trip-term", "2", "Terminus")))
        assertTrue("Terminus trips must not be resolved", result.isEmpty())
    }

    /**
     * Test 5: tripStops() throws: trips stay unresolved; nothing written; next call retries.
     */
    @Test
    fun `fetch failure leaves trips unresolved and retries on next call`() = runTest {
        var shouldFail = true
        val successStops = makeStops(
            "U163Z2P" to "Platform",
            "U002Z1P" to "Next Stop",
            "U003Z1P" to "Downstream"
        )
        val source = object : TripSequenceSource {
            var callCount = 0
            override suspend fun tripStops(tripId: String): List<TripStop> {
                callCount++
                if (shouldFail) throw RuntimeException("Network error")
                return successStops
            }
            override suspend fun stopNames(stopIds: List<String>): Map<String, String> = emptyMap()
        }
        val resolver = makeResolver(source)
        val deps = listOf(makeDeparture("trip-fail", "2", "Dest"))

        // First resolve: fails
        resolver.resolve("U163Z2P", deps)
        assertTrue("Trips stay unresolved after failure", resolver.cached("U163Z2P", deps).isEmpty())
        assertEquals(1, source.callCount)

        // Second resolve: retries (shouldFail still true → still fails)
        resolver.resolve("U163Z2P", deps)
        assertEquals("Should retry on second call", 2, source.callCount)

        // Third resolve: succeeds
        shouldFail = false
        resolver.resolve("U163Z2P", deps)
        assertEquals(3, source.callCount)
        val result = resolver.cached("U163Z2P", deps)
        assertEquals("Next Stop", result["trip-fail"]?.nextStopName)
    }

    /**
     * Test 6: Platform matched by node id when exact stop id is absent from sequence.
     */
    @Test
    fun `platform matched by node id when exact stop id absent`() = runTest {
        // Platform stop id is "U163Z2P", but the trip sequence has "U163Z3P" (same node U163)
        val stops = makeStops(
            "U163Z3P" to "Hradcanska (alt platform)",  // same node U163, but different stop id
            "U156Z1P" to "Prasny most",
            "U155Z1P" to "Downstream"
        )
        val source = FakeTripSequenceSource(mapOf("trip-nodeMatch" to stops))
        val resolver = makeResolver(source)

        resolver.resolve("U163Z2P", listOf(makeDeparture("trip-nodeMatch", "2", "Dest")))
        val result = resolver.cached("U163Z2P", listOf(makeDeparture("trip-nodeMatch", "2", "Dest")))

        assertNotNull("Should match by node id", result["trip-nodeMatch"])
        assertEquals("Prasny most", result["trip-nodeMatch"]?.nextStopName)
    }

    /**
     * Test 7: downstreamNodeIds contains nodes after platform, not nodes before it.
     */
    @Test
    fun `downstreamNodeIds correct - home node downstream present, upstream absent`() = runTest {
        // Stops in sequence: U001 → U163 (platform) → U200 (home node) → U300
        val stops = makeStops(
            "U001Z1P" to "Before",
            "U163Z2P" to "Platform",
            "U200Z1P" to "Home Stop",  // downstream
            "U300Z1P" to "End"
        )
        val source = FakeTripSequenceSource(mapOf("trip-ds" to stops))
        val resolver = makeResolver(source)

        resolver.resolve("U163Z2P", listOf(makeDeparture("trip-ds", "2", "Dest")))
        val result = resolver.cached("U163Z2P", listOf(makeDeparture("trip-ds", "2", "Dest")))

        val downstream = result["trip-ds"]?.downstreamNodeIds ?: emptySet()
        assertTrue("U200 (home) should be downstream", "U200" in downstream)
        assertTrue("U300 should be downstream", "U300" in downstream)
        assertFalse("U001 (upstream) should NOT be downstream", "U001" in downstream)
        assertFalse("U163 (platform itself) should NOT be downstream", "U163" in downstream)
    }

    /**
     * Test 8: With 10 groups and slow source, never more than 4 fetches in flight at once.
     */
    @Test
    fun `max 4 concurrent fetches with 10 groups`() = runTest {
        val stops = makeStops(
            "U163Z2P" to "Platform",
            "U999Z1P" to "Next",
            "U998Z1P" to "Downstream"
        )
        val tripMap = (1..10).associate { i -> "trip-$i" to stops }
        val source = FakeTripSequenceSource(tripMap, delayMs = 50L)
        val resolver = makeResolver(source)

        // 10 departures each with unique (line, headsign) → 10 groups → 10 fetches
        val departures = (1..10).map { i ->
            makeDeparture("trip-$i", "line-$i", "headsign-$i")
        }

        resolver.resolve("U163Z2P", departures)

        assertTrue(
            "Max concurrent fetches should be <= 4, was ${source.maxConcurrent}",
            source.maxConcurrent <= 4
        )
    }

    /**
     * Test 9: Expired rows (fetchedAt older than TTL via clock) are not returned by cached().
     */
    @Test
    fun `expired rows are not returned by cached`() = runTest {
        val stops = makeStops(
            "U163Z2P" to "Platform",
            "U002Z1P" to "Next",
            "U003Z1P" to "Downstream"
        )
        val source = FakeTripSequenceSource(mapOf("trip-exp" to stops))
        val resolver = makeResolver(source)

        // Resolve at fakeNow
        fakeNow = 1_000_000L
        resolver.resolve("U163Z2P", listOf(makeDeparture("trip-exp", "2", "Dest")))

        // Advance clock past TTL
        fakeNow = 1_000_000L + NextStopResolver.TTL_MS + 1

        val result = resolver.cached("U163Z2P", listOf(makeDeparture("trip-exp", "2", "Dest")))
        assertTrue("Expired rows should not be returned", result.isEmpty())
    }

    /**
     * Test 10: Names: missing name in trip response falls back to stopNames() with ONE call for all missing ids.
     */
    @Test
    fun `missing names fall back to stopNames with single call for all missing`() = runTest {
        // Trip sequence has null names (no stop.stop_name in response)
        val stops = listOf(
            TripStop("U163Z2P", 1, null),
            TripStop("U200Z1P", 2, null),  // next stop — name missing
            TripStop("U300Z1P", 3, null),  // downstream — name missing
        )
        val nameMap = mapOf(
            "U163Z2P" to "Platform",
            "U200Z1P" to "Fetched Next",
            "U300Z1P" to "Fetched Downstream"
        )
        val source = FakeTripSequenceSource(
            stops = mapOf("trip-nonames" to stops),
            names = nameMap
        )
        val resolver = makeResolver(source)

        resolver.resolve("U163Z2P", listOf(makeDeparture("trip-nonames", "2", "Dest")))

        assertEquals("Expected exactly 1 stopNames() call", 1, source.stopNamesCallCount.get())
        val result = resolver.cached("U163Z2P", listOf(makeDeparture("trip-nonames", "2", "Dest")))
        assertEquals("Fetched Next", result["trip-nonames"]?.nextStopName)
    }
}
