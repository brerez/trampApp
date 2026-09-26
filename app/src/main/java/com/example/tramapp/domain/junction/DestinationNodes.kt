package com.example.tramapp.domain.junction

import com.example.tramapp.data.local.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DestinationNodes(val byDestination: Map<Destination, Set<String>>) {
    companion object {
        val NONE = DestinationNodes(emptyMap())
    }
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

        return DestinationNodes(map)
    }
}
