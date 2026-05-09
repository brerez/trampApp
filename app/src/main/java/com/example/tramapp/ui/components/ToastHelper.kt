package com.example.tramapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Toast message types for consistent user feedback.
 */
enum class ToastType {
    INFO,           // Neutral information
    SUCCESS,        // Action completed successfully
    WARNING,        // Warning or caution
    ERROR,          // Error occurred
    NOTIFICATION    // General notification
}

/**
 * Data class representing a toast message.
 */
data class ToastMessage(
    val type: ToastType = ToastType.INFO,
    val title: String? = null,
    val message: String,
    val duration: Int = 3500, // Fallback for LongDuration
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val dismissAction: (() -> Unit)? = null
)

/**
 * Toast types with their colors.
 */
object ToastColors {
    val INFO @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val SUCCESS @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val WARNING @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val ERROR @Composable get() = MaterialTheme.colorScheme.errorContainer
    val NOTIFICATION @Composable get() = MaterialTheme.colorScheme.surfaceVariant
}

/**
 * Toast type to color mapping.
 */
@Composable
fun ToastType.toColor(): Color {
    return when (this) {
        ToastType.INFO -> ToastColors.INFO
        ToastType.SUCCESS -> ToastColors.SUCCESS
        ToastType.WARNING -> ToastColors.WARNING
        ToastType.ERROR -> ToastColors.ERROR
        ToastType.NOTIFICATION -> ToastColors.NOTIFICATION
    }
}

/**
 * Toast type to icon mapping.
 */
fun ToastType.toIcon(): androidx.compose.ui.graphics.vector.ImageVector {
    return when (this) {
        ToastType.INFO -> Icons.Default.Info
        ToastType.SUCCESS -> Icons.Default.CheckCircle
        ToastType.WARNING -> Icons.Default.Warning
        ToastType.ERROR -> Icons.Default.Error
        ToastType.NOTIFICATION -> Icons.Default.Notifications
    }
}

/**
 * Toast type to text style mapping.
 */
@Composable
fun ToastType.toTextStyle(): TextStyle {
    return when (this) {
        ToastType.INFO, ToastType.SUCCESS, ToastType.WARNING ->
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        ToastType.ERROR ->
            MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        ToastType.NOTIFICATION ->
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal)
    }
}

/**
 * Compact toast for inline display (e.g., in headers).
 */
@Composable
fun CompactToast(
    message: String,
    type: ToastType = ToastType.INFO,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val icon = type.toIcon()
    val color = type.toColor()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

/**
 * Full toast/snackbar for prominent display.
 */
@Composable
fun FullToast(
    message: String,
    type: ToastType = ToastType.INFO,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onDismiss: () -> Unit = {},
    actionLabel: String? = null,
    action: (() -> Unit)? = null
) {
    val icon = type.toIcon()
    val color = type.toColor()

    Surface(
        modifier = modifier,
        color = color,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                if (actionLabel != null && action != null) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                action()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(actionLabel, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}
