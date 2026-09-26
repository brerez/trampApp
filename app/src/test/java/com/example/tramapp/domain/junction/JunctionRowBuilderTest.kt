package com.example.tramapp.domain.junction

import com.example.tramapp.data.remote.DelayInfo
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.RouteInfo
import com.example.tramapp.data.remote.StopInfo
import com.example.tramapp.data.remote.TimestampInfo
import com.example.tramapp.data.remote.TripInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ===========================================================
// Helpers
// ===========================================================

/**
 * Fixed "now" for tests: 2026-09-26T10:00:00Z in milliseconds.
 * ISO-8601 times are expressed relative to this anchor.
 */
private const val NOW_MS = 1_758_888_000_000L  // 2026-09-26T10:00:00Z

/** Build an ISO-8601 UTC time string that is [offsetMinutes] minutes after NOW_MS. */
private fun isoTime(offsetMinutes: Int): String {
    val epochMs = NOW_MS + offsetMinutes * 60_000L
    return java.time.Instant.ofEpochMilli(epochMs)
        .atOffset(java.time.ZoneOffset.UTC)
        .toString()
}

private fun makeDep(
    tripId: String?,
    line: String,
    headsign: String,
    platformStopId: String,
    scheduledMinOffset: Int,
    predictedMinOffset: Int? = null,
    isCanceled: Boolean = false,
    isAtStop: Boolean = false,
    delayAvailable: Boolean = false,
    delayMinutes: Int = 0,
    isAccessible: Boolean? = null,
    isAirConditioned: Boolean? = null
) = DepartureItem(
    route = RouteInfo(shortName = line, type = 0),
    trip = TripInfo(
        headsign = headsign,
        tripId = tripId,
        isCanceled = isCanceled,
        isAtStop = isAtStop,
        isWheelchairAccessible = isAccessible,
        isAirConditioned = isAirConditioned
    ),
    arrival = TimestampInfo(
        scheduled = isoTime(scheduledMinOffset),
        predicted = predictedMinOffset?.let { isoTime(it) }
    ),
    stop = StopInfo(id = platformStopId),
    delay = DelayInfo(isAvailable = delayAvailable, minutes = delayMinutes)
)

private fun makeResolved(
    tripId: String,
    platformStopId: String,
    nextStopId: String,
    nextStopName: String,
    downstreamNodeIds: Set<String> = emptySet()
) = TripNextStop(
    tripId = tripId,
    platformStopId = platformStopId,
    nextStopId = nextStopId,
    nextStopName = nextStopName,
    downstreamNodeIds = downstreamNodeIds
)

/** A minimal single-platform Hradcanska B junction for AE1. */
private val HRADCANSKA_B_PLATFORM = Platform(
    stopId = "U163Z2P",
    letter = "B",
    position = GeoPoint(50.091, 14.393),
    isTram = true
)

private val HRADCANSKA_B_JUNCTION = Junction(
    nodeId = "U163",
    name = "Hradcanska",
    platforms = listOf(HRADCANSKA_B_PLATFORM)
)

// ===========================================================
// Tests
// ===========================================================

class JunctionRowBuilderTest {

    // ----------------------------------------------------------
    // AE1: Hradcanska B — two resolved next-stop groups
    // ----------------------------------------------------------

