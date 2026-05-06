package com.example.tramapp.data.local.dao

import androidx.room.*
import com.example.tramapp.data.local.entity.LineDirectionEntity

@Dao
interface LineDirectionDao {
    @Query("SELECT * FROM line_directions WHERE stopId = :stopId AND lineName = :lineName AND headsign = :headsign AND destinationType = :destType")
    suspend fun getDirection(stopId: String, lineName: String, headsign: String, destType: String): LineDirectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirection(direction: LineDirectionEntity)

    @Query("DELETE FROM line_directions WHERE timestamp < :expiry")
    suspend fun deleteOldDirections(expiry: Long)
}
