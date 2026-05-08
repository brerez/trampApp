package com.example.tramapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.Duration
import java.time.OffsetDateTime

@Composable
fun TramRow(smartDeparture: com.example.tramapp.domain.SmartDeparture, onClick: () -> Unit) {
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

    val now = OffsetDateTime.now()
    val arrivalTime = try {
        OffsetDateTime.parse(tram.arrival.predicted ?: tram.arrival.scheduled)
    } catch (e: Exception) {
        now
    }
    val diffMinutes = Duration.between(now, arrivalTime).toMinutes()
    val timeText = if (diffMinutes <= 0) "now" else "${diffMinutes} min"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isHighlighted) accentColor else accentColor.copy(alpha = 0.2f))
                    .border(1.dp, if (isHighlighted) Color.Transparent else accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tram.route.shortName,
                    color = if (isHighlighted) DeepBlack else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(tram.trip.headsign, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitleText != null) {
                    Text(subtitleText, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Text(
            timeText,
            color = if (isHighlighted) accentColor else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}
