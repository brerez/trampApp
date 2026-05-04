package com.example.tramapp.data.local.dao

import androidx.room.*
import com.example.tramapp.data.local.entity.DepartureEntity

@Dao
interface DepartureDao {
    @Query("SELECT * FROM departures WHERE stopId = :stopId")
    suspend fun getDeparturesForStop(stopId: String): List<DepartureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartures(departures: List<DepartureEntity>)

    @Query("DELETE FROM departures WHERE stopId = :stopId")
    suspend fun deleteDeparturesForStop(stopId: String)
}
