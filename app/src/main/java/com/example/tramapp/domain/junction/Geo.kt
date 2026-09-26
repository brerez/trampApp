package com.example.tramapp.domain.junction

import kotlin.math.*

object Geo {
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371008.8
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val aLat = Math.toRadians(a.lat)
        val bLat = Math.toRadians(b.lat)

        val sinDLat2 = sin(dLat / 2)
        val sinDLng2 = sin(dLng / 2)
        
        val hav = sinDLat2 * sinDLat2 + cos(aLat) * cos(bLat) * sinDLng2 * sinDLng2
        val c = 2 * atan2(sqrt(hav), sqrt(1 - hav))
        return r * c
    }

    fun bearingDeg(from: GeoPoint, to: GeoPoint): Double {
        val fromLat = Math.toRadians(from.lat)
        val toLat = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)

        val y = sin(dLng) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLng)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360.0) % 360.0
    }

    /** Arrow for the direction to walk given the phone heading; null when heading is null (R16).
     *  relative = (bearing - heading) normalised to 0..360, quantised to 8 sectors of 45 deg centred on
     *  0,45,...: "↑","↗","→","↘","↓","↙","←","↖". */
    fun arrow(bearingDeg: Double, headingDeg: Double?): String? {
        if (headingDeg == null) return null
        var relative = (bearingDeg - headingDeg) % 360.0
        if (relative < 0) relative += 360.0
        val sector = floor((relative + 22.5) / 45.0).toInt() % 8
        val arrows = arrayOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
        return arrows[sector]
    }

    /** Human distance: below 1000 m -> "120 m" (rounded to nearest 10 m), else "1.2 km" (one decimal, '.' separator). */
    fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            val rounded = (Math.round(meters / 10.0) * 10).toInt()
            "$rounded m"
        } else {
            val km = meters / 1000.0
            String.format(java.util.Locale.US, "%.1f km", km)
        }
    }
}
