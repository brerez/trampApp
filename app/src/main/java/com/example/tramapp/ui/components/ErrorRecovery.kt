package com.example.tramapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Error types for consistent error handling.
 */
enum class ErrorType {
    NETWORK,           // No internet connection
    SERVER,            // Server-side issue
    TIMEOUT,           // Request took too long
    UNKNOWN            // Generic catch-all
}

/**
 * Data class representing an error state with recovery options.
 */
data class ErrorState(
    val type: ErrorType = ErrorType.UNKNOWN,
    val message: String,
    val canRetry: Boolean = true,
    val retryAction: (() -> Unit)? = null,
    val isRecovering: Boolean = false
)

/**
 * Unified error recovery composable.
 */
@Composable
fun ErrorRecovery(
    error: ErrorState?,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    showDetails: Boolean = true
) {
    if (error == null || !showDetails) return

    var isRecovering by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon with animation
        val errorIconColor = when (error.type) {
            ErrorType.NETWORK -> Color.Red
            ErrorType.SERVER -> Color(0xFFFFA500) // Orange
            ErrorType.TIMEOUT -> Color.Yellow
            else -> Color.Gray
        }

        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = errorIconColor,
                modifier = Modifier.scale(1.5f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error message with retry button
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Error icon
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Message and retry button
                Column {
                    Text(
                        text = error.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (error.canRetry && !isRecovering) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    error.retryAction?.invoke() ?: onRetry()
                                    isRecovering = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Retry", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Retry progress indicator
        if (isRecovering) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        }
    }
}

/**
 * Simple error banner for inline display.
 */
@Composable
fun ErrorBanner(
    error: ErrorState?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onDismiss: () -> Unit = {}
) {
    if (error == null) return

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}
