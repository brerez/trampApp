package com.example.tramapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.tramapp.data.local.dao.*
import com.example.tramapp.data.local.entity.*

@Database(
    entities = [StationEntity::class, ScheduleEntity::class, TripNextStopEntity::class],
    version = 12, // U11: retire the per-platform pipeline (departure cache, trip_routes, line_directions)
    exportSchema = false
)
abstract class TramDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun tripNextStopDao(): TripNextStopDao
}
