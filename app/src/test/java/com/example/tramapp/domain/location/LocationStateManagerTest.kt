package com.example.tramapp.domain.location

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import com.google.android.gms.maps.model.LatLng

class LocationStateManagerTest {

    private lateinit var locationManager: LocationStateManager

    @Before
    fun setup() {
        locationManager = LocationStateManager()
    }

    @Test
    fun updateGpsLocation_shouldUpdateCurrentGPAndActiveLocationInGPMode() = runTest {
        val latLng = LatLng(50.1234, 14.5678)

        // Initial state should be GPS mode
        assertEquals(false, locationManager.isManual.value)
        assertNotNull(locationManager.activeLocation.value)

        locationManager.updateGpsLocation(latLng)

        // Should update GPS location and active location
        assertEquals(latLng, locationManager.currentGpsLocation.value)
        assertEquals(latLng, locationManager.activeLocation.value)

        // Is manual should still be false
        assertEquals(false, locationManager.isManual.value)
    }

    @Test
    fun setUserSelectedLocation_shouldSwitchToManualModeAndUpdateActiveLocation() = runTest {
        val userLatLng = LatLng(50.2345, 14.6789)

        locationManager.setUserSelectedLocation(userLatLng)

        // Should be in manual mode
        assertEquals(true, locationManager.isManual.value)

        // User selected location should be set
        assertEquals(userLatLng, locationManager.userSelectedLocation.value)

        // Active location should follow user selection
        assertEquals(userLatLng, locationManager.activeLocation.value)
    }

    @Test
    fun setUserSelectedLocation_AfterGPSUpdateShouldSwitchToManualMode() = runTest {
        val gpsLoc = LatLng(50.1111, 14.2222)
        val userLoc = LatLng(50.3333, 14.4444)

        // Start in GPS mode
        locationManager.updateGpsLocation(gpsLoc)
        assertEquals(false, locationManager.isManual.value)

        // Switch to manual mode
        locationManager.setUserSelectedLocation(userLoc)
        assertEquals(true, locationManager.isManual.value)

        // GPS location should still be recorded
        assertEquals(gpsLoc, locationManager.currentGpsLocation.value)

        // Active location should be user selected
        assertEquals(userLoc, locationManager.activeLocation.value)
    }

    @Test
    fun revertToGps_shouldSwitchBackToGPsmodeWhenGPSIsAvailable() = runTest {
        val gpsLoc = LatLng(50.1234, 14.5678)
        val userLoc = LatLng(50.2345, 14.6789)

        // Set up a user selection
        locationManager.updateGpsLocation(gpsLoc)
        locationManager.setUserSelectedLocation(userLoc)
        assertEquals(true, locationManager.isManual.value)

        // Revert to GPS
        locationManager.revertToGps()

        // Should be back in GPS mode
        assertEquals(false, locationManager.isManual.value)

        // User selected location should be cleared
        assertNull(locationManager.userSelectedLocation.value)

        // Active location should be the GPS location
        assertEquals(gpsLoc, locationManager.activeLocation.value)
    }

    @Test
    fun revertToGps_WhenNoGPSLocationAvailable_shouldNotCrash() = runTest {
        val userLoc = LatLng(50.2345, 14.6789)

        // Set up a user selection without setting GPS first
        locationManager.setUserSelectedLocation(userLoc)
        assertEquals(true, locationManager.isManual.value)

        // Revert to GPS - should not crash
        locationManager.revertToGps()

        // Should be back in GPS mode
        assertEquals(false, locationManager.isManual.value)

        // User selected location should be cleared
        assertNull(locationManager.userSelectedLocation.value)

        // Active location should remain unchanged (still user selected)
        assertEquals(userLoc, locationManager.activeLocation.value)
    }

    @Test
    fun updateGpsLocation_InManualMode_shouldNotChangeActiveLocation() = runTest {
        val initialGPS = LatLng(50.1111, 14.2222)
        val userSelected = LatLng(50.3333, 14.4444)
        val newGPS = LatLng(50.9999, 14.8888)

        // Start in GPS mode
        locationManager.updateGpsLocation(initialGPS)
        assertEquals(false, locationManager.isManual.value)
        assertEquals(initialGPS, locationManager.activeLocation.value)

        // Switch to manual mode
        locationManager.setUserSelectedLocation(userSelected)
        assertEquals(true, locationManager.isManual.value)
        assertEquals(userSelected, locationManager.activeLocation.value)

        // Update GPS location while in manual mode
        locationManager.updateGpsLocation(newGPS)

        // Active location should remain user selected
        assertEquals(userSelected, locationManager.activeLocation.value)

        // But GPS location should be updated internally
        assertEquals(newGPS, locationManager.currentGpsLocation.value)
    }

    @Test
    fun revertToGps_switchesToGPsmode_onlyIfGPSLocationWasPreviouslySet() = runTest {
        val gpsLoc = LatLng(50.1234, 14.5678)
        val userLoc = LatLng(50.2345, 14.6789)

        // Start in GPS mode
        locationManager.updateGpsLocation(gpsLoc)

        // Switch to manual mode
        locationManager.setUserSelectedLocation(userLoc)
        assertEquals(true, locationManager.isManual.value)
        assertEquals(userLoc, locationManager.activeLocation.value)

        // Revert to GPS - should switch to the recorded GPS location
        locationManager.revertToGps()
        assertEquals(false, locationManager.isManual.value)
        assertEquals(gpsLoc, locationManager.activeLocation.value)
    }

    @Test
    fun initialState_shouldBeInGPsmodeAtPragueCenter() = runTest {
        // Initial state
        assertNull(locationManager.currentGpsLocation.value)
        assertNull(locationManager.userSelectedLocation.value)

        // Initial active location (Prague center)
        val initialPragueCenter = LatLng(50.0755, 14.4378)
        assertEquals(initialPragueCenter, locationManager.activeLocation.value)

        // Not in manual mode initially
        assertEquals(false, locationManager.isManual.value)
    }

    @Test
    fun updateGpsLocation_setsGPS_location_evenInManualMode() = runTest {
        val gpsLoc = LatLng(50.1234, 14.5678)

        // Switch to manual mode first
        locationManager.setUserSelectedLocation(LatLng(50.9999, 14.8888))
        assertEquals(true, locationManager.isManual.value)

        // Update GPS location
        locationManager.updateGpsLocation(gpsLoc)

        // Should update internal GPS tracking even in manual mode
        assertEquals(gpsLoc, locationManager.currentGpsLocation.value)
    }
}
