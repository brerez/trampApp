package com.example.tramapp.glance

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.example.tramapp.R
import kotlin.math.abs

/**
 * Deterministic, accessible palette for line-number badges in the junction glance notification
 * (R12). ~10 colours, chosen for reasonable contrast with white text and to stay legible on both
 * light and dark notification backgrounds. A given line number always maps to the same colour
 * (stable across refreshes), so the owner learns "8 is teal" over time.
 *
 * RemoteViews cannot tint a shared drawable per-instance on API 26-30, so each palette colour has
 * its own pre-baked rounded-rect drawable (`badge_line_0`..`badge_line_9`) that the renderer picks
 * by index via `setBackgroundResource`.
 */
object LineColors {

    // Named, moderately saturated, mid-to-dark tones (white text is legible on all of them).
    // Index i here must match res/drawable/badge_line_i.xml's fill colour.
    @ColorInt
    private val palette: IntArray = intArrayOf(
        0xFFC62828.toInt(), // red
        0xFF2E7D32.toInt(), // green
        0xFF1565C0.toInt(), // blue
        0xFF6A1B9A.toInt(), // purple
        0xFFEF6C00.toInt(), // orange
        0xFF00838F.toInt(), // teal
        0xFF5D4037.toInt(), // brown
        0xFFAD1457.toInt(), // pink/magenta
        0xFF283593.toInt(), // indigo
        0xFF558B2F.toInt(), // olive green
    )

    private val badgeDrawables: IntArray = intArrayOf(
        R.drawable.badge_line_0,
        R.drawable.badge_line_1,
        R.drawable.badge_line_2,
        R.drawable.badge_line_3,
        R.drawable.badge_line_4,
        R.drawable.badge_line_5,
        R.drawable.badge_line_6,
        R.drawable.badge_line_7,
        R.drawable.badge_line_8,
        R.drawable.badge_line_9,
    )

    private fun indexFor(line: String): Int = abs(line.hashCode()) % palette.size

    /** Stable colour for a line's badge background. Same line -> same colour every time. */
    @ColorInt
    fun colorFor(line: String): Int = palette[indexFor(line)]

    /** Drawable resource id matching [colorFor] for RemoteViews `setBackgroundResource`. */
    @DrawableRes
    fun backgroundResFor(line: String): Int = badgeDrawables[indexFor(line)]
}