    @Test
    fun ae1_twoResolvedNextStopGroupsGiveExactlyTwoRowsForPlatformB() {
        val deps = listOf(
            makeDep("trip-2",  "2",  "Sidliste Rehre",   "U163Z2P", scheduledMinOffset = 2),
            makeDep("trip-1",  "1",  "Sidliste Rehre",   "U163Z2P", scheduledMinOffset = 5),
            makeDep("trip-25", "25", "Sidliste Rehre",   "U163Z2P", scheduledMinOffset = 9),
            makeDep("trip-8",  "8",  "Sidliste Dablice", "U163Z2P", scheduledMinOffset = 3),
            makeDep("trip-18", "18", "Sidliste Dablice", "U163Z2P", scheduledMinOffset = 7),
            makeDep("trip-26", "26", "Sidliste Dablice", "U163Z2P", scheduledMinOffset = 12),
        )
        val resolved = mapOf(
            "U163Z2P" to mapOf(
                "trip-2"  to makeResolved("trip-2",  "U163Z2P", "U156Z1P", "Prasny most"),
                "trip-1"  to makeResolved("trip-1",  "U163Z2P", "U156Z1P", "Prasny most"),
                "trip-25" to makeResolved("trip-25", "U163Z2P", "U156Z1P", "Prasny most"),
                "trip-8"  to makeResolved("trip-8",  "U163Z2P", "U156Z2P", "Vitezne namesti"),
                "trip-18" to makeResolved("trip-18", "U163Z2P", "U156Z2P", "Vitezne namesti"),
                "trip-26" to makeResolved("trip-26", "U163Z2P", "U156Z2P", "Vitezne namesti"),
            )
        )

        val rows = JunctionRowBuilder.build(
            junction = HRADCANSKA_B_JUNCTION,
            departuresByPlatform = mapOf("U163Z2P" to deps),
            resolved = resolved,
            destinations = DestinationNodes.NONE
        )

        assertEquals("Exactly two rows for platform B", 2, rows.size)

        // Row 0 is soonest: trip-2 @ +2 min vs trip-8 @ +3 min → Prasny most is first
        val firstRow  = rows[0]
        val secondRow = rows[1]

        assertEquals("Prasny most",    firstRow.label)
        assertEquals("Vitezne namesti", secondRow.label)
        assertTrue("First row must have isPlatformFirstRow=true",  firstRow.isPlatformFirstRow)
        assertFalse("Second row must have isPlatformFirstRow=false", secondRow.isPlatformFirstRow)
    }

    // ----------------------------------------------------------
    // AE6: 8 rows, all present, ordered by soonest tram (no trimming)
    // ----------------------------------------------------------

    @Test
    fun ae6_eightRowsAllPresentOrderedBySoonestTramNoTrimming() {
        // 8 distinct next stops, each with one trip; scheduled offsets vary
        val offsets = listOf(15, 3, 7, 20, 1, 12, 8, 5)
        val deps = offsets.mapIndexed { i, offset ->
            makeDep("trip-$i", "$i", "Headsign $i", "U163Z2P", scheduledMinOffset = offset)
        }
        val resolved = mapOf(
            "U163Z2P" to deps.mapIndexed { i, dep ->
                dep.trip.tripId!! to makeResolved(dep.trip.tripId!!, "U163Z2P", "NEXT${i}Z1P", "Next Stop $i")
            }.toMap()
        )

        val rows = JunctionRowBuilder.build(
            junction = HRADCANSKA_B_JUNCTION,
            departuresByPlatform = mapOf("U163Z2P" to deps),
            resolved = resolved,
            destinations = DestinationNodes.NONE
        )

        assertEquals("All 8 rows present - no trimming", 8, rows.size)

        // Rows must be ordered by soonest tram departure (ascending offset)
        val sortedOffsets = offsets.sorted()
        rows.forEachIndexed { idx, row ->
            val originalIdx = offsets.indexOf(sortedOffsets[idx])
            val expectedLabel = "Next Stop $originalIdx"
            assertEquals("Row $idx label mismatch", expectedLabel, row.label)
        }
    }

    // ----------------------------------------------------------
    // Cancelled trams: marked cancelled, don't set sort time;
    // row with only cancelled goes last
    // ----------------------------------------------------------

