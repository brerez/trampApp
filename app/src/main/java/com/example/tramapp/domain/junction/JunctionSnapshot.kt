package com.example.tramapp.domain.junction

enum class SnapshotState { LOADING, LIVE, ERROR }
enum class Destination { HOME, WORK, SCHOOL }

data class TramDeparture(
    val tripId: String?,
    val line: String,
    val headsign: String,
    val departureEpochMs: Long,      // predicted ?: scheduled
    val isAtStop: Boolean,
    val delayMinutes: Int?,
    val isCancelled: Boolean,
    val isAccessible: Boolean?,
    val isAirConditioned: Boolean?
) {
    /**
     * 0 means "now". Returns 0 if isAtStop; otherwise floor((departureEpochMs - nowMs) / 60000),
     * clamped to >= 0.
     */
    fun minutesUntil(nowMs: Long): Int {
        if (isAtStop) return 0
        val diff = ((departureEpochMs - nowMs) / 60_000L).toInt()
        return maxOf(0, diff)
    }
}

data class JunctionRow(
    val platformStopId: String,
    val platformLetter: String,
    val platformPosition: GeoPoint,
    val nextStopId: String?,         // null = end-stop fallback
    val label: String,               // next stop name, or end stop (headsign) when unresolved
    val isResolved: Boolean,
    val trams: List<TramDeparture>,
    val isPlatformFirstRow: Boolean, // carries the platform's distance + direction
    val highlights: Set<Destination>
)

data class JunctionSnapshot(
    val junction: Junction,
    val rows: List<JunctionRow>?,    // null until the first successful fetch
    val fetchedAtMs: Long?,          // time of the last SUCCESSFUL fetch
    val state: SnapshotState,
    val lastErrorAtMs: Long? = null
)
