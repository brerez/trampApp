package com.example.tramapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tramapp.data.local.entity.TripNextStopEntity

@Dao
interface TripNextStopDao {
    @Query("SELECT * FROM trip_next_stops WHERE platformStopId = :platformStopId AND tripId IN (:tripIds) AND fetchedAt >= :minFetchedAt")
    suspend fun get(platformStopId: String, tripIds: List<String>, minFetchedAt: Long): List<TripNextStopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<TripNextStopEntity>)

    @Query("DELETE FROM trip_next_stops WHERE fetchedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
