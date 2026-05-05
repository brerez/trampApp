package com.example.tramapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "departures")
data class DepartureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stopId: String,
    val routeShortName: String,
    val routeType: Int,
    val headsign: String,
    val arrivalTime: String, // ISO-8601
    val isPredicted: Boolean,
    val tripId: String? = null
)
