package com.example.tramapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tramapp.data.local.datastore.UserPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    val userPreferences = preferencesManager.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateRadius(radius: Float) {
        viewModelScope.launch {
            preferencesManager.updateDisplayRadius(radius.toInt())
        }
    }

    fun updateLocation(type: String, lat: Double, lng: Double, address: String) {
        viewModelScope.launch {
            when (type) {
                "Home" -> preferencesManager.updateHomeLocation(lat, lng, address)
                "Work" -> preferencesManager.updateWorkLocation(lat, lng, address)
                "School" -> preferencesManager.updateSchoolLocation(lat, lng, address)
            }
        }
    }

    fun updateMaxStations(count: Int) {
        viewModelScope.launch {
            preferencesManager.updateMaxStations(count)
        }
    }
}
