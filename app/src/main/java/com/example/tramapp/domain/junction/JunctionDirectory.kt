package com.example.tramapp.domain.junction

object JunctionDirectory {
    const val REDISCOVER_DISTANCE_M = 300.0
    const val REDISCOVER_AGE_MS = 24L * 60 * 60 * 1000

    fun nodeIdOf(stopId: String): String {
        val idx = stopId.indexOf('Z')
        return if (idx == -1) stopId else stopId.substring(0, idx)
    }

    /** PID city platforms (the only ones trams use) look like "U324Z1P"; rail nodes ("T57073")
     *  and other feeds never carry trams, and their isTram stays unknown when they have no
     *  departures in the window, so they'd otherwise show up as junctions forever. */
    private val PID_PLATFORM = Regex("^U[0-9]+Z")

    /** Groups by nodeId. Excludes non-PID-city stops and platforms with isTram == false. Excludes a junction with no remaining
     *  platforms. Letter = platformCode ?: text between 'Z' and trailing 'P' of stopId ?: "?".
     *  Junction name = most common `name` among its platforms (strip any trailing " [X]" suffix).
     *  Platforms sorted by letter; junctions sorted by nodeId. */
    fun build(stops: List<PlatformStop>): List<Junction> {
        return stops.filter { it.isTram != false && PID_PLATFORM.containsMatchIn(it.stopId) }
            .groupBy { it.nodeId }
            .mapNotNull { (nodeId, platformStops) ->
                if (platformStops.isEmpty()) return@mapNotNull null
                
                val nameCounts = platformStops.groupingBy { 
                    it.name.replace(Regex(" \\[[^\\]]+\\]$"), "")
                }.eachCount()
                val mostCommonName = nameCounts.maxByOrNull { it.value }?.key ?: ""

                val platforms = platformStops.map { ps ->
                    val letter = ps.platformCode ?: run {
                        val zIdx = ps.stopId.indexOf('Z')
                        if (zIdx != -1 && ps.stopId.endsWith("P")) {
                            ps.stopId.substring(zIdx + 1, ps.stopId.length - 1)
                        } else {
                            "?"
                        }
                    }
                    Platform(ps.stopId, letter, GeoPoint(ps.lat, ps.lng), ps.isTram)
                }.sortedBy { it.letter }

                if (platforms.isEmpty()) null else Junction(nodeId, mostCommonName, platforms)
            }
            .sortedBy { it.nodeId }
    }

    /** True when never discovered, moved > REDISCOVER_DISTANCE_M from last discovery point,
     *  or last discovery older than REDISCOVER_AGE_MS. */
    fun shouldRediscover(lastPoint: GeoPoint?, lastTimeMs: Long?, current: GeoPoint, nowMs: Long): Boolean {
        if (lastPoint == null || lastTimeMs == null) return true
        if (nowMs - lastTimeMs > REDISCOVER_AGE_MS) return true
        if (Geo.distanceM(lastPoint, current) > REDISCOVER_DISTANCE_M) return true
        return false
    }
}
