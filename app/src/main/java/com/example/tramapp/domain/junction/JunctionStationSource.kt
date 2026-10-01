package com.example.tramapp.domain.junction

import com.example.tramapp.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Station-cache seam for [JunctionLocator] (U10). Lets tests supply a fake/stub station source
 * (with a real [JunctionLocator]) instead of a full [com.example.tramapp.data.repository.TramRepository].
 * TramRepository implements this directly.
 */
interface JunctionStationSource {
    val allStations: Flow<List<StationEntity>>

    /** One batched nearby-stations discovery call; returns the discovered/refreshed station ids. */
    suspend fun refreshNearbyStations(lat: Double, lng: Double, radius: Int): List<String>
}
