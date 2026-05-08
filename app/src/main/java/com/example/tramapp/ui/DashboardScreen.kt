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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tramapp.ui.theme.*
import com.example.tramapp.ui.components.*
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
    val loadingStations by viewModel.loadingStations.collectAsState()
    val status by viewModel.status.collectAsState()
    
    val showTripPopup by viewModel.showTripPopup.collectAsState()
    val isTripLoading by viewModel.isTripLoading.collectAsState()
    val selectedTripDetails by viewModel.selectedTripDetails.collectAsState()
    val throttleMessage by viewModel.throttleMessage.collectAsState()
    val apiQueryCount by viewModel.apiQueryCount.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoritesFirst by viewModel.favoritesFirst.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    
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
            HeaderSection(apiQueryCount, onSettingsClick = { showSettingsDialog = true })
            
            // API Throttle Banner
            if (throttleMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = Color.Red.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Red,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = throttleMessage!!,
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
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

                // Follow GPS Toggle (Top Start)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    color = SurfaceGlass,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Follow GPS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = !isManualLocation,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.revertToGps()
                                } else {
                                    viewModel.updateLocation(currentLocation, isManual = true)
                                }
                            },
                            modifier = Modifier.scale(0.7f)
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

            val expandedStations = remember { mutableStateMapOf<Int, Boolean>().apply { put(0, true) } }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Group platforms by base station name (strip [A], [B] etc.)
                val grouped = nearbyStations.groupBy { station ->
                    station.name.replace(Regex("\\s*\\[.*]$"), "").trim()
                }
                
                itemsIndexed(grouped.entries.toList()) { index, (baseName, platforms) ->
                    val platformIds = platforms.map { it.id }
                    val isAnyLoading = platformIds.any { loadingStations.contains(it) }
                    val platformDeps = platforms.map { station ->
                        val platformLabel = Regex("\\[(.+)]$").find(station.name)?.groupValues?.get(1) ?: ""
                        platformLabel to (stationDepartures[station.id] ?: emptyList())
                    }
                    
                    val isExpanded = expandedStations[index] ?: false

                    StationGroupCard(
                        baseName = baseName,
                        platformDepartures = platformDeps,
                        isExpanded = isExpanded,
                        isLoading = isAnyLoading,
                        favorites = favorites,
                        onExpandToggle = {
                            expandedStations[index] = !isExpanded
                            if (!isExpanded && platformDeps.all { it.second.isEmpty() }) {
                                viewModel.refreshStationGroup(platformIds)
                            }
                        },
                        onFavoriteClick = { line ->
                            viewModel.toggleFavorite(line)
                        },
                        onTramClick = { tripId, routeName, destination ->
                            viewModel.selectTram(tripId, routeName, destination)
                        }
                    )
                }
            }
        }

        if (showSettingsDialog) {
            com.example.tramapp.ui.components.SettingsDialog(
                favorites = favorites,
                favoritesFirst = favoritesFirst,
                onToggleFavoritesFirst = { viewModel.updateFavoritesFirst(it) },
                onRemoveFavorite = { viewModel.toggleFavorite(it) },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showTripPopup) {
            TramRoutePopup(
                details = selectedTripDetails,
                isLoading = isTripLoading,
                onDismiss = { viewModel.dismissTripPopup() }
            )
        }
    }
}

@Composable
fun HeaderSection(queryCount: Int, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Ahoj, Prague", color = TextSecondary, fontSize = 14.sp)
            Text("Find your tram", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Debug: $queryCount API calls", color = AccentCyan.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceGlass)
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Star, contentDescription = "Settings", tint = Color.White)
        }
    }
}
