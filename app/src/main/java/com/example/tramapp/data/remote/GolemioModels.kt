package com.example.tramapp.data.remote

import com.google.gson.annotations.SerializedName

data class GolemioResponse<T>(
    val features: List<Feature<T>>
)

data class Feature<T>(
    val geometry: Geometry,
    val properties: T
)

data class Geometry(
    val coordinates: List<Double>
)

data class StopProperties(
    @SerializedName("stop_id") val stopId: String,
    @SerializedName("stop_name") val stopName: String,
    @SerializedName("platform_code") val platformCode: String?,
    @SerializedName("location_type") val locationType: Int?
)

data class DepartureResponse(
    val departures: List<DepartureItem>
)

data class DepartureItem(
    @SerializedName("route") val route: RouteInfo,
    @SerializedName("trip") val trip: TripInfo,
    @SerializedName("arrival_timestamp") val arrival: TimestampInfo,
    @SerializedName("stop") val stop: StopInfo
)

data class RouteInfo(
    @SerializedName("short_name") val shortName: String,
    @SerializedName("type") val type: Int
)
data class TripInfo(
    @SerializedName("headsign") val headsign: String,
    @SerializedName("id") val tripId: String? = null,
    // U8/KTD4 (R7-R9): nullable — null means "unknown" (omit the indicator), not "false".
    @SerializedName("is_wheelchair_accessible") val isWheelchairAccessible: Boolean? = null,
    @SerializedName("is_air_conditioned") val isAirConditioned: Boolean? = null
)
data class TimestampInfo(val scheduled: String, val predicted: String?)
data class StopInfo(val id: String)

data class TripDetailsResponse(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("stop_times") val stopTimes: List<TripStopTime>,
    @SerializedName("shapes") val shapes: List<Feature<Any?>>
)

data class TripStopTime(
    @SerializedName("stop_id") val stopId: String,
    @SerializedName("stop_sequence") val stopSequence: Int,
    @SerializedName("stop") val stop: TripStopInfo? = null
)

data class TripStopInfo(
    @SerializedName("stop_name") val stopName: String
)
