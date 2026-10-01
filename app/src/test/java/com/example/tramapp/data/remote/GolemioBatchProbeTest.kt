package com.example.tramapp.data.remote

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

/**
 * U3 — junction batch fetch live probe.
 *
 * Resolves the platform stop ids for Kamenická (A/B) and Strossmayerovo náměstí
 * (A-D) via `gtfs/stops` latlng search, then makes ONE `pid/departureboards`
 * call with all resolved ids (ids[] batching) and prints per-platform departure
 * counts and platform codes.
 *
 * Requires GOLEMIO_API_KEY in local.properties (repo root, one level up from the
 * `app` module). Skips (via org.junit.Assume) rather than failing when the key
 * is missing or the live API is unreachable, so the offline unit suite stays
 * green.
 */
class GolemioBatchProbeTest {

    // Kamenická, Prague 7 — used as the search center per U3 spec.
    private val centerLat = 50.1003
    private val centerLng = 14.4292

    private fun apiKey(): String? {
        val localPropsFile = File("../local.properties")
        if (!localPropsFile.exists()) return null
        val properties = Properties()
        properties.load(FileInputStream(localPropsFile))
        return properties.getProperty("GOLEMIO_API_KEY")
    }

    private fun buildService(apiKey: String): GolemioService {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-access-token", apiKey)
                    .build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.golemio.cz/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(GolemioService::class.java)
    }

    @Test
    fun `probe junction batch departures for Kamenicka and Strossmayerovo namesti`() {
        val apiKey = apiKey()
        assumeTrue("GOLEMIO_API_KEY missing from local.properties — skipping live probe", apiKey != null)

        val service = buildService(apiKey!!)

        val stopsResponse = try {
            kotlinx.coroutines.runBlocking { service.getStops("$centerLat,$centerLng", limit = 200) }
        } catch (e: Exception) {
            println("Probe network error resolving stops (${e.message}); skipping.")
            assumeNoException(e)
            return
        }

        val platformStops = stopsResponse.features
            .filter { it.properties.locationType == 0 }
            .filter { it.properties.stopName == "Kamenická" || it.properties.stopName == "Strossmayerovo náměstí" }

        println("Resolved platform stops: " + platformStops.map { "${it.properties.stopId} (${it.properties.stopName} [${it.properties.platformCode}])" })

        assumeTrue(
            "Could not resolve any Kamenická / Strossmayerovo náměstí platforms near ($centerLat,$centerLng) — skipping",
            platformStops.isNotEmpty()
        )

        val ids = platformStops.map { it.properties.stopId }

        val response = try {
            kotlinx.coroutines.runBlocking {
                service.getDepartureBoards(ids = ids, limit = 200, minutesAfter = 60)
            }
        } catch (e: Exception) {
            println("Probe network error fetching departureboards (${e.message}); skipping.")
            assumeNoException(e)
            return
        }

        val byPlatform = ids.associateWith { id -> response.departures.filter { it.stop.id == id } }

        println("===== probe: junction batch departureboards =====")
        for (stop in platformStops) {
            val id = stop.properties.stopId
            val deps = byPlatform[id].orEmpty()
            println("platform $id (${stop.properties.stopName} [${stop.properties.platformCode}]): ${deps.size} departures")
        }
        println("===== end probe =====")

        val platformsWithDepartures = byPlatform.count { (_, deps) -> deps.isNotEmpty() }
        println("platform count with departures: $platformsWithDepartures")

        assertTrue(
            "Expected at least 4 platforms with departures, got $platformsWithDepartures",
            platformsWithDepartures >= 4
        )

        val allDepartures = byPlatform.values.flatten()
        assertTrue("Expected at least one departure overall", allDepartures.isNotEmpty())
        allDepartures.forEach { dep ->
            assertTrue(
                "Departure at stop ${dep.stop.id} should have a platform_code",
                dep.stop.platformCode != null
            )
        }
    }
}
