package com.example.tramapp.domain.junction

import com.example.tramapp.data.local.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DestinationNodes(
    val byDestination: Map<Destination, Set<String>>,
    /** Where each destination is; used to drop destinations the user is already at. */
    val anchors: Map<Destination, GeoPoint> = emptyMap(),
) {
    companion object {
        val NONE = DestinationNodes(emptyMap())
        const val SUPPRESS_WITHIN_M = 500.0
    }

    /** Without this every line leaving a junction near home passes home-area stops and gets
     *  flagged home-bound. */
    fun awayFrom(point: GeoPoint): DestinationNodes = copy(
        byDestination = byDestination.filterKeys { dest ->
            val anchor = anchors[dest] ?: return@filterKeys true
            Geo.distanceM(anchor, point) > SUPPRESS_WITHIN_M
        }
    )
}

interface DestinationNodeSource {
    suspend fun current(): DestinationNodes
}

/**
 * Maps home/work/school stop ids from UserPreferencesManager.userPreferences (first())
 * to node ids via JunctionDirectory.nodeIdOf.
 */
class PreferencesDestinationNodeSource @Inject constructor(
    private val prefs: UserPreferencesManager
) : DestinationNodeSource {

    override suspend fun current(): DestinationNodes {
        val userPrefs = prefs.userPreferences.first()

        fun stopIdsToNodeIds(stopIds: Set<String>): Set<String> =
            stopIds
                .filter { it.isNotBlank() }
                .map { JunctionDirectory.nodeIdOf(it) }
                .filter { it.isNotBlank() }
                .toSet()

        val map = buildMap<Destination, Set<String>> {
            val homeNodes = stopIdsToNodeIds(userPrefs.homeStopIds)
            if (homeNodes.isNotEmpty()) put(Destination.HOME, homeNodes)

            val workNodes = stopIdsToNodeIds(userPrefs.workStopIds)
            if (workNodes.isNotEmpty()) put(Destination.WORK, workNodes)

            val schoolNodes = stopIdsToNodeIds(userPrefs.schoolStopIds)
            if (schoolNodes.isNotEmpty()) put(Destination.SCHOOL, schoolNodes)
        }

        val anchors = buildMap<Destination, GeoPoint> {
            if (userPrefs.homeLat != null && userPrefs.homeLng != null) put(Destination.HOME, GeoPoint(userPrefs.homeLat, userPrefs.homeLng))
            if (userPrefs.workLat != null && userPrefs.workLng != null) put(Destination.WORK, GeoPoint(userPrefs.workLat, userPrefs.workLng))
            if (userPrefs.schoolLat != null && userPrefs.schoolLng != null) put(Destination.SCHOOL, GeoPoint(userPrefs.schoolLat, userPrefs.schoolLng))
        }

        return DestinationNodes(map, anchors)
    }
}
