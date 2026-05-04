package com.example.tramapp.domain

import org.junit.Test
import kotlin.math.*

/**
 * Tests the new station-cache + dot product approach against real Prague tram data.
 *
 * Real coordinates (verified via web search):
 * - Home: U Studánky 14, Prague 7 → 50.1017, 14.4124
 * - Kamenická station: 50.0996, 14.4289
 * - Work (Rapid7): Pernerova 39, Karlín → 50.0906, 14.4533
 * - School (Riverside): M. Horákové 300/129 → 50.1296, 14.3987
 */
class MathTest {

    // Simulated destination line caches (what getLineNamesNearLocation would return)
    // Work (Rapid7 / Křižíkova): stations within 750m serve lines 3, 8, 24
    // (Line 26 does NOT pass through Karlín — it goes via city center)
    val workLines = setOf("3", "8", "24")

    // School (Riverside / M. Horákové): stations within 750m serve lines 1, 8, 25, 26
    val schoolLines = setOf("1", "8", "25", "26")

    /** Cosine similarity direction check — same as in GetSmartDeparturesUseCase */
    fun isHeadingToward(
        userLat: Double, userLng: Double,
        terminalLat: Double, terminalLng: Double,
        destLat: Double, destLng: Double
    ): Boolean {
        val lngScale = cos(userLat * PI / 180.0)
        val dxTerm = (terminalLng - userLng) * lngScale
        val dyTerm = terminalLat - userLat
        // Skip trams about to terminate (< ~1km)
        val termDistSq = dxTerm * dxTerm + dyTerm * dyTerm
        if (termDistSq < 0.00008) return false
        val dxDest = (destLng - userLng) * lngScale
        val dyDest = destLat - userLat
        val dot = dxDest * dxTerm + dyDest * dyTerm
        val magDest = sqrt(dxDest * dxDest + dyDest * dyDest)
        val magTerm = sqrt(dxTerm * dxTerm + dyTerm * dyTerm)
        if (magDest == 0.0 || magTerm == 0.0) return false
        val cosSim = dot / (magDest * magTerm)
        return cosSim > 0.3
    }

    fun checkBound(lineName: String, terminalLat: Double, terminalLng: Double,
                   destinationLines: Set<String>,
                   userLat: Double, userLng: Double,
                   destLat: Double, destLng: Double): Boolean {
        if (!destinationLines.contains(lineName)) return false
        return isHeadingToward(userLat, userLng, terminalLat, terminalLng, destLat, destLng)
    }

