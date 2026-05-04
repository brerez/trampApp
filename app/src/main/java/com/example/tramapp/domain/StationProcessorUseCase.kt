package com.example.tramapp.domain

import com.example.tramapp.data.local.entity.StationEntity
import javax.inject.Inject

class StationProcessorUseCase @Inject constructor() {

    /**
     * Processes raw stations from the API/DB to append direction or terminal info to the name,
     * to help the user distinguish between platforms with the same name.
     */
    fun formatStationName(name: String, direction: String?): String {
        return if (direction.isNullOrBlank()) {
            name
        } else {
            "$name ($direction)"
        }
    }

    /**
     * Sorts the stations.
     * Rule: If a station is favorited AND its distance to the user is <= the configured display radius,
     * it is bumped to the top of the list.
     * Otherwise, stations are sorted by distance.
     */
    fun sortStations(
        stationsWithDistances: List<Pair<StationEntity, Float>>,
        displayRadiusMeters: Float
    ): List<Pair<StationEntity, Float>> {
        return stationsWithDistances
            .filter { it.second <= displayRadiusMeters }
            .sortedWith(Comparator { (station1, distance1), (station2, distance2) ->
                val isS1Fav = station1.isFavorite
                val isS2Fav = station2.isFavorite

                if (isS1Fav && !isS2Fav) return@Comparator -1
                if (!isS1Fav && isS2Fav) return@Comparator 1

                // If both are favs or neither are favs, sort by distance
                distance1.compareTo(distance2)
            })
    }
}
