package com.example.tramapp.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.example.tramapp.domain.junction.Geo
import com.example.tramapp.glance.CompassSampler
import com.example.tramapp.ui.theme.TextSecondary

/**
 * U10 (R9, KTD10): live compass arrow for a junction row's first platform row. Samples the
 * rotation-vector sensor only while this composable is on screen (collection is cancelled when
 * it leaves composition), exactly as the notification's [CompassSampler] does — discrete 8-way
 * quantised arrow glyphs (via [Geo.arrow]); no heading falls back to distance-only (R16).
 */
@Composable
fun CompassArrow(
    bearingDeg: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Recomposing on every raw sensor sample (tens of Hz at SENSOR_DELAY_UI) never lets Compose's
    // idling resource settle, which hangs instrumented tests ("possibly due to compose being
    // busy"). Geo.arrow already quantises heading to 8 sectors, so only push a state update when
    // the quantised glyph actually changes — real device redraws stay just as responsive.
    var arrow by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val sampler = CompassSampler(context)
        sampler.headingFlow().collect { heading ->
            val next = Geo.arrow(bearingDeg, heading)
            if (next != arrow) arrow = next
        }
    }

    val currentArrow = arrow
    if (currentArrow != null) {
        Text(
            text = currentArrow,
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = modifier.testTag("compass-arrow")
        )
    }
}
