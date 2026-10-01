package com.example.tramapp.domain.junction

import com.example.tramapp.data.local.dao.TripNextStopDao
import com.example.tramapp.data.local.entity.TripNextStopEntity
import com.example.tramapp.data.remote.GolemioService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * U5 — NextStopResolver live probe.
 *
 * Finds platforms near Hradcanska, Kamenicka, and Strossmayerovo namesti via gtfs/stops latlng
 * (location_type 0, name match), fetches departures via getDepartureBoards, runs the resolver
 * with a real Retrofit GolemioService adapter source + fake dao, and prints per-platform
 * "line -> next stop". Asserts at least one resolution.
 *
 * Skips via org.junit.Assume when GOLEMIO_API_KEY is absent from local.properties or on
 * network failure, so the offline unit suite stays green.
 */
class NextStopProbeTest {

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
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.golemio.cz/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(GolemioService::class.java)
    }

    /**
     * Adapter TripSequenceSource that talks directly to GolemioService.
     * No cache; retries on HTTP 429 like the app's withRetry, since the live API rate-limits bursts.
     */
    private class ServiceTripSequenceSource(private val service: GolemioService) : TripSequenceSource {
        private suspend fun <T> retry429(block: suspend () -> T): T {
            var attempt = 0
            while (true) {
                try {
                    return block()
                } catch (e: retrofit2.HttpException) {
                    if (e.code() != 429 || attempt >= 4) throw e
                    attempt++
                    kotlinx.coroutines.delay(2000L * attempt)
                }
            }
        }

        override suspend fun tripStops(tripId: String): List<TripStop> {
            val response = retry429 { service.getTripDetails(tripId, includeStopTimes = true, includeShapes = false) }
            return response.stopTimes.map { st ->
                TripStop(stopId = st.stopId, sequence = st.stopSequence, name = st.stop?.stopName)
            }
        }

        override suspend fun stopNames(stopIds: List<String>): Map<String, String> {
            if (stopIds.isEmpty()) return emptyMap()
            return try {
                val resp = retry429 { service.getStopsByIds(stopIds) }
                resp.features.associate { it.properties.stopId to it.properties.stopName }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    /**
     * In-memory TripNextStopDao for probe.
     */
    private class ProbeDao : TripNextStopDao {
        val store = mutableListOf<TripNextStopEntity>()

        override suspend fun get(
            platformStopId: String,
            tripIds: List<String>,
            minFetchedAt: Long
        ): List<TripNextStopEntity> {
            return store.filter {
                it.platformStopId == platformStopId && it.tripId in tripIds && it.fetchedAt >= minFetchedAt
            }
        }

        override suspend fun insertAll(rows: List<TripNextStopEntity>) {
            val keys = rows.map { it.tripId to it.platformStopId }.toSet()
            store.removeAll { (it.tripId to it.platformStopId) in keys }
            store.addAll(rows)
        }

        override suspend fun deleteOlderThan(threshold: Long) {
            store.removeAll { it.fetchedAt < threshold }
        }
    }

    @Test
    fun `probe next-stop resolver for Hradcanska, Kamenicka and Strossmayerovo namesti`() {
        val apiKey = apiKey()
        assumeTrue("GOLEMIO_API_KEY missing from local.properties — skipping live probe", apiKey != null)

        val service = buildService(apiKey!!)
        val source = ServiceTripSequenceSource(service)
        val dao = ProbeDao()
        val resolver = NextStopResolver(source, dao)

        data class SearchCenter(val name: String, val lat: Double, val lng: Double, val nameFilter: String)

        val centers = listOf(
            SearchCenter("Hradcanska", 50.0975, 14.4036, "Hradčanská"),
            SearchCenter("Kamenicka", 50.1003, 14.4292, "Kamenická"),
            SearchCenter("Strossmayerovo namesti", 50.1015, 14.4331, "Strossmayerovo náměstí")
        )

        // Step 1: Find platforms for each location
        val platformsByLocation = mutableMapOf<String, List<com.example.tramapp.data.remote.StopProperties>>()

        for (center in centers) {
            val stopsResponse = try {
                runBlocking { service.getStops("${center.lat},${center.lng}", limit = 200) }
            } catch (e: Exception) {
                println("Probe: could not fetch stops for ${center.name}: ${e.message}")
                assumeNoException(e)
                return
            }

            val platforms = stopsResponse.features
                .filter { it.properties.locationType == 0 }
                .filter { it.properties.stopName == center.nameFilter }
                .map { it.properties }

            println("${center.name}: found ${platforms.size} platforms: ${platforms.map { "${it.stopId} [${it.platformCode}]" }}")
            if (platforms.isNotEmpty()) {
                platformsByLocation[center.name] = platforms
            }
        }

        assumeTrue(
            "Could not find any platforms for any of the three locations — skipping",
            platformsByLocation.isNotEmpty()
        )

        // Step 2: For each location fetch departures for all its platforms
        val allPlatformIds = platformsByLocation.values.flatten().map { it.stopId }

        val departureResponse = try {
            runBlocking { service.getDepartureBoards(ids = allPlatformIds, limit = 200, minutesAfter = 60) }
        } catch (e: Exception) {
            println("Probe: could not fetch departures: ${e.message}")
            assumeNoException(e)
            return
        }

        // Group departures by platform
        val departuresByPlatform = allPlatformIds.associateWith { platformId ->
            departureResponse.departures.filter { it.stop.id == platformId && it.route.type == 0 }
        }

        // Step 3: Run the resolver per platform
        val notFoundPlatforms = mutableListOf<String>()
        var totalResolutions = 0

        for ((locationName, platforms) in platformsByLocation) {
            println("\n===== $locationName =====")
            for (platform in platforms) {
                val deps = departuresByPlatform[platform.stopId].orEmpty()
                println("  Platform ${platform.stopId} [${platform.platformCode}]: ${deps.size} tram departures")
                if (deps.isEmpty()) continue

                val result = try {
                    runBlocking { resolver.resolve(platform.stopId, deps) }
                } catch (e: Exception) {
                    println("    Resolve error: ${e.message}")
                    continue
                }

                if (result.isEmpty()) {
                    notFoundPlatforms.add(platform.stopId)
                    println("    No resolutions for this platform")
                } else {
                    totalResolutions += result.size
                    // Print unique (line -> next stop) pairs
                    val lineToNext = result.values
                        .groupBy { entry ->
                            deps.find { it.trip.tripId == entry.tripId }?.route?.shortName ?: "?"
                        }
                        .mapValues { (_, entries) -> entries.map { it.nextStopName }.distinct() }
                    for ((line, nextStops) in lineToNext.entries.sortedBy { it.key }) {
                        println("    line $line -> ${nextStops.joinToString(", ")}")
                    }
                }
            }
        }

        println("\n===== Summary =====")
        println("Total trip resolutions: $totalResolutions")
        if (notFoundPlatforms.isNotEmpty()) {
            println("Platforms with no resolutions (stop id not in trip sequences): $notFoundPlatforms")
        }
        println("===================")

        assertTrue(
            "Expected at least one resolution across all platforms, got $totalResolutions",
            totalResolutions >= 1
        )
    }
}
