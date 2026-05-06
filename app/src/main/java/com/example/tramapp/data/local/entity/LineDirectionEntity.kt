package com.example.tramapp.data.local.entity

import androidx.room.Entity

@Entity(tableName = "line_directions", primaryKeys = ["stopId", "lineName", "headsign", "destinationType"])
data class LineDirectionEntity(
    val stopId: String,          // The station where the user is (e.g., Kamenická)
    val lineName: String,        // e.g., "8"
    val headsign: String,        // e.g., "Starý Hloubětín"
    val destinationType: String, // "home", "work", or "school"
    val isBound: Boolean,
    val timestamp: Long
)
