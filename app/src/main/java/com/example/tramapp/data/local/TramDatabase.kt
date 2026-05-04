package com.example.tramapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.tramapp.data.local.dao.DepartureDao
import com.example.tramapp.data.local.dao.ScheduleDao
import com.example.tramapp.data.local.dao.StationDao
import com.example.tramapp.data.local.entity.DepartureEntity
import com.example.tramapp.data.local.entity.ScheduleEntity
import com.example.tramapp.data.local.entity.StationEntity

@Database(entities = [StationEntity::class, ScheduleEntity::class, DepartureEntity::class], version = 2, exportSchema = false)
abstract class TramDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun departureDao(): DepartureDao
}
