package com.example.tramapp.domain.junction

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun testBearingAndArrow() {
        val p1 = GeoPoint(50.08, 14.42)
        // 1e-5 deg lat = ~1.11 m -> 100m = ~90e-5
        // 1e-5 deg lng = ~0.715 m -> 100m = ~140e-5
        val p2 = GeoPoint(50.08 + 90e-5, 14.42 + 140e-5)
        
        val bearing = Geo.bearingDeg(p1, p2) 
        // bearing should be around 45 deg for an equal meters north-east offset
        assertEquals("↗", Geo.arrow(bearing, 0.0))
        assertEquals("↖", Geo.arrow(bearing, 90.0))
        assertEquals(null, Geo.arrow(bearing, null))
    }

    @Test
    fun testDistance() {
        val p1Dist = GeoPoint(50.0, 14.0)
        val p2Dist = GeoPoint(50.001, 14.0)
        assertEquals(111.0, Geo.distanceM(p1Dist, p2Dist), 1.0)
    }

    @Test
    fun testFormatDistance() {
        assertEquals("120 m", Geo.formatDistance(123.0))
        assertEquals("1.2 km", Geo.formatDistance(1234.0))
    }
}
