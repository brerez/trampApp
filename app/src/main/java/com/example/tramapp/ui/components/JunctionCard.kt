package com.example.tramapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.domain.junction.GeoPoint
import com.example.tramapp.domain.junction.Junction
import com.example.tramapp.domain.junction.JunctionSnapshot
import com.example.tramapp.domain.junction.SnapshotState
import com.example.tramapp.ui.theme.GlassBorder
import com.example.tramapp.ui.theme.SurfaceGlass
import com.example.tramapp.ui.theme.TextSecondary
import java.time.OffsetDateTime

/**
 * U10 (R4, R9, R22, R23, KTD10): a junction card replaces the old per-station group card.
 * Junction structure (name, platforms) renders immediately from [junction]; [snapshot] carries
 * rows/fetch state from the shared [com.example.tramapp.domain.junction.JunctionEngine] — the
 * same model the notification uses. Rows appear as a skeleton until the first fetch resolves
 * ("no stale departure times", KTD10).
 */
@Composable
fun JunctionCard(
    junction: Junction,
    snapshot: JunctionSnapshot?,
    userLocation: GeoPoint?,
    isExpanded: Boolean,
    favorites: Set<String>,
    now: OffsetDateTime,
    onExpandToggle: () -> Unit,
    onFavoriteClick: (String) -> Unit,
    onTramClick: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceGlass)
            .clickable { onExpandToggle() }
            .border(width = 1.dp, color = GlassBorder, shape = RoundedCornerShape(28.dp))
            .padding(20.dp)
            .testTag("junction-card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(junction.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextSecondary
            )
        }

        if (isExpanded) {
            val rows = snapshot?.rows
            when {
                rows == null -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    SkeletonRow(width = 220.dp, modifier = Modifier.testTag("skeleton"))
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonRow(width = 160.dp)
                }
                rows.isEmpty() -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    val message = if (snapshot.state == SnapshotState.ERROR) {
                        "Couldn't refresh — showing last known state."
                    } else {
                        "No departures found."
                    }
                    Text(message, color = TextSecondary, fontSize = 12.sp)
                }
                else -> {
                    rows.forEachIndexed { index, row ->
                        Spacer(modifier = Modifier.height(14.dp))
                        JunctionRowView(
                            row = row,
                            userLocation = userLocation,
                            favorites = favorites,
                            now = now,
                            onFavoriteClick = onFavoriteClick,
                            onTramClick = onTramClick
                        )
                        if (index < rows.size - 1) {
                            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
        }
    }
}
