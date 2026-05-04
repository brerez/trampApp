package com.example.tramapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val direction: String?, // NWES or terminal direction
    val isFavorite: Boolean = false
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String, // Unique ID for the departure
    val stationId: String,
    val routeName: String, // e.g., "9", "22"
    val destination: String,
    val expectedDepartureTime: Long, // timestamp
    val isRealTime: Boolean
)
