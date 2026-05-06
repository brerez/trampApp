package com.example.tramapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tramapp.data.local.entity.TripRouteEntity

@Dao
interface TripRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripRoute(tripRoute: TripRouteEntity)

    @Query("SELECT * FROM trip_routes WHERE routeKey = :routeKey")
    suspend fun getTripRoute(routeKey: String): TripRouteEntity?

    @Query("DELETE FROM trip_routes WHERE timestamp < :threshold")
    suspend fun deleteOldRoutes(threshold: Long)
}
