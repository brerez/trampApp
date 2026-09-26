package com.example.tramapp.domain.junction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JunctionDirectoryTest {
    @Test
    fun testBuild() {
        // 8. U324Z1P and U324Z2P group into one junction "U324"; 
        // a platform with isTram=false is excluded; isTram=null is kept.
        // 9. Letter from platformCode, fallback from stop id ("U324Z2P" -> "2"); 
        // name suffix " [A]" stripped.
        val stops = listOf(
            PlatformStop("U324Z1P", "U324", "Kamenická [A]", "1", 50.0, 14.0, true),
            PlatformStop("U324Z2P", "U324", "Kamenická [B]", null, 50.0, 14.0, null),
            PlatformStop("U324Z3P", "U324", "Kamenická", "3", 50.0, 14.0, false),
            PlatformStop("U100Z1P", "U100", "Letenské náměstí", "1", 50.0, 14.0, true)
        )
        
        val junctions = JunctionDirectory.build(stops)
        
        assertEquals(2, junctions.size)
        
        val u324 = junctions.find { it.nodeId == "U324" }!!
        assertEquals("Kamenická", u324.name)
        assertEquals(2, u324.platforms.size) // Z3P excluded
        
        val p1 = u324.platforms.find { it.stopId == "U324Z1P" }!!
        assertEquals("1", p1.letter)
        
        val p2 = u324.platforms.find { it.stopId == "U324Z2P" }!!
        assertEquals("2", p2.letter)
    }

    @Test
    fun testShouldRediscover() {
        val now = 1000000L
        val current = GeoPoint(50.0, 14.0)
        
        // null point -> true
        assertTrue(JunctionDirectory.shouldRediscover(null, now, current, now))
        
        // 299 m -> false.
        val d299 = Math.toDegrees(299.0 / 6371008.8)
        val p299 = GeoPoint(50.0 + d299, 14.0)
        assertFalse(JunctionDirectory.shouldRediscover(p299, now, current, now))
        
        // 301 m -> true
        val d301 = Math.toDegrees(301.0 / 6371008.8)
        val p301 = GeoPoint(50.0 + d301, 14.0)
        assertTrue(JunctionDirectory.shouldRediscover(p301, now, current, now))
        
        // 25 h old -> true
        assertTrue(JunctionDirectory.shouldRediscover(p299, now - 25 * 3600 * 1000L, current, now))
    }
}
