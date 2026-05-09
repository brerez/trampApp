package com.example.tramapp.domain

import com.example.tramapp.data.local.datastore.UserPreferences
import com.example.tramapp.data.remote.*
import com.example.tramapp.data.repository.TramRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.FileInputStream
import java.util.Properties

class RealApiIntegrationTest {

    private lateinit var service: GolemioService
    private val repository = mockk<TramRepository>()
    private val useCase = GetSmartDeparturesUseCase(repository)
    private val preferences = mockk<UserPreferences>()

    @Before
    fun setup() {
        val properties = Properties()
        properties.load(FileInputStream("C:/Users/erezb/git/tramApp/local.properties"))
        val apiKey = properties.getProperty("GOLEMIO_API_KEY")

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

        service = retrofit.create(GolemioService::class.java)
    }

    @Test
    fun reproduceLetenskeNamestiIssue() = runBlocking {
        // Mock Location
        mockkStatic(android.location.Location::class)
        every { android.location.Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val results = arg<FloatArray>(4)
            results[0] = 1000f // Distance > 500m
        }

        // Mock preferences for School (Hradčanská)
        coEvery { preferences.schoolLat } returns 50.0989
        coEvery { preferences.schoolLng } returns 14.4079
        coEvery { preferences.schoolStopNames } returns setOf("Hradčanská")
        coEvery { preferences.schoolStopIds } returns setOf("U999Z1P") // Different ID for Hradčanská
        
        coEvery { preferences.homeLat } returns null
        coEvery { preferences.homeLng } returns null
        coEvery { preferences.workLat } returns null
        coEvery { preferences.workLng } returns null
        coEvery { preferences.homeStopNames } returns emptySet()
        coEvery { preferences.workStopNames } returns emptySet()
        coEvery { preferences.homeStopIds } returns emptySet()
        coEvery { preferences.workStopIds } returns emptySet()

        // Fetch REAL departures for Letenské náměstí (Platform A or B)
        val stopId = "U324Z2P" // Letenské náměstí [B]
        val response = service.getDepartures(stopId)
        
        println("Departures count: ${response.departures.size}")
        response.departures.forEach { println("Line: ${it.route.shortName}, Headsign: ${it.trip.headsign}") }
        
        assertTrue("Should have departures", response.departures.isNotEmpty())

        // Find a departure that we expect to go to Hradčanská (e.g. Line 1, 8, 25, 26)
        val departure = response.departures.firstOrNull { dep ->
            dep.route.shortName in listOf("1", "8", "25", "26")
        }

        println("Found departure: ${departure?.route?.shortName} to ${departure?.trip?.headsign}")

        assertTrue("Should find a relevant tram departure", departure != null)

        // Mock repository behavior
        coEvery { repository.getCachedDirection(any(), any(), any(), any()) } returns null
        coEvery { repository.saveDirection(any(), any(), any(), any(), any()) } just Runs
        
        // Fetch REAL trip sequence
        val tripId = departure!!.trip.tripId ?: ""
        println("Trip ID: $tripId")
        val tripDetails = service.getTripDetails(tripId)
        val stopIds = tripDetails.stopTimes.map { it.stopId }
        
        // Fetch REAL names for the IDs to simulate the fix in repository
        val namesResponse = service.getStopsByIds(stopIds)
        val nameMap = namesResponse.features.associate { it.properties.stopId to it.properties.stopName }
        val pairs = stopIds.map { it to (nameMap[it] ?: "") }
        
        println("Real route IDs: $stopIds")
        println("Real route names: ${pairs.map { it.second }}")

        coEvery { repository.getTripSequence(any(), any(), any()) } returns pairs

        // Mock allStations to simulate the name fallback
        val stationList = listOf(
            com.example.tramapp.data.local.entity.StationEntity(stopId, "Letenské náměstí [A]", 50.1, 14.4, null, false, 0, true),
            com.example.tramapp.data.local.entity.StationEntity("U999Z1P", "Hradčanská [A]", 50.0989, 14.4079, null, false, 0, true)
        )
        val allStationsFlow = MutableStateFlow(stationList)
        every { repository.allStations } returns allStationsFlow

        // Act
        val bounds = useCase.checkBounds(departure, "Letenské náměstí", preferences, 50.0996, 14.4289)

        // Assert
        // This test should FAIL if the issue is present (IDs mismatch or name fallback fails)
        // We assume line 1, 8, 25, 26 from Letenske Namesti [A] go towards Hradcanska!
        // Wait, Letenske Namesti [A] is towards Strossmayerovo namesti or Hradcanska?
        // Let's check!
        // If it goes towards Hradcanska, then isSchoolBound should be true!
        
        assertTrue("Should be school bound", bounds.third)
    }
}
