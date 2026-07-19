package com.example.tramapp.ui

import com.example.tramapp.domain.SmartDeparture

/**
 * U4 (R1–R4) — Explicit per-station (per-platform-id) UI state.
 *
 * Replaces the old implicit "a station has data iff it is a key in
 * `_rawStationDepartures`" model. A station is only promoted into the ranked,
 * populated list once it reaches [Ready]; [Loading] renders a skeleton /
 * "checking…" affordance; [Empty] is collapsed out.
 */
sealed interface StationUiState {
    /** Departures are in flight — render a skeleton, never a populated-looking empty row. */
    data object Loading : StationUiState

    /** Confirmed departures (non-empty, future trams). Holds a stable position. */
    data class Ready(val departures: List<SmartDeparture>) : StationUiState

    /** Confirmed no relevant departures — collapsed/hidden, excluded from the visible list. */
    data object Empty : StationUiState
}

/**
 * A visible station *group* (platforms sharing a base name) with its aggregate
 * settled state, ordered by [StationUiState] promotion then distance.
 *
 * Aggregation rule (KTD2):
 *  - [isReady]   : at least one platform is [StationUiState.Ready] with departures
 *                  → promoted, rendered as a populated card. Stays Ready (does not
 *                  flicker to a skeleton) while a refresh re-fetches in place.
 *  - [isLoading] : no departures yet and at least one platform still resolving
 *                  (Loading or not-yet-fetched) → rendered as a skeleton.
 *  - Empty groups (all platforms [StationUiState.Empty]) are excluded entirely.
 */
data class StationRow(
    val baseName: String,
    val platformIds: List<String>,
    /** platform label (e.g. "A") → its departures; only meaningful when [isReady]. */
    val platformDepartures: List<Pair<String, List<SmartDeparture>>>,
    val isReady: Boolean,
    val distanceSq: Double
) {
    /** A still-resolving group with nothing to show yet → skeleton. */
    val isLoading: Boolean get() = !isReady
}
