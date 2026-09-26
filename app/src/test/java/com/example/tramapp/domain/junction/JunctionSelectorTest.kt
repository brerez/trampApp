package com.example.tramapp.domain.junction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JunctionSelectorTest {
    private lateinit var selector: JunctionSelector

    // approx degrees for meters north (for deterministic simple tests)
    private fun latOffset(meters: Double): Double = Math.toDegrees(meters / 6371008.8)

    @Before
    fun setUp() {
        selector = JunctionSelector()
    }

    private fun junc(nodeId: String, lat: Double, lng: Double = 14.0): Junction {
        return Junction(nodeId, "Name", listOf(Platform(nodeId + "Z1P", "1", GeoPoint(lat, lng), true)))
    }

    @Test
    fun testRequirement1_and_2() {
        val kLat = 50.0
        val sLat = 50.0 + latOffset(200.0)
        val tLat = 50.0 + latOffset(400.0)
        
        val k = junc("Kamenicka", kLat)
        val s = junc("Strossmayerovo", sLat)
        val t = junc("Third", tLat)
        
        selector.setJunctions(listOf(k, s, t))
        
        var sel = selector.onFix(LocationFix(kLat, 14.0, 10f, 0L)) as JunctionSelection.Selected
        assertEquals("Kamenicka", sel.junction.nodeId)
        assertEquals(0, sel.cycleIndex)
        
        sel = selector.cycleNext() as JunctionSelection.Selected
        assertEquals("Strossmayerovo", sel.junction.nodeId)
        assertEquals(1, sel.cycleIndex)
        
        sel = selector.onFix(LocationFix(kLat + latOffset(10.0), 14.0, 10f, 1L)) as JunctionSelection.Selected
        assertEquals("Strossmayerovo", sel.junction.nodeId)
        assertEquals(1, sel.cycleIndex)

        sel = selector.cycleNext() as JunctionSelection.Selected
        assertEquals("Third", sel.junction.nodeId)
        assertEquals(2, sel.cycleIndex)

        sel = selector.cycleNext() as JunctionSelection.Selected
        assertEquals("Kamenicka", sel.junction.nodeId)
        assertEquals(0, sel.cycleIndex)
        
        sel = selector.cycleNext() as JunctionSelection.Selected
        assertEquals("Strossmayerovo", sel.junction.nodeId)
        
        // accurate fixes twice make third clearly nearest
        sel = selector.onFix(LocationFix(tLat, 14.0, 10f, 2L)) as JunctionSelection.Selected
        assertEquals("Strossmayerovo", sel.junction.nodeId)
        
        sel = selector.onFix(LocationFix(tLat, 14.0, 10f, 3L)) as JunctionSelection.Selected
        assertEquals("Third", sel.junction.nodeId)
        assertEquals(0, sel.cycleIndex)
    }

    @Test
    fun testRequirement3_Jitter() {
        val j1 = junc("J1", 50.0)
        val j2 = junc("J2", 50.0 + latOffset(40.0))
        selector.setJunctions(listOf(j1, j2))
        
        selector.onFix(LocationFix(50.0, 14.0, 10f, 0L))
        
        for (i in 1..10) {
            val sel = selector.onFix(LocationFix(50.0 + latOffset(15.0), 14.0, 10f, i.toLong())) as JunctionSelection.Selected
            assertEquals("J1", sel.junction.nodeId)
            
            val sel2 = selector.onFix(LocationFix(50.0 + latOffset(25.0), 14.0, 10f, i.toLong() + 100)) as JunctionSelection.Selected
            assertEquals("J1", sel2.junction.nodeId)
        }
    }

    @Test
    fun testRequirement4_SwitchMargin() {
        val j1 = junc("J1", 50.0)
        val j2 = junc("J2", 50.0 + latOffset(100.0))
        selector.setJunctions(listOf(j1, j2))
        
        selector.onFix(LocationFix(50.0, 14.0, 10f, 0L))
        
        val fixLat = 50.0 + latOffset(65.0)
        
        var sel = selector.onFix(LocationFix(fixLat, 14.0, 10f, 1L)) as JunctionSelection.Selected
        assertEquals("J1", sel.junction.nodeId)
        
        sel = selector.onFix(LocationFix(fixLat, 14.0, 10f, 2L)) as JunctionSelection.Selected
        assertEquals("J2", sel.junction.nodeId)
    }

    @Test
    fun testRequirement5_InaccurateFixIgnored() {
        val j1 = junc("J1", 50.0)
        val j2 = junc("J2", 50.0 + latOffset(100.0))
        selector.setJunctions(listOf(j1, j2))
        
        selector.onFix(LocationFix(50.0, 14.0, 10f, 0L))
        
        selector.onFix(LocationFix(50.0 + latOffset(100.0), 14.0, 120f, 1L))
        val sel = selector.onFix(LocationFix(50.0 + latOffset(100.0), 14.0, 120f, 2L)) as JunctionSelection.Selected
        
        assertEquals("J1", sel.junction.nodeId)
    }

    @Test
    fun testRequirement6_WalkingRange() {
        val j1 = junc("J1", 50.0 + latOffset(800.0))
        selector.setJunctions(listOf(j1))
        
        val sel = selector.onFix(LocationFix(50.0, 14.0, 10f, 0L))
        assertTrue(sel is JunctionSelection.NoneInRange)
        assertEquals(800.0, (sel as JunctionSelection.NoneInRange).nearestDistanceM!!, 1.0)
        
        selector.walkingRangeM = 1000.0
        val sel2 = selector.onFix(LocationFix(50.0, 14.0, 10f, 1L))
        assertTrue(sel2 is JunctionSelection.Selected)
        assertEquals("J1", (sel2 as JunctionSelection.Selected).junction.nodeId)
    }

    @Test
    fun testRequirement7_Pin() {
        val j1 = junc("J1", 50.0)
        val j2 = junc("J2", 50.0 + latOffset(200.0))
        val j3 = junc("J3", 50.0 + latOffset(400.0))
        selector.setJunctions(listOf(j1, j2, j3))
        
        selector.onFix(LocationFix(50.0, 14.0, 10f, 0L)) // Anchor J1
        
        var sel = selector.pin("J2") as JunctionSelection.Selected
        assertEquals("J2", sel.junction.nodeId)
        
        selector.onFix(LocationFix(50.0 + latOffset(400.0), 14.0, 10f, 1L))
        sel = selector.onFix(LocationFix(50.0 + latOffset(400.0), 14.0, 10f, 2L)) as JunctionSelection.Selected
        
        assertEquals("J3", sel.junction.nodeId)
        assertEquals(0, sel.cycleIndex)
    }

    @Test
    fun testAnchorLeavesWalkingRange() {
        val j1 = junc("J1", 50.0)
        selector.setJunctions(listOf(j1))
        var sel = selector.onFix(LocationFix(50.0, 14.0, 10f, 0L))
        assertTrue(sel is JunctionSelection.Selected)

        sel = selector.onFix(LocationFix(50.0 + latOffset(800.0), 14.0, 10f, 1L))
        assertTrue(sel is JunctionSelection.NoneInRange)
    }
}
