package com.example.tramapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val direction: String? = null,
    val isFavorite: Boolean = false,
    val lastUpdate: Long = 0
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
@Entity(tableName = "trip_routes")
data class TripRouteEntity(
    @PrimaryKey val routeKey: String, // e.g. "8-Starý Hloubětín"
    val stopIds: String, // Comma-separated stop names or IDs
    val timestamp: Long
)
