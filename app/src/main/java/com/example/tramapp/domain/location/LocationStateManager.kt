package com.example.tramapp.domain.location

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationStateManager @Inject constructor() {

    private val _currentGpsLocation = MutableStateFlow<Location?>(null)
    private val _userSelectedLocation = MutableStateFlow<Location?>(null)

    /**
     * The location that should actually be used for display logic (fetching stations).
     * This favors the user's manual selection if present, otherwise uses GPS.
     */
    val activeDisplayLocation: StateFlow<Location?>
        get() = _userSelectedLocation.value?.let { MutableStateFlow(it) } ?: _currentGpsLocation.asStateFlow()

    fun updateGpsLocation(location: Location) {
        _currentGpsLocation.value = location
    }

    fun setUserSelectedLocation(latitude: Double, longitude: Double) {
        val loc = Location("user_mock").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        _userSelectedLocation.value = loc
    }

    fun revertToGps() {
        _userSelectedLocation.value = null
    }

    val isUsingMockedLocation: Boolean
        get() = _userSelectedLocation.value != null
}
