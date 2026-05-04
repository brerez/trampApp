package com.example.tramapp.domain

import android.location.Location
import javax.inject.Inject

data class UserAddresses(
    val home: Location?,
    val work: Location?,
    val school: Location?
)

data class TramRoute(
    val id: String,
    val name: String,
    val destinationName: String,
    val destinationLocation: Location
)

enum class HighlightType {
    NONE, HOME, WORK, SCHOOL
}

class CalculateSmartHighlightsUseCase @Inject constructor() {

    /**
     * Determines if a tram route should be highlighted based on the user's current location,
     * the tram's destination, and the user's saved addresses.
     * 
     * Logic:
     * If the tram destination is within [destinationThresholdMeters] of a saved address (e.g. Home),
     * AND the user's current location is further than [suppressionThresholdMeters] from that address
     * (meaning they aren't already there), we highlight the route.
     */
    fun invoke(
        currentLocation: Location,
        tramRoute: TramRoute,
        userAddresses: UserAddresses,
        destinationThresholdMeters: Float = 1000f, // 1km radius for destination matching
        suppressionThresholdMeters: Float = 500f // 500m radius to suppress highlights if already near
    ): HighlightType {
        
        val addressesToCheck = listOf(
            HighlightType.HOME to userAddresses.home,
            HighlightType.WORK to userAddresses.work,
            HighlightType.SCHOOL to userAddresses.school
        )

        for ((type, addressLocation) in addressesToCheck) {
            if (addressLocation != null) {
                val distanceToDestination = tramRoute.destinationLocation.distanceTo(addressLocation)
                val currentDistanceToAddress = currentLocation.distanceTo(addressLocation)

                // If the tram goes near the address, and we are not currently near the address
                if (distanceToDestination <= destinationThresholdMeters && currentDistanceToAddress > suppressionThresholdMeters) {
                    return type
                }
            }
        }

        return HighlightType.NONE
    }
}
