package com.example.tramapp.domain.location

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationStateManager @Inject constructor() {

    private val _currentGpsLocation = MutableStateFlow<LatLng?>(null)
    private val _userSelectedLocation = MutableStateFlow<LatLng?>(null)

    private val _activeLocation = MutableStateFlow(LatLng(50.0755, 14.4378))
    val activeLocation: StateFlow<LatLng> = _activeLocation.asStateFlow()

    private val _isManual = MutableStateFlow(false)
    val isManual: StateFlow<Boolean> = _isManual.asStateFlow()

    fun updateGpsLocation(latLng: LatLng) {
        _currentGpsLocation.value = latLng
        if (!_isManual.value) {
            _activeLocation.value = latLng
        }
    }

    fun setUserSelectedLocation(latLng: LatLng) {
        _userSelectedLocation.value = latLng
        _isManual.value = true
        _activeLocation.value = latLng
    }

    fun revertToGps() {
        _userSelectedLocation.value = null
        _isManual.value = false
        _currentGpsLocation.value?.let {
            _activeLocation.value = it
        }
    }
}
