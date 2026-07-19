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
import com.example.tramapp.ui.theme.*
import com.example.tramapp.ui.components.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
// Assuming StationEntity is available in the project. If not, we might need to import it.
// import com.example.tramapp.data.StationEntity 

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isManualLocation by viewModel.isManualLocation.collectAsState()
    val stationDepartures by viewModel.stationDepartures.collectAsState()
    val loadingStations by viewModel.loadingStations.collectAsState()
    val status by viewModel.status.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()

    val showTripPopup by viewModel.showTripPopup.collectAsState()
    val isTripLoading by viewModel.isTripLoading.collectAsState()
    val selectedTripDetails by viewModel.selectedTripDetails.collectAsState()
    val throttleMessage by viewModel.throttleMessage.collectAsState()
    val apiQueryCount by viewModel.apiQueryCount.collectAsState()
    val appStatus by viewModel.appStatus.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoritesFirst by viewModel.favoritesFirst.collectAsState()
    val visibleStations by viewModel.visibleStations.collectAsState()
    val visibleStationRows by viewModel.visibleStationRows.collectAsState()
    val hasMoreStations by viewModel.hasMoreStations.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }

    LaunchedEffect(currentLocation) {
        // Snap position directly to avoid animation failures or race conditions on start
        cameraPositionState.position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(currentLocation, 15f)
    }

    val expandedStations = remember { mutableStateMapOf<String, Boolean>() }
    // R24: sticky — expands the first *settled* (Ready) station exactly once. Never re-fires
    // on a later refresh/visible-set trickle, so it can't collapse or move a user's choice.
    var hasAutoExpanded by remember { mutableStateOf(false) }
    // U11 (R25): map is collapsed to a slim peek by default so it doesn't push the first
    // Ready station below the fold; user-expandable, independent of station state.
    var isMapExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(visibleStationRows) {
        val currentBaseNames = visibleStationRows.map { it.baseName }.toSet()
        val keysToRemove = expandedStations.keys.filter { it !in currentBaseNames }
        keysToRemove.forEach { expandedStations.remove(it) }

        if (!hasAutoExpanded) {
            val firstSettled = visibleStationRows.firstOrNull { it.isReady }
            if (firstSettled != null) {
                expandedStations[firstSettled.baseName] = true
                hasAutoExpanded = true
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
            // Header Section — not loading-gated: title/status/settings render
            // immediately (appStatus already has a sensible default before any refresh
            // completes; previously this was stuck showing a permanent skeleton because
            // `isLoading` was a local flag that no code path ever set to false).
            HeaderSection(
                appStatus = appStatus,
                now = currentTime,
                queryCount = apiQueryCount,
                stationsCount = visibleStationRows.size,
                onSettingsClick = { showSettingsDialog = true }
            )

            // API Throttle Banner
            if (throttleMessage != null) {
                ErrorRecovery(
                    error = ErrorState(
                        type = ErrorType.NETWORK,
                        message = throttleMessage!!,
                        canRetry = true,
                        retryAction = { viewModel.refreshNow() }
                    )
                )
            }

            Text(
                "Nearby Stations",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White,
                modifier = Modifier.padding(top = 20.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Map Component — kept outside the LazyColumn on purpose: a GoogleMap embedded as
            // a lazy-list item fights the list for vertical drag gestures (the list wins,
            // panning the map instead scrolls the whole screen). As a fixed sibling above the
            // scrollable station list, the map gets touch input in its bounds exclusively.
            if (visibleStations.isEmpty()) {
                SkeletonRow(width = 300.dp)
            } else {
                val filteredStations = visibleStations.filter { stationDepartures.containsKey(it.id) }
                GoogleMapComponent(
                    cameraPositionState = cameraPositionState,
                    onLocationChange = { viewModel.updateLocation(it, isManual = true) },
                    nearbyStations = filteredStations,
                    isManualLocation = isManualLocation,
                    currentLocation = currentLocation,
                    viewModel = viewModel,
                    isExpanded = isMapExpanded,
                    onToggleExpanded = { isMapExpanded = !isMapExpanded }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Stations List — driven off the U4 ordered StationUiState list: Loading
                // renders a skeleton (never an empty-looking populated card), Empty is
                // collapsed out entirely (excluded from visibleStationRows), Ready renders
                // the populated card.
                if (visibleStationRows.isEmpty()) {
                    items(5) {
                        SkeletonRow(width = 300.dp)
                    }
                } else {
                    itemsIndexed(visibleStationRows, key = { _, row -> row.baseName }) { _, row ->
                        if (row.isLoading) {
                            StationSkeletonCard(
                                baseName = row.baseName,
                                modifier = Modifier.testTag("skeleton")
                            )
                        } else {
                            val isExpanded = expandedStations[row.baseName] ?: false
                            val isRefetching = row.platformIds.any { loadingStations.contains(it) }

                            StationGroupCard(
                                baseName = row.baseName,
                                platformDepartures = row.platformDepartures,
                                isExpanded = isExpanded,
                                isLoading = isRefetching,
                                favorites = favorites,
                                now = currentTime,
                                onExpandToggle = {
                                    expandedStations[row.baseName] = !isExpanded
                                    if (!isExpanded) {
                                        viewModel.refreshStationGroup(row.platformIds)
                                    }
                                },
                                onFavoriteClick = { line ->
                                    viewModel.toggleFavorite(line)
                                },
                                onTramClick = { tripId, routeName, destination ->
                                    viewModel.selectTram(tripId, routeName, destination)
                                },
                                modifier = Modifier.testTag("station-card")
                            )
                        }
                    }

                    if (hasMoreStations) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AccentViolet.copy(alpha = 0.15f))
                                    .border(1.dp, AccentViolet, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.loadMoreStations() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = AccentViolet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "Load 3 more stations",
                                        color = AccentViolet,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
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
            // U10 (R10, R11, R22): a real freshness/connection cue replaces the always-visible
            // "Debug: N API calls" string. The debug counter is kept, but gated behind
            // BuildConfig.DEBUG (owner opted keep-but-gate, not delete).
            com.example.tramapp.ui.components.CompactStatusIndicator(
                status = appStatus,
                now = now.toInstant().toEpochMilli()
            )
            if (com.example.tramapp.BuildConfig.DEBUG) {
                Text(
                    "Debug: $queryCount API calls, $stationsCount stations found",
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
            // U10 (R23): gear icon replaces the star, since this opens Settings, not favorites.
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable
fun GoogleMapComponent(
    cameraPositionState: CameraPositionState,
    onLocationChange: (LatLng) -> Unit,
    nearbyStations: List<com.example.tramapp.data.local.entity.StationEntity>, // Use real entity type
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

            // Always visible regardless of collapsed/expanded state — previously these lived
            // inside the expanded-only map overlay and vanished once the map defaulted to
            // collapsed (U11), which was a real regression: users lost quick access to
            // "use my location" / "refresh now".
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
                    // U11 (R26): shared dark style — no bright tan rectangle.
                    mapStyleOptions = MapStyleOptions(
                        com.example.tramapp.ui.components.MapStyleConfig.DARK_MAP_STYLE_JSON
                    )
                )
            ) {
                nearbyStations.forEach { station ->
                    Marker(
                        state = MarkerState(position = LatLng(station.latitude, station.longitude)),
                        title = station.name
                    )
                }
            }
        }
    }
}