    @Test
    fun cancelledTramDoesNotSetRowSortTime_allCancelledRowSortsLast() {
        val deps = listOf(
            // Row A: one live tram at +5 min
            makeDep("trip-live",      "1", "Dest A", "U163Z2P", scheduledMinOffset = 5),
            // Row B: only a cancelled tram at +2 min (should go LAST despite earlier time)
            makeDep("trip-cancelled", "2", "Dest B", "U163Z2P", scheduledMinOffset = 2, isCanceled = true),
        )
        val resolved = mapOf(
            "U163Z2P" to mapOf(
                "trip-live"      to makeResolved("trip-live",      "U163Z2P", "NEXTA_Z1P", "Stop A"),
                "trip-cancelled" to makeResolved("trip-cancelled", "U163Z2P", "NEXTB_Z1P", "Stop B"),
            )
        )

        val rows = JunctionRowBuilder.build(
            junction = HRADCANSKA_B_JUNCTION,
            departuresByPlatform = mapOf("U163Z2P" to deps),
            resolved = resolved,
            destinations = DestinationNodes.NONE
        )

        assertEquals(2, rows.size)
        // Live row first, cancelled-only row last
        assertEquals("Stop A", rows[0].label)
        assertEquals("Stop B", rows[1].label)

        // The cancelled tram is still in the row, marked cancelled
        assertTrue("Tram in all-cancelled row must be marked cancelled", rows[1].trams[0].isCancelled)
    }

    // ----------------------------------------------------------
    // isAtStop → minutesUntil == 0; delay carried correctly
    // ----------------------------------------------------------

    @Test
    fun isAtStopGivesMinutesUntilZero_delayAvailableCarriedAsInt_unavailableDelayIsNull() {
        val atStopDep = makeDep(
            "trip-atStop", "1", "Dest", "U163Z2P",
            scheduledMinOffset = 10,
            isAtStop = true,
            delayAvailable = true,
            delayMinutes = 3
        )
        val tram = JunctionRowBuilder.toTram(atStopDep)!!
        assertEquals("isAtStop -> minutesUntil(nowMs) == 0", 0, tram.minutesUntil(NOW_MS))

        // Delay available with non-zero minutes → carried
        val delayDep = makeDep(
            "trip-delay", "2", "Dest", "U163Z2P",
            scheduledMinOffset = 5,
            delayAvailable = true,
            delayMinutes = 3
        )
        val delayTram = JunctionRowBuilder.toTram(delayDep)!!
        assertEquals("delay=+3 when available and non-zero", 3, delayTram.delayMinutes)

        // Delay unavailable → null
        val noDelayDep = makeDep(
            "trip-nodelay", "3", "Dest", "U163Z2P",
            scheduledMinOffset = 5,
            delayAvailable = false,
            delayMinutes = 3
        )
        val noDelayTram = JunctionRowBuilder.toTram(noDelayDep)!!
        assertNull("delay=null when isAvailable=false", noDelayTram.delayMinutes)

        // Delay available but minutes==0 → null (no point showing "+0")
        val zeroDelayDep = makeDep(
            "trip-zerodelay", "4", "Dest", "U163Z2P",
            scheduledMinOffset = 5,
            delayAvailable = true,
            delayMinutes = 0
        )
        val zeroDelayTram = JunctionRowBuilder.toTram(zeroDelayDep)!!
        assertNull("delay=null when minutes==0", zeroDelayTram.delayMinutes)
    }

    // ----------------------------------------------------------
    // R14: highlight logic
    // ----------------------------------------------------------

