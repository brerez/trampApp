package com.example.tramapp.domain.junction

import com.example.tramapp.data.remote.DepartureItem
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Pure, stateless builder: converts raw API departure items for a junction into
 * sorted, grouped JunctionRows. No Android imports.
 */
object JunctionRowBuilder {

    // ---------- public API ----------

    fun build(
        junction: Junction,
        departuresByPlatform: Map<String, List<DepartureItem>>,
        resolved: Map<String, Map<String, TripNextStop>>,   // platformStopId -> tripId -> next stop
        destinations: DestinationNodes
    ): List<JunctionRow> {

        // 1. Build a flat list of raw rows (one per platform × next-stop group).
        val rawRows = mutableListOf<RowAccumulator>()

        for (platform in junction.platforms) {
            val deps = departuresByPlatform[platform.stopId].orEmpty()
            val platformResolved = resolved[platform.stopId].orEmpty()

            // Group departures: resolved trips group by nextStopId; unresolved group by headsign.
            val groups = LinkedHashMap<RowKey, MutableList<TramDeparture>>()
            for (item in deps) {
                val tram = toTram(item) ?: continue
                val tripId = item.trip.tripId
                val nextStop = if (tripId != null) platformResolved[tripId] else null
                val key = if (nextStop != null) {
                    RowKey.Resolved(nextStop.nextStopId, nextStop.nextStopName)
                } else {
                    RowKey.Fallback(item.trip.headsign)
                }
                groups.getOrPut(key) { mutableListOf() }.add(tram)
            }

            // Turn each group into a RowAccumulator.
            for ((key, trams) in groups) {
                val sortedTrams = trams.sortedBy { it.departureEpochMs }
                val (nextStopId, label, isResolved) = when (key) {
                    is RowKey.Resolved -> Triple(key.nextStopId, key.label, true)
                    is RowKey.Fallback -> Triple(null, key.headsign, false)
                }

                // Compute the row's sort time: earliest non-cancelled tram time (or Long.MAX if all cancelled).
                val sortEpoch = sortedTrams
                    .firstOrNull { !it.isCancelled }
                    ?.departureEpochMs
                    ?: Long.MAX_VALUE

                // Highlight: any RESOLVED trip in the row has a downstream node in the destination's set.
                val rowHighlights = computeHighlights(
                    trams = trams,
                    platformResolved = platformResolved,
                    destinations = destinations
                )

                rawRows.add(
                    RowAccumulator(
                        platformStopId = platform.stopId,
                        platformLetter = platform.letter,
                        platformPosition = platform.position,
                        nextStopId = nextStopId,
                        label = label,
                        isResolved = isResolved,
                        trams = sortedTrams,
                        sortEpoch = sortEpoch,
                        highlights = rowHighlights
                    )
                )
            }
        }

        // 2. Sort rows: by sortEpoch (rows with only-cancelled go last via Long.MAX_VALUE),
        //    then platform letter, then label.
        rawRows.sortWith(
            compareBy<RowAccumulator> { it.sortEpoch }
                .thenBy { it.platformLetter }
                .thenBy { it.label }
        )

        // 3. Mark the FIRST row per platform as isPlatformFirstRow.
        val seenPlatforms = mutableSetOf<String>()

        return rawRows.map { acc ->
            val isFirst = seenPlatforms.add(acc.platformStopId) // returns true if newly added
            JunctionRow(
                platformStopId = acc.platformStopId,
                platformLetter = acc.platformLetter,
                platformPosition = acc.platformPosition,
                nextStopId = acc.nextStopId,
                label = acc.label,
                isResolved = acc.isResolved,
                trams = acc.trams,
                isPlatformFirstRow = isFirst,
                highlights = acc.highlights
            )
        }
    }

    /**
     * Converts a DepartureItem to a TramDeparture; returns null if neither
     * scheduled nor predicted time can be parsed.
     */
    fun toTram(item: DepartureItem): TramDeparture? {
        val epochMs = parseEpochMs(item.arrival.predicted)
            ?: parseEpochMs(item.arrival.scheduled)
            ?: return null

        val delayMinutes: Int? = if (item.delay?.isAvailable == true && item.delay.minutes != 0) {
            item.delay.minutes
        } else {
            null
        }

        return TramDeparture(
            tripId = item.trip.tripId,
            line = item.route.shortName,
            headsign = item.trip.headsign,
            departureEpochMs = epochMs,
            isAtStop = item.trip.isAtStop == true,
            delayMinutes = delayMinutes,
            isCancelled = item.trip.isCanceled == true,
            isAccessible = item.trip.isWheelchairAccessible,
            isAirConditioned = item.trip.isAirConditioned
        )
    }

    // ---------- private helpers ----------

    private sealed interface RowKey {
        data class Resolved(val nextStopId: String, val label: String) : RowKey
        data class Fallback(val headsign: String) : RowKey
    }

    private data class RowAccumulator(
        val platformStopId: String,
        val platformLetter: String,
        val platformPosition: GeoPoint,
        val nextStopId: String?,
        val label: String,
        val isResolved: Boolean,
        val trams: List<TramDeparture>,
        val sortEpoch: Long,
        val highlights: Set<Destination>
    )

    private fun computeHighlights(
        trams: List<TramDeparture>,
        platformResolved: Map<String, TripNextStop>,
        destinations: DestinationNodes
    ): Set<Destination> {
        if (destinations.byDestination.isEmpty()) return emptySet()

        // Collect all downstream node sets for resolved trips in this row.
        val downstreamNodes = trams
            .mapNotNull { t -> if (t.tripId != null) platformResolved[t.tripId] else null }
            .flatMap { it.downstreamNodeIds }
            .toSet()

        if (downstreamNodes.isEmpty()) return emptySet()

        return destinations.byDestination
            .filterValues { nodeSet -> nodeSet.any { it in downstreamNodes } }
            .keys
    }

    private fun parseEpochMs(timeString: String?): Long? {
        if (timeString.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(timeString).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(timeString).toEpochMilli()
            } catch (_: Exception) {
                try {
                    ZonedDateTime.parse(timeString).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
