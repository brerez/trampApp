package com.example.tramapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.tramapp.data.local.dao.*
import com.example.tramapp.data.local.entity.*

@Database(
    entities = [StationEntity::class, ScheduleEntity::class, DepartureEntity::class, TripRouteEntity::class, LineDirectionEntity::class, TripNextStopEntity::class],
    version = 11, // U5: TripNextStopEntity for per-trip next-stop resolver
    exportSchema = false
)
abstract class TramDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun departureDao(): DepartureDao
    abstract fun tripRouteDao(): TripRouteDao
    abstract fun lineDirectionDao(): LineDirectionDao
    abstract fun tripNextStopDao(): TripNextStopDao
}
