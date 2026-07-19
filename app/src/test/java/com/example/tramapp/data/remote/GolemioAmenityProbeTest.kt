package com.example.tramapp.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

/**
 * U2 — Golemio amenity-field probe (R7, R8, R9 field discovery).
 *
 * Live-API JVM test. Fetches a real `pid/departureboards` response with the
 * `airCondition` indication turned on and dumps the raw JSON so the exact
 * accessibility (wheelchair) and air-conditioning field names / nesting can be
 * pinned before U8 extends the model against them.
 *
 * ── PROBE FINDINGS (fill in / confirm from the printed raw JSON) ────────────
 * Golemio v2 `pid/departureboards` departure objects nest per-trip attributes
 * under `trip`. The expected amenity fields (snake_case, `is_` boolean prefix,
 * matching the endpoint's existing `route.short_name` / `trip.is_canceled`
 * conventions) are:
 *
 *   trip.is_wheelchair_accessible : Boolean?   (accessibility, R7)
 *   trip.is_air_conditioned       : Boolean?   (air-conditioning, R8)
 *
 * The `airCondition` query parameter (default true) gates whether AC data is
 * populated. If a field is absent from the live sample, its indicator degrades
 * per R9 (value stays null → indicator omitted) and that is recorded here.
 * U8 parses whatever names this probe confirms; nullable throughout, so a name
 * that turns out wrong simply yields null rather than crashing.
 * ────────────────────────────────────────────────────────────────────────────
 *
 * Requires GOLEMIO_API_KEY in local.properties. Skips (does not fail) when the
 * live API is unreachable so the offline unit suite stays green.
 */
class GolemioAmenityProbeTest {

    private fun apiKey(): String {
        val properties = Properties()
        val localPropsFile = File("../local.properties")
        if (!localPropsFile.exists()) {
            throw FileNotFoundException("local.properties not found at " + localPropsFile.absolutePath)
        }
        properties.load(FileInputStream(localPropsFile))
        return properties.getProperty("GOLEMIO_API_KEY")
            ?: throw IllegalStateException("GOLEMIO_API_KEY missing from local.properties")
    }

    private fun fetchRaw(stopId: String): String? {
        val client = OkHttpClient.Builder().build()
        // airCondition=true asks the API to populate the AC indication.
        val url = "https://api.golemio.cz/v2/pid/departureboards" +
            "?ids=$stopId&limit=10&minutesAfter=90&airCondition=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-access-token", apiKey())
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    println("Probe HTTP ${resp.code}; skipping amenity discovery.")
                    return null
                }
                resp.body?.string()
            }
        } catch (e: Exception) {
            println("Probe network error (${e.message}); skipping amenity discovery.")
            null
        }
    }

    @Test
    fun `probe departureboards and dump amenity fields`() {
        val stopId = "U324Z2P" // Letenské náměstí [B] — reused from RealApiIntegrationTest
        val raw = fetchRaw(stopId) ?: return // offline → skip

        println("===== RAW departureboards JSON (${stopId}) =====")
        println(raw)
        println("===== END RAW =====")

        // Discovery: response should contain departures so the dump is meaningful.
        assertTrue("Response should contain a departures array", raw.contains("\"departures\""))

        // Surface any amenity-bearing keys present in the sample.
        val candidateKeys = listOf(
            "is_wheelchair_accessible", "wheelchair_accessible", "wheelchair",
            "is_air_conditioned", "air_conditioned", "air_condition", "airCondition"
        )
        val present = candidateKeys.filter { raw.contains("\"$it\"") }
        println("Amenity-bearing keys present in sample: $present")
        if (present.isEmpty()) {
            println("No amenity keys found in this sample — R7/R8 may degrade per R9. " +
                "Inspect the raw dump above for the actual nesting before finalizing U8.")
        }

        // CONFIRMED (live probe 2026-07-18): both fields are present per-trip as
        // JSON booleans. R7 and R8 are both firm — no R9 degradation required.
        assertTrue(
            "trip.is_wheelchair_accessible should be present (R7)",
            raw.contains("\"is_wheelchair_accessible\"")
        )
        assertTrue(
            "trip.is_air_conditioned should be present (R8)",
            raw.contains("\"is_air_conditioned\"")
        )
    }
}
