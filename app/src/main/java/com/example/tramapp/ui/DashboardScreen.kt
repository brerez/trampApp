package com.example.tramapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tramapp.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

data class MockTram(val line: String, val destination: String, val time: String, val isHomeBound: Boolean = false)
data class MockStation(val name: String, val direction: String, val isFavorite: Boolean, val trams: List<MockTram>, val location: LatLng)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isManualLocation by viewModel.isManualLocation.collectAsState()
    val nearbyStations by viewModel.nearbyStations.collectAsState()
    val stationDepartures by viewModel.stationDepartures.collectAsState()
    val status by viewModel.status.collectAsState()
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }

    // Always follow currentLocation from ViewModel (covers cache restore, GPS lock, revert)
    LaunchedEffect(currentLocation) {
        cameraPositionState.animate(
            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(currentLocation, 15f)
        )
    }


    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        // Decorative background glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-100).dp, y = (-50).dp)
                .blur(100.dp)
                .background(AccentViolet.copy(alpha = 0.15f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            HeaderSection()
            
            Text(
                text = status,
                color = if (status.contains("error", ignoreCase = true)) Color.Red else AccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Premium Map Component
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng ->
                        viewModel.updateLocation(latLng)
                    },
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = false,
                        compassEnabled = false
                    ),
                    properties = MapProperties(
                        mapType = MapType.NORMAL,
                        isMyLocationEnabled = true,
                        mapStyleOptions = MapStyleOptions(
                            "[" +
                            "  { \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#ebe3cd\" } ] }," +
                            "  { \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#523735\" } ] }," +
                            "  { \"elementType\": \"labels.text.stroke\", \"stylers\": [ { \"color\": \"#f5f1e6\" } ] }," +
                            "  { \"featureType\": \"administrative\", \"elementType\": \"geometry.stroke\", \"stylers\": [ { \"color\": \"#c9b2a6\" } ] }," +
                            "  { \"featureType\": \"road\", \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#f5f1e6\" } ] }," +
                            "  { \"featureType\": \"water\", \"elementType\": \"geometry.fill\", \"stylers\": [ { \"color\": \"#b9d3c2\" } ] }" +
                            "]"
                        )
                    )
                ) {
                    // Station Markers (Only show those with verified tram departures)
                    nearbyStations.filter { stationDepartures.containsKey(it.id) }.forEach { station ->
                        Marker(
                            state = MarkerState(position = LatLng(station.latitude, station.longitude)),
                            title = station.name
                        )
                    }
                }

                // Floating Action Buttons (in Map Box)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isManualLocation) {
                        FloatingActionButton(
                            onClick = { viewModel.revertToGps() },
                            containerColor = Color.DarkGray,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Back to GPS", modifier = Modifier.size(20.dp))
                        }
                    }

                    FloatingActionButton(
                        onClick = { viewModel.refreshNow() },
                        containerColor = AccentViolet,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // When the user stops dragging, update ViewModel with the new map center
            // isManual=true so the debounce-refresh runs and GPS doesn't snap back
            LaunchedEffect(cameraPositionState.isMoving) {
                if (!cameraPositionState.isMoving && cameraPositionState.position.target != currentLocation) {
                    viewModel.updateLocation(cameraPositionState.position.target, isManual = true)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                "Nearby Stations",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Group platforms by base station name (strip [A], [B] etc.)
                val stationsWithDeps = nearbyStations.filter { stationDepartures.containsKey(it.id) }
                val grouped = stationsWithDeps.groupBy { station ->
                    station.name.replace(Regex("\\s*\\[.*]$"), "").trim()
                }
                items(grouped.entries.toList()) { (baseName, platforms) ->
                    val platformDeps = platforms.map { station ->
                        val platformLabel = Regex("\\[(.+)]$").find(station.name)?.groupValues?.get(1) ?: ""
                        platformLabel to (stationDepartures[station.id] ?: emptyList())
                    }.filter { it.second.isNotEmpty() }

                    StationGroupCard(baseName, platformDeps)
                }
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Ahoj, Prague", color = TextSecondary, fontSize = 14.sp)
            Text("Find your tram", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(SurfaceGlass),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
fun StationGroupCard(
    baseName: String,
    platformDepartures: List<Pair<String, List<com.example.tramapp.domain.SmartDeparture>>>
) {
    val allDeps = platformDepartures.flatMap { it.second }
    val isAnyHomeBound = allDeps.any { it.isHomeBound }
    val isAnyWorkBound = allDeps.any { it.isWorkBound }
    val isAnySchoolBound = allDeps.any { it.isSchoolBound }
    val isHighlighted = isAnyHomeBound || isAnyWorkBound || isAnySchoolBound
    val highlightColor = when {
        isAnyHomeBound -> HomeGlow
        isAnyWorkBound -> WorkGlow
        isAnySchoolBound -> SchoolGlow
        else -> GlassBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceGlass)
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                brush = if (isHighlighted) {
                    Brush.linearGradient(listOf(highlightColor, highlightColor.copy(alpha = 0.5f)))
                } else {
                    SolidColor(GlassBorder)
                },
                shape = RoundedCornerShape(28.dp)
            )
            .padding(20.dp)
    ) {
        // Station name
        Text(baseName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        // Destination badges
        if (isHighlighted) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAnyHomeBound) DestinationBadge("🏠 HOME", HomeGlow)
                if (isAnyWorkBound) DestinationBadge("💼 WORK", WorkGlow)
                if (isAnySchoolBound) DestinationBadge("🎓 SCHOOL", SchoolGlow)
            }
        }

        // Platform sections
        platformDepartures.forEachIndexed { pIndex, (platformLabel, departures) ->
            Spacer(modifier = Modifier.height(14.dp))

            // Platform sub-header
            if (platformLabel.isNotEmpty()) {
                Text(
                    "[$platformLabel]",
                    color = AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Departure rows for this platform
            departures.forEachIndexed { index, smartDeparture ->
                TramRow(smartDeparture)
                if (index < departures.size - 1) {
                    HorizontalDivider(color = GlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                }
            }

            // Separator between platforms
            if (pIndex < platformDepartures.size - 1) {
                HorizontalDivider(
                    color = AccentCyan.copy(alpha = 0.3f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
fun DestinationBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TramRow(smartDeparture: com.example.tramapp.domain.SmartDeparture) {
    val tram = smartDeparture.item
    val isHomeBound = smartDeparture.isHomeBound
    val isWorkBound = smartDeparture.isWorkBound
    val isSchoolBound = smartDeparture.isSchoolBound
    val accentColor = when {
        isHomeBound -> HomeGlow
        isWorkBound -> WorkGlow
        isSchoolBound -> SchoolGlow
        else -> AccentCyan
    }
    val isHighlighted = isHomeBound || isWorkBound || isSchoolBound
    val subtitleText = when {
        isHomeBound -> "Towards home"
        isWorkBound -> "Towards work"
        isSchoolBound -> "Towards school"
        else -> null
    }

    val now = java.time.OffsetDateTime.now()
    val arrivalTime = try {
        java.time.OffsetDateTime.parse(tram.arrival.predicted ?: tram.arrival.scheduled)
    } catch (e: Exception) {
        now
    }
    val diffMinutes = java.time.Duration.between(now, arrivalTime).toMinutes()
    val timeText = if (diffMinutes <= 0) "now" else "${diffMinutes} min"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isHighlighted) accentColor else accentColor.copy(alpha = 0.2f))
                    .border(1.dp, if (isHighlighted) Color.Transparent else accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tram.route.shortName,
                    color = if (isHighlighted) DeepBlack else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(tram.trip.headsign, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitleText != null) {
                    Text(subtitleText, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Text(
            timeText,
            color = if (isHighlighted) accentColor else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}
