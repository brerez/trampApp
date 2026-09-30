package com.example.tramapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*
import java.time.Duration
import java.time.OffsetDateTime

@Composable
fun TramRow(
    smartDeparture: com.example.tramapp.domain.SmartDeparture,
    isFavorite: Boolean,
    now: OffsetDateTime,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    // U10 (R11, KTD10): junction rows carry cancellation/minutes directly from the
    // TramDeparture model instead of re-deriving them from arrival.predicted/scheduled.
    isCancelled: Boolean = false,
    minutesOverride: Int? = null
) {
    val tram = smartDeparture.item
    val isHomeBound = smartDeparture.isHomeBound
    val isWorkBound = smartDeparture.isWorkBound
    val isSchoolBound = smartDeparture.isSchoolBound
    val accentColor = when {
        isHomeBound -> HomeGlow
        isWorkBound -> WorkGlow
        isSchoolBound -> SchoolGlow
        else -> AccentCyan
    }
    val isHighlighted = isHomeBound || isWorkBound || isSchoolBound
    val subtitleText = when {
        isHomeBound -> "Towards home"
        isWorkBound -> "Towards work"
        isSchoolBound -> "Towards school"
        else -> null
    }

    val timeText = when {
        isCancelled -> "Cancelled"
        // Minutes already reflect the predicted arrival, so no separate delay suffix.
        minutesOverride != null -> if (minutesOverride <= 0) "now" else "$minutesOverride min"
        else -> {
            val arrivalTime = try {
                OffsetDateTime.parse(tram.arrival.predicted ?: tram.arrival.scheduled)
            } catch (e: Exception) {
                now
            }
            val diffMinutes = Duration.between(now, arrivalTime).toMinutes()
            if (diffMinutes <= 0) "now" else "${diffMinutes} min"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isHighlighted) accentColor else accentColor.copy(alpha = 0.2f))
                    .border(1.dp, if (isHighlighted) Color.Transparent else accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { onFavoriteClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tram.route.shortName,
                    color = if (isHighlighted) DeepBlack else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                // U10 (KTD10): the favourite star lives on the line badge only — it no longer
                // reorders rows, so a separate standalone star affordance is misleading.
                if (isFavorite) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Star,
                        contentDescription = "Favorite",
                        tint = Color.Yellow,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .testTag("favorite-star")
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    tram.trip.headsign,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitleText != null) {
                    Text(subtitleText, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // U9 (R9, R21): small, monochrome amenity glyphs — never a relevance color —
            // rendered only when the flag is known (non-null), left of the countdown.
            if (smartDeparture.isAccessible == true) {
                Icon(
                    imageVector = Icons.Filled.Accessible,
                    contentDescription = "Wheelchair accessible",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("amenity-glyph")
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (smartDeparture.isAirConditioned == true) {
                Icon(
                    imageVector = Icons.Filled.AcUnit,
                    contentDescription = "Air conditioned",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("amenity-glyph")
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            // R19: countdown is the primary cue — fontSize/weight never shrink for glyphs.
            Text(
                timeText,
                color = when {
                    isCancelled -> Color.Red.copy(alpha = 0.8f)
                    isHighlighted -> accentColor
                    else -> Color.White
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("tram-time")
            )
        }
    }
}
