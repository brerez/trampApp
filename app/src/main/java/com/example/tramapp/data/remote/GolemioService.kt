package com.example.tramapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
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

    @GET("gtfs/trips/{id}")
    suspend fun getTripDetails(
        @Path("id") tripId: String,
        @Query("includeStopTimes") includeStopTimes: Boolean = true,
        @Query("includeShapes") includeShapes: Boolean = true
    ): TripDetailsResponse
}
