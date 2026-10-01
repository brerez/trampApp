package com.example.tramapp.domain.junction

data class GeoPoint(val lat: Double, val lng: Double)

data class LocationFix(val lat: Double, val lng: Double, val accuracyM: Float, val timeMs: Long) {
    val point: GeoPoint get() = GeoPoint(lat, lng)
}

/** Input row from the station cache (mapped from StationEntity by the caller). */
data class PlatformStop(
    val stopId: String, val nodeId: String, val name: String, val platformCode: String?,
    val lat: Double, val lng: Double, val isTram: Boolean?   // null = not yet known
)

data class Platform(val stopId: String, val letter: String, val position: GeoPoint, val isTram: Boolean?)

data class Junction(val nodeId: String, val name: String, val platforms: List<Platform>)

data class RankedJunction(val junction: Junction, val distanceM: Double)

sealed interface JunctionSelection {
    data object NoFix : JunctionSelection
    
    /** No tram junction within walking range (R8). */
    data class NoneInRange(val nearestDistanceM: Double?) : JunctionSelection
    
    data class Selected(
        val junction: Junction,
        val distanceM: Double,              // distance from latest fix to the junction's nearest platform
        val ranked: List<RankedJunction>,   // all junctions within walking range, nearest first (latest fix)
        val cycleIndex: Int,                // 0 = anchor (hysteresis-nearest), 1 = 2nd, 2 = 3rd
        val fix: LocationFix                // the latest fix
    ) : JunctionSelection
}
