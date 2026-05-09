package com.example.tramapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.tramapp.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow

/**
 * Accessibility audit checklist for the app.
 */
object AccessibilityChecklist {
    // Minimum touch target sizes (Material Design 3)
    val MIN_TOUCH_TARGET_SIZE = 48.dp

    // Recommended contrast ratios
    const val AAA_CONTRAST_RATIO = 7.0
    const val AA_CONTRAST_RATIO = 4.5

    // Text size recommendations
    val BASE_TEXT_SIZE = 16.sp
    val SMALL_TEXT_SIZE = 12.sp
    val LARGE_TEXT_SIZE = 20.sp

    fun checkContrastRatio(color: Color): Float {
        // Convert to relative luminance
        val r = color.red
        val g = color.green
        val b = color.blue

        val rl = if (r > 0.03928f) {
            ((r.toDouble() + 0.055) / 1.055).pow(2.4)
        } else {
            r.toDouble() / 12.92
        }

        val gl = if (g > 0.03928f) {
            ((g.toDouble() + 0.055) / 1.055).pow(2.4)
        } else {
            g.toDouble() / 12.92
        }

        val bl = if (b > 0.03928f) {
            ((b.toDouble() + 0.055) / 1.055).pow(2.4)
        } else {
            b.toDouble() / 12.92
        }

        val luminance = 0.2126 * rl + 0.7152 * gl + 0.0722 * bl

        // Calculate contrast ratio against white (1.0)
        return (1.05 / (luminance + 0.05)).toFloat()
    }

    fun checkContrastRatioAgainstBlack(color: Color): Float {
        val r = color.red
        val g = color.green
        val b = color.blue

        val rl = if (r > 0.03928f) {
            ((r.toDouble() + 0.055) / 1.055).pow(2.4)
        } else {
            r.toDouble() / 12.92
        }

        val gl = if (g > 0.03928f) {
            ((g.toDouble() + 0.055) / 1.055).pow(2.4)
        } else {
            g.toDouble() / 12.92
        }

        val bl = if (b > 0.03928f) {
            ((b.toDouble() + 0.055) / 1.055).pow(2.4)
        } else {
            b.toDouble() / 12.92
        }

        val luminance = 0.2126 * rl + 0.7152 * gl + 0.0722 * bl

        // Calculate contrast ratio against black (0.0)
        return ((luminance + 0.05) / 0.05).toFloat()
    }

    /**
     * Validate a color for use with white text.
     */
    fun validateColorForWhiteText(color: Color): Pair<Boolean, String> {
        val ratio = checkContrastRatio(color)
        return if (ratio >= AA_CONTRAST_RATIO) {
            true to "Passes WCAG AA (%.2f:1)".format(ratio)
        } else {
            false to "Fails WCAG AA (%.2f:1, needs %.2f more)".format(ratio, AA_CONTRAST_RATIO - ratio)
        }
    }

    /**
     * Validate a color for use with black text.
     */
    fun validateColorForBlackText(color: Color): Pair<Boolean, String> {
        val ratio = checkContrastRatioAgainstBlack(color)
        return if (ratio >= AA_CONTRAST_RATIO) {
            true to "Passes WCAG AA (%.2f:1)".format(ratio)
        } else {
            false to "Fails WCAG AA (%.2f:1, needs %.2f more)".format(ratio, AA_CONTRAST_RATIO - ratio)
        }
    }

    fun validateTextSize(size: Float): Pair<Boolean, String> {
        return if (size >= BASE_TEXT_SIZE.value) {
            true to "Passes minimum base size"
        } else {
            false to "Below minimum base size of ${BASE_TEXT_SIZE.value}sp"
        }
    }

    fun validateTouchTarget(size: Dp): Pair<Boolean, String> {
        return if (size >= MIN_TOUCH_TARGET_SIZE) {
            true to "Passes minimum touch target"
        } else {
            false to "Below minimum of ${MIN_TOUCH_TARGET_SIZE.value.toInt()}dp"
        }
    }

    /**
     * Full accessibility audit for a color scheme.
     */
    fun auditColorScheme(): Map<String, Pair<Boolean, String>> {
        val colors = mapOf(
            "AccentViolet" to AccentViolet,
            "AccentCyan" to AccentCyan,
            "SurfaceGlass" to SurfaceGlass,
            "DeepBlack" to DeepBlack,
            "TextPrimary" to TextPrimary,
            "TextSecondary" to TextSecondary,
            "HomeGlow" to HomeGlow,
            "WorkGlow" to WorkGlow,
            "SchoolGlow" to SchoolGlow
        )

        return colors.map { (name, color) ->
            name to validateColorForWhiteText(color)
        }.toMap()
    }

