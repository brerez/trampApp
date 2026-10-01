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
    val lastUpdate: Long = 0,
    val isTram: Boolean? = null,
    // U3: junction (PID stop node) id — the stop id prefix before the first 'Z' — and platform code.
    val nodeId: String? = null,
    val platformCode: String? = null
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
