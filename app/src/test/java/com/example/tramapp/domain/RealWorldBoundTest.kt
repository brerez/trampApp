package com.example.tramapp.domain

import org.junit.Test
import org.junit.Assert.*

class RealWorldBoundTest {

    @Test
    fun testUserScenarioWithIds() {
        // Station: Kamenická
        val currentStopId = "U605Z1P" // Kamenická [A]
        val stationName = "Kamenická [A]"
        
        // Destinations
        val schoolDestIds = setOf("U391Z1P") // Hradčanská
        val workDestIds = setOf("U463Z1P") // Křižíkova

        // Route for Tram 8 towards Nádraží Podbaba (Westbound)
        val routeIds = listOf(
            "U101", "U102", "U103", "U104", "U105", "U106", "U107", "U108", "U109", "U110",
            "U111", "U112", "U463Z1P", "U114", "U115", "U116", "U117", "U118", "U119", "U605Z1P",
            "U121", "U122", "U123", "U391Z1P", "U125", "U126", "U127", "U128"
        )

        println("=== DIAGNOSTICS ===")
        println("Current Stop: $stationName ($currentStopId)")
        println("School Dests: ($schoolDestIds)")
        
        // 1. Match by ID
        val currentIndexId = routeIds.indexOfFirst { it == currentStopId }
        val schoolIndexId = routeIds.indexOfFirst { it == "U391Z1P" }
        val workIndexId = routeIds.indexOfFirst { it == "U463Z1P" }
        
        println("\nID Matching:")
        println("  Current Index: $currentIndexId")
        println("  School Index:  $schoolIndexId")
        println("  Work Index:    $workIndexId")
        
        val isSchoolBoundId = schoolIndexId > currentIndexId
        val isWorkBoundId = workIndexId > currentIndexId
        
        println("  Is School Bound (ID): $isSchoolBoundId")
        println("  Is Work Bound (ID):   $isWorkBoundId")

        assertTrue("Should be school-bound by ID", isSchoolBoundId)
        assertFalse("Should NOT be work-bound", isWorkBoundId)
        
        println("\n✅ TEST PASSED")
    }
}
