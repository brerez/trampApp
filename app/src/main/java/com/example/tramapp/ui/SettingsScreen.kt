package com.example.tramapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*
import androidx.hilt.navigation.compose.hiltViewModel

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToMap: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var radius by remember { mutableFloatStateOf(750f) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val prefs by viewModel.userPreferences.collectAsState()

    fun formatCoords(lat: Double?, lng: Double?) =
        if (lat != null && lng != null) "📍 %.4f, %.4f".format(lat, lng) else "Tap to set"

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 50.dp)
                .blur(100.dp)
                .background(AccentCyan.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
        )

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Configuration",
                    modifier = Modifier.padding(top = 10.dp),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Smart Locations Section
                SettingsGroup(title = "Smart Destinations") {
                    LocationSettingItem(
                        title = "Home Address",
                        subtitle = prefs?.homeAddress ?: formatCoords(prefs?.homeLat, prefs?.homeLng),
                        icon = Icons.Default.Home,
                        color = HomeGlow,
                        onClick = { onNavigateToMap("Home") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LocationSettingItem(
                        title = "Work Address",
                        subtitle = prefs?.workAddress ?: formatCoords(prefs?.workLat, prefs?.workLng),
                        icon = Icons.Default.Build,
                        color = WorkGlow,
                        onClick = { onNavigateToMap("Work") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LocationSettingItem(
                        title = "School Address",
                        subtitle = prefs?.schoolAddress ?: formatCoords(prefs?.schoolLat, prefs?.schoolLng),
                        icon = Icons.Default.Info,
                        color = SchoolGlow,
                        onClick = { onNavigateToMap("School") }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Search Radius & Max Stations
                SettingsGroup(title = "Station Discovery") {
                    Text(
                        "Search Radius: ${radius.toInt()}m",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 500f..2000f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentViolet,
                            activeTrackColor = AccentViolet,
                            inactiveTrackColor = SurfaceGlass
                        )
                    )
                    Text(
                        "Limits how far the app looks for nearby stations from your current location.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val maxStations = prefs?.maxStations?.toFloat() ?: 4f
                    var stationsSlider by remember(maxStations) { mutableFloatStateOf(maxStations) }
                    Text(
                        "Max Stations: ${stationsSlider.toInt()}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = stationsSlider,
                        onValueChange = { stationsSlider = it },
                        onValueChangeFinished = { viewModel.updateMaxStations(stationsSlider.toInt()) },
                        valueRange = 2f..8f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentViolet,
                            activeTrackColor = AccentViolet,
                            inactiveTrackColor = SurfaceGlass
                        )
                    )
                    Text(
                        "Number of nearby stations to display and fetch departures for. Lower = faster, less API usage.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}



@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(), 
            color = AccentCyan, 
            fontSize = 16.sp, 
            fontWeight = FontWeight.Bold, 
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGlass.copy(alpha = 0.3f))
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}


@Composable
fun LocationSettingItem(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 14.sp, lineHeight = 18.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
    }
}
