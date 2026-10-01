package com.example.tramapp.data.local.entity

import androidx.room.Entity

@Entity(tableName = "trip_next_stops", primaryKeys = ["tripId", "platformStopId"])
data class TripNextStopEntity(
    val tripId: String,
    val platformStopId: String,
    val nextStopId: String,
    val nextStopName: String,
    val downstreamNodeIds: String,   // comma-separated node ids
    val fetchedAt: Long
)
