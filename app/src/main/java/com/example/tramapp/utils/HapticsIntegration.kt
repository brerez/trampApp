package com.example.tramapp.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Extension function to add haptic feedback to clickable elements.
 */
fun Modifier.withHaptics(
    type: HapticType = HapticType.TAP,
    enabled: Boolean = true
): Modifier {
    return composed {
        if (!enabled) return@composed this

        val context = LocalContext.current

        // Apply appropriate haptic feedback based on interaction
        when (type) {
            HapticType.TAP -> { /* Handled in ClickableWithHaptics */ }
            HapticType.SUCCESS -> { /* Handled in ClickableWithHaptics */ }
            HapticType.ERROR -> { /* Handled in ClickableWithHaptics */ }
            HapticType.SWIPE -> { /* Handled in ClickableWithHaptics */ }
            HapticType.NOTIFICATION -> { /* Handled in ClickableWithHaptics */ }
        }

        this
    }
}

/**
 * Haptic types for different interaction scenarios.
 */
enum class HapticType {
    TAP,           // Regular button press
    SUCCESS,       // Selection/confirmation
    ERROR,         // Error/warning
    SWIPE,         // Swipe gestures
    NOTIFICATION   // New message/alert
}

/**
 * Composable wrapper for clickable elements with haptics.
 */
@Composable
fun ClickableWithHaptics(
    onClick: () -> Unit,
    enabled: Boolean = true,
    type: HapticType = HapticType.TAP,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current

    Box(
        modifier = modifier
            .then(if (enabled) Modifier.clickable(interactionSource = interactionSource, indication = null) {
                onClick()
                when (type) {
                    HapticType.TAP -> Haptics.tap(view)
                    HapticType.SUCCESS -> Haptics.success(view)
                    HapticType.ERROR -> Haptics.error(view)
                    HapticType.SWIPE -> Haptics.swipe(view)
                    HapticType.NOTIFICATION -> Haptics.notification(view)
                }
            } else Modifier)
    ) {
        content()
    }
}

/**
 * Helper to determine appropriate haptic type for a given action.
 */
fun getHapticTypeForAction(action: String): HapticType {
    return when (action.lowercase()) {
        "select", "favorite", "toggle" -> HapticType.SUCCESS
        "delete", "remove", "clear" -> HapticType.ERROR
        "search", "filter", "refresh" -> HapticType.TAP
        else -> HapticType.TAP
    }
}
