package com.example.tramapp.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.local.datastore.toSessionSettings

class SessionHealthTest {

    @Test
    fun testNeverWithoutExemptionWarns() {
        val warnings = SessionHealth.check(null, false)
        assertTrue(warnings.contains(SessionWarning.BATTERY_OPTIMIZED_LONG_SESSION))
    }

    @Test
    fun testNeverWithExemptionDoesNotWarn() {
        val warnings = SessionHealth.check(null, true)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun test120MinWithoutExemptionWarns() {
        val warnings = SessionHealth.check(120, false)
        assertTrue(warnings.contains(SessionWarning.BATTERY_OPTIMIZED_LONG_SESSION))
    }

    @Test
    fun test15MinWithoutExemptionDoesNotWarn() {
        val warnings = SessionHealth.check(15, false)
        assertTrue(warnings.isEmpty())
    }
    
    @Test
    fun test30MinWithoutExemptionDoesNotWarn() {
        val warnings = SessionHealth.check(30, false)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun test60MinWithoutExemptionDoesNotWarn() {
        val warnings = SessionHealth.check(60, false)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun testNotificationText() {
        val text = SessionHealth.notificationText(setOf(SessionWarning.BATTERY_OPTIMIZED_LONG_SESSION))
        assertEquals("Battery saver may stop this session", text)

        val emptyText = SessionHealth.notificationText(emptySet())
        assertNull(emptyText)
    }

    // Since DataStore cannot easily run on the JVM here without Robolectric or special File setups
    // (no existing pattern was found in the project), testing the pure mapping functions instead.
    @Test
    fun testPreferencesMapping() {
        val prefs = UserPreferences(
            homeLat = null, homeLng = null, homeAddress = null,
            workLat = null, workLng = null, workAddress = null,
            schoolLat = null, schoolLng = null, schoolAddress = null,
            lastLat = null, lastLng = null,
            isManualStartup = false, displayRadius = 750, maxStations = 4,
            homeLines = emptySet(), workLines = emptySet(), schoolLines = emptySet(),
            homeStopNames = emptySet(), workStopNames = emptySet(), schoolStopNames = emptySet(),
            homeStopIds = emptySet(), workStopIds = emptySet(), schoolStopIds = emptySet(),
            homeLinesTimestamp = 0L, workLinesTimestamp = 0L, schoolLinesTimestamp = 0L,
            favorites = emptySet(), favoritesFirst = false,
            sessionScreenOnIntervalSec = 20,
            sessionScreenOffIntervalMin = 3,
            sessionTimeoutMin = 60
        )
        val settings = prefs.toSessionSettings()
        assertEquals(20_000L, settings.screenOnIntervalMs)
        assertEquals(180_000L, settings.screenOffIntervalMs)
        assertEquals(3_600_000L, settings.timeoutMs)
        
        val prefsNever = prefs.copy(sessionTimeoutMin = null)
        val settingsNever = prefsNever.toSessionSettings()
        assertNull(settingsNever.timeoutMs)
    }
}
