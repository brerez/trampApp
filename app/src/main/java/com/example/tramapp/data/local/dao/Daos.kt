package com.example.tramapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tramapp.data.local.entity.ScheduleEntity
import com.example.tramapp.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations")
    fun getAllStations(): Flow<List<StationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<StationEntity>)

    @Query("UPDATE stations SET isFavorite = :isFavorite WHERE id = :stationId")
    suspend fun updateFavoriteStatus(stationId: String, isFavorite: Boolean)

    @Query("SELECT * FROM stations WHERE isFavorite = 1")
    fun getFavoriteStations(): Flow<List<StationEntity>>

    @Query("UPDATE stations SET isTram = :isTram WHERE id = :stationId")
    suspend fun updateIsTramStatus(stationId: String, isTram: Boolean)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE stationId = :stationId ORDER BY expectedDepartureTime ASC")
    fun getSchedulesForStation(stationId: String): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM schedules WHERE stationId = :stationId")
    suspend fun clearSchedulesForStation(stationId: String)
}
