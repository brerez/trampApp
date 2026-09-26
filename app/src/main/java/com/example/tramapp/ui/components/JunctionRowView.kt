package com.example.tramapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.data.remote.DepartureItem
import com.example.tramapp.data.remote.RouteInfo
import com.example.tramapp.data.remote.StopInfo
import com.example.tramapp.data.remote.TimestampInfo
import com.example.tramapp.data.remote.TripInfo
import com.example.tramapp.domain.SmartDeparture
import com.example.tramapp.domain.junction.Geo
import com.example.tramapp.domain.junction.GeoPoint
import com.example.tramapp.domain.junction.JunctionRow
import com.example.tramapp.domain.junction.TramDeparture
import com.example.tramapp.ui.theme.AccentCyan
import com.example.tramapp.ui.theme.HomeGlow
import com.example.tramapp.ui.theme.TextSecondary
import java.time.OffsetDateTime

private const val TRAMS_PER_ROW = 6

/** Adapts a junction-model [TramDeparture] into the existing [SmartDeparture]/[TramRow]
 *  rendering pipeline (KTD10: "using the existing TramRow"). The synthetic [DepartureItem]
 *  carries only the fields TramRow reads; arrival timestamps are not used for the countdown
 *  text (TramRow uses [minutesOverride] instead) so an arbitrary ISO instant is fine. */
private fun TramDeparture.toSmartDeparture(highlighted: Boolean): SmartDeparture {
    val epochStr = java.time.Instant.ofEpochMilli(departureEpochMs).toString()
    return SmartDeparture(
        item = DepartureItem(
            route = RouteInfo(shortName = line, type = 0),
            trip = TripInfo(
                headsign = headsign,
                tripId = tripId,
                isWheelchairAccessible = isAccessible,
                isAirConditioned = isAirConditioned,
                isCanceled = isCancelled,
                isAtStop = isAtStop
            ),
            arrival = TimestampInfo(scheduled = epochStr, predicted = epochStr),
            stop = StopInfo(id = tripId ?: "")
        ),
        isHomeBound = highlighted
    )
}

/**
 * U10 (R9, R11, R13, R14, KTD8, KTD10): one junction row — platform letter, live compass arrow +
 * distance (first row of the platform only), "-> next stop" label (highlighted rows get a
 * leading marker + bold label per R14), and up to 6 trams via the existing [TramRow].
 */
@Composable
fun JunctionRowView(
    row: JunctionRow,
    userLocation: GeoPoint?,
    favorites: Set<String>,
    now: OffsetDateTime,
    onFavoriteClick: (String) -> Unit,
    onTramClick: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighlighted = row.highlights.isNotEmpty()

    Column(modifier = modifier.testTag("junction-row")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.platformLetter,
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.testTag("junction-row-platform")
            )
            Spacer(modifier = Modifier.width(6.dp))

            if (row.isPlatformFirstRow && userLocation != null) {
                val bearing = Geo.bearingDeg(userLocation, row.platformPosition)
                CompassArrow(bearing, modifier = Modifier.testTag("junction-row-arrow"))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    Geo.formatDistance(Geo.distanceM(userLocation, row.platformPosition)),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("junction-row-distance")
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (isHighlighted) {
                Text("● ", color = HomeGlow, fontSize = 12.sp)
            }
            Text(
                "→ ${row.label}",
                color = if (isHighlighted) HomeGlow else Color.White,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.testTag("junction-row-label")
            )
        }

        Spacer(modifier = Modifier.padding(top = 6.dp))

        row.trams.take(TRAMS_PER_ROW).forEach { tram ->
            TramRow(
                smartDeparture = tram.toSmartDeparture(isHighlighted),
                isFavorite = favorites.contains(tram.line),
                now = now,
                onFavoriteClick = { onFavoriteClick(tram.line) },
                onClick = {
                    val tripId = tram.tripId
                    if (tripId != null) onTramClick(tripId, tram.line, row.label)
                },
                isCancelled = tram.isCancelled,
                delayMinutes = tram.delayMinutes,
                minutesOverride = tram.minutesUntil(now.toInstant().toEpochMilli())
            )
        }
    }
}
