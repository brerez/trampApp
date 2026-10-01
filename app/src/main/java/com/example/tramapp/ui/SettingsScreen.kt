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
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.testTag
import com.example.tramapp.glance.SessionHealth
import com.example.tramapp.glance.SessionWarning

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

    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val packageName = context.packageName
    var isIgnoringBattery by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(packageName)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            contentWindowInsets = WindowInsets(0),
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

                Spacer(modifier = Modifier.height(24.dp))

                // Tram glance session
                SettingsGroup(title = "Tram glance session") {
                    val screenOnValues = listOf(10, 20, 30, 60)
                    val screenOnLabels = listOf("10 s", "20 s", "30 s", "60 s")
                    val currentScreenOn = prefs?.sessionScreenOnIntervalSec ?: 20
                    val screenOnIndex = screenOnValues.indexOf(currentScreenOn).takeIf { it >= 0 } ?: 1
                    ChoiceRow(
                        title = "Screen-on refresh",
                        options = screenOnLabels,
                        selectedIndex = screenOnIndex,
                        onSelect = { viewModel.updateSessionScreenOnInterval(screenOnValues[it]) },
                        testTag = "session-screen-on"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val screenOffValues = listOf(1, 3, 5, 10)
                    val screenOffLabels = listOf("1 min", "3 min", "5 min", "10 min")
                    val currentScreenOff = prefs?.sessionScreenOffIntervalMin ?: 3
                    val screenOffIndex = screenOffValues.indexOf(currentScreenOff).takeIf { it >= 0 } ?: 1
                    ChoiceRow(
                        title = "Screen-off refresh",
                        options = screenOffLabels,
                        selectedIndex = screenOffIndex,
                        onSelect = { viewModel.updateSessionScreenOffInterval(screenOffValues[it]) },
                        testTag = "session-screen-off"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val timeoutValues = listOf(15, 30, 60, 120, null)
                    val timeoutLabels = listOf("15 min", "30 min", "1 h", "2 h", "Never")
                    val currentTimeout = if (prefs == null) 60 else prefs!!.sessionTimeoutMin
                    val timeoutIndex = timeoutValues.indexOf(currentTimeout).takeIf { it >= 0 } ?: 2
                    ChoiceRow(
                        title = "Session timeout",
                        options = timeoutLabels,
                        selectedIndex = timeoutIndex,
                        onSelect = { viewModel.updateSessionTimeout(timeoutValues[it]) },
                        testTag = "session-timeout"
                    )

                    val warnings = SessionHealth.check(currentTimeout, isIgnoringBattery)
                    if (warnings.contains(SessionWarning.BATTERY_OPTIMIZED_LONG_SESSION)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("battery-warning-card"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF330000))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Long sessions need battery exemption", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("On Samsung also set Battery → Unrestricted for this app.", color = Color.LightGray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:$packageName")
                                        }
                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(intent)
                                        } else {
                                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                ) {
                                    Text("Fix", color = Color.White)
                                }
                            }
                        }
                    }
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

@Composable
fun ChoiceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, text ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AccentViolet else SurfaceGlass)
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text, color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
