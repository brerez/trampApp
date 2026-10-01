package com.example.tramapp.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTimeoutCodecTest {

    @Test
    fun unsetTimeoutDefaultsToOneHour() {
        assertEquals(60, decodeSessionTimeout(null))
    }

    @Test
    fun neverRoundTripsAsNull() {
        assertNull(decodeSessionTimeout(encodeSessionTimeout(null)))
    }

    @Test
    fun minuteValuesRoundTrip() {
        listOf(15, 30, 60, 120).forEach { assertEquals(it, decodeSessionTimeout(encodeSessionTimeout(it))) }
    }
}
