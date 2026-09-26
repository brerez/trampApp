package com.example.tramapp.domain

import com.example.tramapp.data.remote.DepartureItem

/**
 * U11: kept as the small UI-facing departure model after [GetSmartDeparturesUseCase] (the
 * per-platform bound-check pipeline it used to live in) was removed. [TramRow]/[JunctionRowView]
 * still render through it (KTD10: "using the existing TramRow").
 */
data class SmartDeparture(
    val item: DepartureItem,
    var isHomeBound: Boolean = false,
    var isWorkBound: Boolean = false,
    var isSchoolBound: Boolean = false,
    val isAccessible: Boolean? = item.trip.isWheelchairAccessible,
    val isAirConditioned: Boolean? = item.trip.isAirConditioned
)
