package com.example.tramapp.domain

import com.google.android.gms.maps.model.LatLng

data class TripDetails(
    val tripId: String,
    val routeName: String,
    val destination: String,
    val stations: List<TripStation>,
    val polyline: List<LatLng>
)

data class TripStation(
    val id: String,
    val name: String,
    val sequence: Int
)
