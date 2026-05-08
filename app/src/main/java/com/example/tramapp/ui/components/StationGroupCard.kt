package com.example.tramapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*

@Composable
fun StationGroupCard(
    baseName: String,
    platformDepartures: List<Pair<String, List<com.example.tramapp.domain.SmartDeparture>>>,
    isExpanded: Boolean,
    isLoading: Boolean,
    favorites: Set<String>,
    onExpandToggle: () -> Unit,
    onFavoriteClick: (String) -> Unit,
    onTramClick: (String, String, String) -> Unit
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
            .clickable { onExpandToggle() }
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
        // Station name + Expand Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(baseName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AccentCyan,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }

        // Destination badges
        if (isHighlighted) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAnyHomeBound) DestinationBadge("🏠 HOME", HomeGlow)
                if (isAnyWorkBound) DestinationBadge("💼 WORK", WorkGlow)
                if (isAnySchoolBound) DestinationBadge("🎓 SCHOOL", SchoolGlow)
            }
        }

        if (isExpanded) {
            if (platformDepartures.all { it.second.isEmpty() } && !isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("No departures found or tap to refresh.", color = TextSecondary, fontSize = 12.sp)
            }

            // Platform sections
            platformDepartures.forEachIndexed { pIndex, (platformLabel, departures) ->
                if (departures.isEmpty()) return@forEachIndexed
                
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
                    val lineName = smartDeparture.item.route.shortName
                    TramRow(
                        smartDeparture = smartDeparture,
                        isFavorite = favorites.contains(lineName),
                        onFavoriteClick = { onFavoriteClick(lineName) },
                        onClick = {
                            val tripId = smartDeparture.item.trip.tripId ?: ""
                            onTramClick(
                                tripId, 
                                lineName, 
                                smartDeparture.item.trip.headsign
                            )
                        }
                    )
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