    @Test
    fun r14_homeHighlightAppliedWhenDownstreamNodeMatches_upstreamNodeDoesNotHighlight() {
        val homeNodeId = "U999"

        val deps = listOf(
            makeDep("trip-home",  "1", "Dest Home",  "U163Z2P", scheduledMinOffset = 5),
            makeDep("trip-other", "2", "Dest Other", "U163Z2P", scheduledMinOffset = 8),
        )

        // trip-home has homeNode downstream; trip-other has only upstream nodes
        val resolved = mapOf(
            "U163Z2P" to mapOf(
                "trip-home"  to makeResolved("trip-home",  "U163Z2P", "NEXT1Z1P", "Home Row Next",  downstreamNodeIds = setOf(homeNodeId, "U998")),
                "trip-other" to makeResolved("trip-other", "U163Z2P", "NEXT2Z1P", "Other Row Next", downstreamNodeIds = setOf("U001")),
            )
        )

        val destinationsNone = DestinationNodes.NONE
        val destinationsHome = DestinationNodes(mapOf(Destination.HOME to setOf(homeNodeId)))

        val rowsNone = JunctionRowBuilder.build(
            HRADCANSKA_B_JUNCTION,
            mapOf("U163Z2P" to deps),
            resolved,
            destinationsNone
        )
        val rowsHome = JunctionRowBuilder.build(
            HRADCANSKA_B_JUNCTION,
            mapOf("U163Z2P" to deps),
            resolved,
            destinationsHome
        )

        // Same row count and order regardless of highlights (R14)
        assertEquals("Row set unchanged by highlight", rowsNone.size, rowsHome.size)
        rowsNone.zip(rowsHome).forEach { (none, home) ->
            assertEquals("Row order unchanged", none.label, home.label)
        }

        val homeRow  = rowsHome.first { it.label == "Home Row Next" }
        val otherRow = rowsHome.first { it.label == "Other Row Next" }

        assertTrue("Home row must be highlighted",                      Destination.HOME in homeRow.highlights)
        assertFalse("Other row (upstream only) must NOT be highlighted", Destination.HOME in otherRow.highlights)

        // NONE build has no highlights at all
        rowsNone.forEach { row ->
            assertTrue("NONE build: no highlights on row ${row.label}", row.highlights.isEmpty())
        }
    }

    // ----------------------------------------------------------
    // R13: unresolved → fallback row; resolution → row moves
    // ----------------------------------------------------------

    @Test
    fun r13_unresolvedTripSitsInFallbackRow_withResolutionItMovesToNextStopRow() {
        val dep = makeDep("trip-unresolved", "1", "Headsign End", "U163Z2P", scheduledMinOffset = 5)

        // Build with no resolution → fallback row
        val rowsUnresolved = JunctionRowBuilder.build(
            HRADCANSKA_B_JUNCTION,
            mapOf("U163Z2P" to listOf(dep)),
            emptyMap(),
            DestinationNodes.NONE
        )
        assertEquals(1, rowsUnresolved.size)
        val fallbackRow = rowsUnresolved[0]
        assertFalse("Fallback row must have isResolved=false",      fallbackRow.isResolved)
        assertEquals("Headsign End",                                fallbackRow.label)
        assertNull("Fallback row must have null nextStopId",         fallbackRow.nextStopId)

        // Build with resolution → next-stop row; fallback row disappears
        val resolved = mapOf(
            "U163Z2P" to mapOf(
                "trip-unresolved" to makeResolved(
                    "trip-unresolved", "U163Z2P", "NEXTZ1P", "Resolved Stop"
                )
            )
        )
        val rowsResolved = JunctionRowBuilder.build(
            HRADCANSKA_B_JUNCTION,
            mapOf("U163Z2P" to listOf(dep)),
            resolved,
            DestinationNodes.NONE
        )
        assertEquals("Fallback row disappears once resolved", 1, rowsResolved.size)
        val resolvedRow = rowsResolved[0]
        assertTrue("Resolved row must have isResolved=true", resolvedRow.isResolved)
        assertEquals("Resolved Stop",                        resolvedRow.label)
        assertEquals("NEXTZ1P",                              resolvedRow.nextStopId)
    }

    // ----------------------------------------------------------
    // Two platforms: first row per platform has isPlatformFirstRow
    // ----------------------------------------------------------

