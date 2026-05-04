package com.example.tramapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.tramapp.ui.theme.DeepBlack
import com.example.tramapp.ui.theme.SurfaceGlass
import com.example.tramapp.ui.theme.AccentCyan
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.platform.LocalContext
import android.location.Geocoder
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    locationType: String,
    onLocationSelected: (Double, Double, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // Default to Prague center
    val prague = LatLng(50.0755, 14.4378)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(prague, 12f)
    }
    
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedAddress by remember { mutableStateOf("Custom Location") }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun performSearch() {
        if (searchQuery.isBlank()) return
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            // Appending Prague to help the geocoder stay in the city
            val addressList = geocoder.getFromLocationName("$searchQuery, Prague", 1)
            if (!addressList.isNullOrEmpty()) {
                val address = addressList[0]
                val latLng = LatLng(address.latitude, address.longitude)
                selectedLocation = latLng
                selectedAddress = address.getAddressLine(0) ?: address.featureName ?: "Selected Location"
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        } catch (e: Exception) {
            // Handle search error
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(DeepBlack)) {
                TopAppBar(
                    title = { Text("Set $locationType Location", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlack)
                )
                
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search address (e.g. Letna)", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            TextButton(onClick = { performSearch() }) {
                                Text("GO", color = AccentCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = AccentCyan
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        bottomBar = {
            if (selectedLocation != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceGlass)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            selectedLocation?.let { onLocationSelected(it.latitude, it.longitude, selectedAddress) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Save Location", color = Color.White)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    selectedLocation = latLng
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                selectedAddress = addr.getAddressLine(0) ?: addr.featureName ?: "Custom Location"
                            } else {
                                selectedAddress = "Custom Location"
                            }
                        } catch (e: Exception) {
                            selectedAddress = "Custom Location"
                        }
                    }
                },
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = true
                ),
                properties = MapProperties(
                    isMyLocationEnabled = true
                )
            ) {
                selectedLocation?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = "Selected Location"
                    )
                }
            }
        }
    }
}