    @Test
    fun testRealScenario() {
        val userLat = 50.0996; val userLng = 14.4289  // Kamenická
        val workLat = 50.0906; val workLng = 14.4533   // Rapid7
        val schoolLat = 50.1296; val schoolLng = 14.3987 // Riverside

        // Terminal coordinates
        val vystavisteLat = 50.1065; val vystavisteLng = 14.4305      // Lines 1, 25 East
        val zelivskehoLat = 50.0785; val zelivskehoLng = 14.4745      // Line 26 East
        val staryHloubetinLat = 50.1054; val staryHloubetinLng = 14.5290  // Line 8 East
        val sidlistePetrinyLat = 50.0847; val sidlistePetrinyLng = 14.3338  // Line 1 West
        val bilaHoraLat = 50.0828; val bilaHoraLng = 14.3217           // Line 25 West
        val dedinaLat = 50.0898; val dedinaLng = 14.3014               // Line 26 West
        val nadraziPodbabaLat = 50.1127; val nadraziPodbabaLng = 14.3941  // Line 8 West

        println("=== WORK (only Line 8 East should match) ===")
        val w1a = checkBound("1", vystavisteLat, vystavisteLng, workLines, userLat, userLng, workLat, workLng)
        val w25a = checkBound("25", vystavisteLat, vystavisteLng, workLines, userLat, userLng, workLat, workLng)
        val w26a = checkBound("26", zelivskehoLat, zelivskehoLng, workLines, userLat, userLng, workLat, workLng)
        val w8a = checkBound("8", staryHloubetinLat, staryHloubetinLng, workLines, userLat, userLng, workLat, workLng)
        val w1b = checkBound("1", sidlistePetrinyLat, sidlistePetrinyLng, workLines, userLat, userLng, workLat, workLng)
        val w25b = checkBound("25", bilaHoraLat, bilaHoraLng, workLines, userLat, userLng, workLat, workLng)
        val w26b = checkBound("26", dedinaLat, dedinaLng, workLines, userLat, userLng, workLat, workLng)
        val w8b = checkBound("8", nadraziPodbabaLat, nadraziPodbabaLng, workLines, userLat, userLng, workLat, workLng)

        println("  Line 1   East (Výstaviště):      ${w1a} (expected: false)")
        println("  Line 25  East (Výstaviště):      ${w25a} (expected: false)")
        println("  Line 26  East (Želivského):      ${w26a} (expected: false)")
        println("  Line 8   East (Starý Hloubětín): ${w8a} (expected: TRUE)")
        println("  Line 1   West (Sídl. Petřiny):   ${w1b} (expected: false)")
        println("  Line 25  West (Bílá Hora):       ${w25b} (expected: false)")
        println("  Line 26  West (Dědina):          ${w26b} (expected: false)")
        println("  Line 8   West (Nádraží Podbaba): ${w8b} (expected: false)")

        println("\n=== SCHOOL (Lines 1, 25, 26, 8 West should match) ===")
        val s1a = checkBound("1", vystavisteLat, vystavisteLng, schoolLines, userLat, userLng, schoolLat, schoolLng)
        val s26a = checkBound("26", zelivskehoLat, zelivskehoLng, schoolLines, userLat, userLng, schoolLat, schoolLng)
        val s8a = checkBound("8", staryHloubetinLat, staryHloubetinLng, schoolLines, userLat, userLng, schoolLat, schoolLng)
        val s1b = checkBound("1", sidlistePetrinyLat, sidlistePetrinyLng, schoolLines, userLat, userLng, schoolLat, schoolLng)
        val s25b = checkBound("25", bilaHoraLat, bilaHoraLng, schoolLines, userLat, userLng, schoolLat, schoolLng)
        val s26b = checkBound("26", dedinaLat, dedinaLng, schoolLines, userLat, userLng, schoolLat, schoolLng)
        val s8b = checkBound("8", nadraziPodbabaLat, nadraziPodbabaLng, schoolLines, userLat, userLng, schoolLat, schoolLng)

        println("  Line 1   East (Výstaviště):      ${s1a} (expected: false)")
        println("  Line 26  East (Želivského):      ${s26a} (expected: false)")
        println("  Line 8   East (Starý Hloubětín): ${s8a} (expected: false)")
        println("  Line 1   West (Sídl. Petřiny):   ${s1b} (expected: TRUE)")
        println("  Line 25  West (Bílá Hora):       ${s25b} (expected: TRUE)")
        println("  Line 26  West (Dědina):          ${s26b} (expected: TRUE)")
        println("  Line 8   West (Nádraží Podbaba): ${s8b} (expected: TRUE)")

        // Assert all results
        assert(!w1a)  { "Line 1 East should NOT be work-bound" }
        assert(!w25a) { "Line 25 East should NOT be work-bound" }
        assert(!w26a) { "Line 26 East should NOT be work-bound" }
        assert(w8a)   { "Line 8 East SHOULD be work-bound" }
        assert(!w1b)  { "Line 1 West should NOT be work-bound" }
        assert(!w25b) { "Line 25 West should NOT be work-bound" }
        assert(!w26b) { "Line 26 West should NOT be work-bound" }
        assert(!w8b)  { "Line 8 West should NOT be work-bound" }

        assert(!s1a)  { "Line 1 East should NOT be school-bound" }
        assert(!s26a) { "Line 26 East should NOT be school-bound" }
        assert(!s8a)  { "Line 8 East should NOT be school-bound" }
        assert(s1b)   { "Line 1 West SHOULD be school-bound" }
        assert(s25b)  { "Line 25 West SHOULD be school-bound" }
        assert(s26b)  { "Line 26 West SHOULD be school-bound" }
        assert(s8b)   { "Line 8 West SHOULD be school-bound" }

        println("\n✅ ALL ASSERTIONS PASSED")
    }
}
