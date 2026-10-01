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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tramapp.domain.junction.GeoPoint
import com.example.tramapp.ui.theme.*
import com.example.tramapp.ui.components.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

/** One map marker for a junction platform (replaces the old per-station marker list). */
data class JunctionMapMarker(val id: String, val position: LatLng, val title: String)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isManualLocation by viewModel.isManualLocation.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()

    val showTripPopup by viewModel.showTripPopup.collectAsState()
    val isTripLoading by viewModel.isTripLoading.collectAsState()
    val selectedTripDetails by viewModel.selectedTripDetails.collectAsState()
    val throttleMessage by viewModel.throttleMessage.collectAsState()
    val apiQueryCount by viewModel.apiQueryCount.collectAsState()
    val appStatus by viewModel.appStatus.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoritesFirst by viewModel.favoritesFirst.collectAsState()
    val junctionCards by viewModel.junctionCards.collectAsState()
    val noStopInRange by viewModel.noStopInRange.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }

    LaunchedEffect(currentLocation) {
        // Snap position directly to avoid animation failures or race conditions on start
        cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }

    // R4/KTD10: nodeId -> expanded. The nearest junction (or a deep-linked pinned one) is
    // auto-expanded exactly once each; the user's own toggles are never overridden afterwards.
    val expandedJunctions = remember { mutableStateMapOf<String, Boolean>() }
    var hasAutoExpandedNearest by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(junctionCards) {
        val currentNodeIds = junctionCards.map { it.junction.nodeId }.toSet()
        val keysToRemove = expandedJunctions.keys.filter { it !in currentNodeIds }
        keysToRemove.forEach { expandedJunctions.remove(it) }

        val first = junctionCards.firstOrNull()
        if (first != null) {
            if (first.isPinned) {
                expandedJunctions[first.junction.nodeId] = true
            } else if (!hasAutoExpandedNearest) {
                expandedJunctions[first.junction.nodeId] = true
                hasAutoExpandedNearest = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
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
            HeaderSection(
                appStatus = appStatus,
                now = currentTime,
                queryCount = apiQueryCount,
                stationsCount = junctionCards.size,
                onSettingsClick = { showSettingsDialog = true }
            )

            if (throttleMessage != null) {
                ErrorBanner(
                    error = ErrorState(
                        type = ErrorType.NETWORK,
                        message = throttleMessage!!
                    )
                )
            }

            Text(
                "Nearby Junctions",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White,
                modifier = Modifier.padding(top = 20.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Map Component — kept as a fixed sibling above the scrollable list (see U1's
            // comment history); markers are now junction platforms, not raw stations (KTD10).
            val markers = remember(junctionCards) {
                junctionCards.flatMap { card ->
                    card.junction.platforms.map { platform ->
                        JunctionMapMarker(
                            id = platform.stopId,
                            position = LatLng(platform.position.lat, platform.position.lng),
                            title = "${card.junction.name} [${platform.letter}]"
                        )
                    }
                }
            }
            GoogleMapComponent(
                cameraPositionState = cameraPositionState,
                onLocationChange = { viewModel.updateLocation(it, isManual = true) },
                markers = markers,
                isManualLocation = isManualLocation,
                currentLocation = currentLocation,
                viewModel = viewModel,
                isExpanded = isMapExpanded,
                onToggleExpanded = { isMapExpanded = !isMapExpanded }
            )
            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (junctionCards.isEmpty()) {
                    if (noStopInRange) {
                        item {
                            Text(
                                "No tram stop within walking range.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(4) {
                            SkeletonRow(width = 300.dp, modifier = Modifier.testTag("skeleton"))
                        }
                    }
                } else {
                    itemsIndexed(junctionCards, key = { _, card -> card.junction.nodeId }) { _, card ->
                        val isExpanded = expandedJunctions[card.junction.nodeId] ?: false
                        JunctionCard(
                            junction = card.junction,
                            snapshot = card.snapshot,
                            userLocation = GeoPoint(currentLocation.latitude, currentLocation.longitude),
                            isExpanded = isExpanded,
                            favorites = favorites,
                            now = currentTime,
                            onExpandToggle = { expandedJunctions[card.junction.nodeId] = !isExpanded },
                            onFavoriteClick = { line -> viewModel.toggleFavorite(line) },
                            onTramClick = { tripId, routeName, destination ->
                                viewModel.selectTram(tripId, routeName, destination)
                            }
                        )
                    }
                }
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
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
fun HeaderSection(
    appStatus: com.example.tramapp.ui.components.AppStatus,
    now: java.time.OffsetDateTime,
    queryCount: Int,
    stationsCount: Int,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Find your tram", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            com.example.tramapp.ui.components.CompactStatusIndicator(
                status = appStatus,
                now = now.toInstant().toEpochMilli()
            )
            if (com.example.tramapp.BuildConfig.DEBUG) {
                Text(
                    "Debug: $queryCount API calls, $stationsCount junctions shown",
                    color = AccentCyan.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceGlass)
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable
fun GoogleMapComponent(
    cameraPositionState: CameraPositionState,
    onLocationChange: (LatLng) -> Unit,
    markers: List<JunctionMapMarker>,
    isManualLocation: Boolean,
    currentLocation: LatLng,
    viewModel: DashboardViewModel,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasLocationPermission = remember {
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val mapHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 220.dp else 64.dp,
        label = "mapHeight"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggleExpanded)
                    .testTag("map-toggle")
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Map", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse map" else "Expand map",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FloatingActionButton(
                    onClick = {
                        if (isManualLocation) {
                            viewModel.revertToGps()
                        } else {
                            viewModel.updateLocation(currentLocation, isManual = true)
                        }
                    },
                    containerColor = if (!isManualLocation) AccentViolet else Color.DarkGray,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Location Toggle", modifier = Modifier.size(16.dp))
                }

                FloatingActionButton(
                    onClick = { viewModel.refreshNow() },
                    containerColor = AccentViolet,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    onLocationChange(latLng)
                },
                uiSettings = com.example.tramapp.ui.components.MapStyleConfig.getDefaultMapUiSettings(),
                properties = MapProperties(
                    mapType = MapType.NORMAL,
                    isMyLocationEnabled = hasLocationPermission,
                    mapStyleOptions = MapStyleOptions(
                        com.example.tramapp.ui.components.MapStyleConfig.DARK_MAP_STYLE_JSON
                    )
                )
            ) {
                markers.forEach { marker ->
                    Marker(
                        state = MarkerState(position = marker.position),
                        title = marker.title
                    )
                }
            }
        }
    }
}