    @Test
    fun twoPlatforms_eachPlatformFirstRowHasIsPlatformFirstRow_noOtherRowDoes() {
        val platformA = Platform("U163Z1P", "A", GeoPoint(50.091, 14.392), true)
        val platformB = Platform("U163Z2P", "B", GeoPoint(50.091, 14.393), true)
        val junction  = Junction("U163", "Hradcanska", listOf(platformA, platformB))

        val depsA = listOf(
            makeDep("trip-A1", "1", "Dest A1", "U163Z1P", scheduledMinOffset = 3),
            makeDep("trip-A2", "2", "Dest A2", "U163Z1P", scheduledMinOffset = 7),
        )
        val depsB = listOf(
            makeDep("trip-B1", "8",  "Dest B1", "U163Z2P", scheduledMinOffset = 5),
            makeDep("trip-B2", "18", "Dest B2", "U163Z2P", scheduledMinOffset = 10),
        )

        val resolved = mapOf(
            "U163Z1P" to mapOf(
                "trip-A1" to makeResolved("trip-A1", "U163Z1P", "NEXTA1Z1P", "Next A1"),
                "trip-A2" to makeResolved("trip-A2", "U163Z1P", "NEXTA2Z1P", "Next A2"),
            ),
            "U163Z2P" to mapOf(
                "trip-B1" to makeResolved("trip-B1", "U163Z2P", "NEXTB1Z1P", "Next B1"),
                "trip-B2" to makeResolved("trip-B2", "U163Z2P", "NEXTB2Z1P", "Next B2"),
            )
        )

        val rows = JunctionRowBuilder.build(
            junction,
            mapOf("U163Z1P" to depsA, "U163Z2P" to depsB),
            resolved,
            DestinationNodes.NONE
        )

        assertEquals(4, rows.size)

        val firstRowsA = rows.filter { it.platformStopId == "U163Z1P" && it.isPlatformFirstRow }
        val firstRowsB = rows.filter { it.platformStopId == "U163Z2P" && it.isPlatformFirstRow }
        assertEquals("Platform A must have exactly 1 first row", 1, firstRowsA.size)
        assertEquals("Platform B must have exactly 1 first row", 1, firstRowsB.size)

        val nonFirstA = rows.filter { it.platformStopId == "U163Z1P" && !it.isPlatformFirstRow }
        val nonFirstB = rows.filter { it.platformStopId == "U163Z2P" && !it.isPlatformFirstRow }
        assertEquals("Platform A: 1 non-first row", 1, nonFirstA.size)
        assertEquals("Platform B: 1 non-first row", 1, nonFirstB.size)
    }

    // ----------------------------------------------------------
    // Additional: predicted time wins over scheduled
    // ----------------------------------------------------------

    @Test
    fun predictedTimeWinsOverScheduled() {
        val depWithPredicted = makeDep(
            "trip-pred", "1", "Dest", "U163Z2P",
            scheduledMinOffset = 10,
            predictedMinOffset = 7
        )
        val tram = JunctionRowBuilder.toTram(depWithPredicted)!!
        val expectedMs = NOW_MS + 7 * 60_000L
        assertEquals("Predicted time wins over scheduled", expectedMs, tram.departureEpochMs)
    }

    @Test
    fun unparseableTimeStringReturnsNullFromToTram() {
        val badDep = DepartureItem(
            route = RouteInfo("1", 0),
            trip = TripInfo("Dest"),
            arrival = TimestampInfo(scheduled = "NOT_A_TIME", predicted = null),
            stop = StopInfo("U163Z2P")
        )
        assertNull("Unparseable time -> null", JunctionRowBuilder.toTram(badDep))
    }

    // ----------------------------------------------------------
    // minutesUntil: floor and clamp
    // ----------------------------------------------------------

    @Test
    fun minutesUntilReturnsZeroForPastDepartures_correctFloorForFuture() {
        val tram = TramDeparture(
            tripId = "t",
            line = "1",
            headsign = "H",
            departureEpochMs = NOW_MS + 3 * 60_000L + 59_000L,  // 3 min 59 s from now
            isAtStop = false,
            delayMinutes = null,
            isCancelled = false,
            isAccessible = null,
            isAirConditioned = null
        )
        assertEquals("floor(3.983 min) = 3", 3, tram.minutesUntil(NOW_MS))

        val pastTram = tram.copy(departureEpochMs = NOW_MS - 1_000L)
        assertEquals("Past -> clamped to 0", 0, pastTram.minutesUntil(NOW_MS))
    }
}