    /**
     * Comprehensive audit checking both white and black backgrounds.
     */
    fun comprehensiveAudit(): Map<String, Pair<Boolean, String>> {
        val colors = mapOf(
            "AccentViolet" to AccentViolet,
            "AccentCyan" to AccentCyan,
            "SurfaceGlass" to SurfaceGlass,
            "DeepBlack" to DeepBlack,
            "TextPrimary" to TextPrimary,
            "TextSecondary" to TextSecondary,
            "HomeGlow" to HomeGlow,
            "WorkGlow" to WorkGlow,
            "SchoolGlow" to SchoolGlow
        )

        val results = mutableMapOf<String, Pair<Boolean, String>>()

        for ((name, color) in colors) {
            // Check against white background (text on dark surfaces)
            val whiteResult = validateColorForWhiteText(color)

            // Check against black background (accent/branding elements)
            val blackResult = validateColorForBlackText(color)

            // Determine overall pass/fail based on worst case
            val passed = whiteResult.first && blackResult.first
            val message = when {
                passed -> "Passes both backgrounds"
                !whiteResult.first && !blackResult.first -> "Fails both: White (${whiteResult.second}), Black (${blackResult.second})"
                !whiteResult.first -> "White background only: ${whiteResult.second}"
                else -> "Black background only: ${blackResult.second}"
            }

            results[name] = passed to message
        }

        return results
    }

    /**
     * Get overall pass rate for quick status checking.
     */
    fun getOverallPassRate(): Pair<Int, Int> {
        val allColors = mapOf(
            "AccentViolet" to AccentViolet,
            "AccentCyan" to AccentCyan,
            "SurfaceGlass" to SurfaceGlass,
            "DeepBlack" to DeepBlack,
            "TextPrimary" to TextPrimary,
            "TextSecondary" to TextSecondary,
            "HomeGlow" to HomeGlow,
            "WorkGlow" to WorkGlow,
            "SchoolGlow" to SchoolGlow
        )

        var passed = 0
        var total = allColors.size

        for ((name, color) in allColors) {
            val whiteResult = validateColorForWhiteText(color).first
            val blackResult = validateColorForBlackText(color).first
            if (whiteResult && blackResult) passed++
        }

        return passed to total
    }

    /**
     * Get detailed contrast ratios for all colors.
     */
    fun getDetailedContrastRatios(): Map<String, Map<String, Float>> {
        val allColors = mapOf(
            "AccentViolet" to AccentViolet,
            "AccentCyan" to AccentCyan,
            "SurfaceGlass" to SurfaceGlass,
            "DeepBlack" to DeepBlack,
            "TextPrimary" to TextPrimary,
            "TextSecondary" to TextSecondary,
            "HomeGlow" to HomeGlow,
            "WorkGlow" to WorkGlow,
            "SchoolGlow" to SchoolGlow
        )

        val ratios = mutableMapOf<String, Map<String, Float>>()

        for ((name, color) in allColors) {
            val whiteRatio = checkContrastRatio(color)
            val blackRatio = checkContrastRatioAgainstBlack(color)
            ratios[name] = mapOf("White Background" to whiteRatio, "Black Background" to blackRatio)
        }

        return ratios
    }

    /**
     * Get quick summary string for status bar or logging.
     */
    fun getQuickSummary(): String {
        val (passed, total) = getOverallPassRate()
        val percentage = ((passed.toFloat() / total) * 100).toInt()

        return when {
            percentage >= 95 -> "Excellent: $percentage% of colors pass WCAG AA"
            percentage >= 80 -> "Good: $percentage% of colors pass WCAG AA"
            percentage >= 60 -> "Fair: $percentage% of colors pass WCAG AA"
            else -> "Needs Work: Only $percentage% of colors pass WCAG AA"
        }
    }

    /**
     * Audit report composable showing all color validation results.
     */
    @Composable
    fun ColorAuditReport() {
        val auditResults = comprehensiveAudit()

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Color Contrast Audit",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick summary
            val (passed, total) = getOverallPassRate()
            val percentage = ((passed.toFloat() / total) * 100).toInt()
            val statusText = when {
                percentage >= 95 -> "Excellent: $percentage% pass"
                percentage >= 80 -> "Good: $percentage% pass"
                percentage >= 60 -> "Fair: $percentage% pass"
                else -> "Needs Work: $percentage% pass"
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = statusText,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            auditResults.forEach { (name, pair) ->
                val (passed, message) = pair
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (passed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (passed) Color.Green else Color.Red,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "$name: $message",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Accessibility-aware text composable.
 */
@Composable
fun AccessibleText(
    text: String,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = if (fontSize.isSp) {
            fontSize.value.coerceAtLeast(AccessibilityChecklist.BASE_TEXT_SIZE.value).sp
        } else {
            fontSize
        },
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

/**
 * Accessibility-aware button.
 */
@Composable
fun AccessibleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(min = AccessibilityChecklist.MIN_TOUCH_TARGET_SIZE)
            .heightIn(min = AccessibilityChecklist.MIN_TOUCH_TARGET_SIZE)
    ) {
        content()
    }
}

/**
 * Accessibility audit report composable.
 */
@Composable
fun AccessibilityAuditReport(
    title: String = "Accessibility Audit",
    results: Map<String, Pair<Boolean, String>>? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        results?.forEach { (name, pair) ->
            val (passed, message) = pair
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (passed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (passed) Color.Green else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "$name: $message",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
