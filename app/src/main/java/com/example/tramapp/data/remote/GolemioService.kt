package com.example.tramapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface GolemioService {
    
    @GET("gtfs/stops")
    suspend fun getStops(
        @Query("latlng") latLng: String, // format "lat,lng"
        @Query("limit") limit: Int = 20
    ): GolemioResponse<StopProperties>

    @GET("pid/departureboards")
    suspend fun getDepartures(
        @Query("ids") stopId: String,
        @Query("limit") limit: Int = 10,
        @Query("minutesBefore") minutesBefore: Int = 0,
        @Query("minutesAfter") minutesAfter: Int = 60
    ): DepartureResponse
}
