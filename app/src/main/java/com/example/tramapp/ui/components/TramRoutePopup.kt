package com.example.tramapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

@Composable
fun TramRoutePopup(
    details: com.example.tramapp.domain.TripDetails?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(32.dp))
                .background(DeepBlack)
                .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
        ) {
            // Background blur/glow
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = 150.dp, y = (-50).dp)
                    .blur(80.dp)
                    .background(AccentCyan.copy(alpha = 0.1f), CircleShape)
            )

            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = details?.routeName ?: "Loading...",
                            color = AccentCyan,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = details?.destination ?: "Fetching route",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceGlass)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentCyan)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Fetching route coordinates...", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                } else if (details != null) {
                    // Minimap
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    ) {
                        val cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(
                                details.polyline.firstOrNull() ?: LatLng(50.0755, 14.4378), 
                                13f
                            )
                        }
                        
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                            properties = MapProperties(
                                mapStyleOptions = MapStyleOptions("[ { \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#242f3e\" } ] }, { \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#746855\" } ] }, { \"elementType\": \"labels.text.stroke\", \"stylers\": [ { \"color\": \"#242f3e\" } ] }, { \"featureType\": \"administrative.locality\", \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#d59563\" } ] }, { \"featureType\": \"poi\", \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#d59563\" } ] }, { \"featureType\": \"poi.park\", \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#263c3f\" } ] }, { \"featureType\": \"poi.park\", \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#6b9a76\" } ] }, { \"featureType\": \"road\", \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#38414e\" } ] }, { \"featureType\": \"road\", \"elementType\": \"geometry.stroke\", \"stylers\": [ { \"color\": \"#212a37\" } ] }, { \"featureType\": \"road\", \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#9ca5b3\" } ] }, { \"featureType\": \"road.highway\", \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#746855\" } ] }, { \"featureType\": \"road.highway\", \"elementType\": \"geometry.stroke\", \"stylers\": [ { \"color\": \"#1f2835\" } ] }, { \"featureType\": \"road.highway\", \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#f3d19c\" } ] }, { \"featureType\": \"transit\", \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#2f3948\" } ] }, { \"featureType\": \"transit.station\", \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#d59563\" } ] }, { \"featureType\": \"water\", \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#17263c\" } ] }, { \"featureType\": \"water\", \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#515c6d\" } ] }, { \"featureType\": \"water\", \"elementType\": \"labels.text.stroke\", \"stylers\": [ { \"color\": \"#17263c\" } ] } ]")
                            )
                        ) {
                            Polyline(
                                points = details.polyline,
                                color = AccentCyan,
                                width = 10f
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Station List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(details.stations) { station ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(AccentCyan, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    station.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
