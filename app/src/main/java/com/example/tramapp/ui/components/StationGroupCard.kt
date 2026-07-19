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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*
import java.time.OffsetDateTime

@Composable
fun StationGroupCard(
    baseName: String,
    platformDepartures: List<Pair<String, List<com.example.tramapp.domain.SmartDeparture>>>,
    isExpanded: Boolean,
    isLoading: Boolean,
    favorites: Set<String>,
    now: OffsetDateTime,
    onExpandToggle: () -> Unit,
    onFavoriteClick: (String) -> Unit,
    onTramClick: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // U9/R20 (KTD5): the colored line badge is the single primary relevance cue and the
    // "Towards …" subtitle (rendered per-row in TramRow) is the one allowed secondary — no
    // card-level color. The gradient border + emoji destination-badge row are removed.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceGlass)
            .clickable { onExpandToggle() }
            .border(width = 1.dp, color = GlassBorder, shape = RoundedCornerShape(28.dp))
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
                        now = now,
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

/**
 * U5 (R1, R4): a still-resolving station's placeholder. Deliberately distinct from the
 * populated [StationGroupCard] shape (own name label + shimmer bars, no expand chevron, no
 * departure rows) so it can never be mistaken for a populated card with "no trams" — that
 * misreading is exactly what the state-model rework replaces.
 */
@Composable
fun StationSkeletonCard(
    baseName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceGlass)
            .border(width = 1.dp, color = GlassBorder, shape = RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(baseName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = AccentCyan,
                strokeWidth = 2.dp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SkeletonRow(width = 220.dp)
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonRow(width = 160.dp)
    }
}
