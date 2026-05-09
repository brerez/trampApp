package com.example.tramapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*

/**
 * Unified status indicator for consistent connection/network state display.
 */
enum class ConnectionStatus {
    ONLINE,
    OFFLINE,
    LOADING,
    ERROR
}

/**
 * Data class representing the current app status.
 */
data class AppStatus(
    val connection: ConnectionStatus = ConnectionStatus.ONLINE,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val message: String? = null
)

/**
 * Unified status indicator composable.
 */
@Composable
fun StatusIndicator(
    status: AppStatus?,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    showMessage: Boolean = false
) {
    val iconColor = when (status?.connection) {
        ConnectionStatus.ONLINE -> AccentCyan
        ConnectionStatus.OFFLINE -> Color.Red
        ConnectionStatus.LOADING -> AccentViolet.copy(alpha = 0.5f)
        ConnectionStatus.ERROR -> Color.Red
        else -> TextSecondary
    }

    val iconTint = when (status?.connection) {
        ConnectionStatus.ONLINE -> Color.White
        ConnectionStatus.OFFLINE -> Color.Red
        ConnectionStatus.LOADING -> AccentViolet
        ConnectionStatus.ERROR -> Color.Red
        else -> TextPrimary
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status icon
        if (showIcon) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when (status?.connection) {
                    ConnectionStatus.LOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentViolet,
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        val imageVector = when (status?.connection) {
                            ConnectionStatus.ONLINE -> Icons.Default.Wifi
                            ConnectionStatus.OFFLINE -> Icons.Default.SignalCellular4Bar
                            ConnectionStatus.ERROR -> Icons.Default.Error
                            else -> Icons.Default.Wifi
                        }
                        Icon(
                            imageVector = imageVector,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Status message (optional)
        if (showMessage && status?.message != null) {
            Text(
                text = status.message,
                color = iconColor.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

/**
 * Compact status indicator for header display.
 */
@Composable
fun CompactStatusIndicator(
    status: AppStatus?,
    modifier: Modifier = Modifier
) {
    val connectionColor = when (status?.connection) {
        ConnectionStatus.ONLINE -> AccentCyan.copy(alpha = 0.6f)
        ConnectionStatus.OFFLINE -> Color.Red.copy(alpha = 0.8f)
        ConnectionStatus.LOADING -> TextSecondary.copy(alpha = 0.5f)
        ConnectionStatus.ERROR -> Color.Red
        else -> TextSecondary.copy(alpha = 0.4f)
    }

    val statusText = when (status?.connection) {
        ConnectionStatus.ONLINE -> "Online"
        ConnectionStatus.OFFLINE -> "Offline"
        ConnectionStatus.LOADING -> "Loading..."
        ConnectionStatus.ERROR -> "Error"
        else -> "Checking..."
    }

    Text(
        text = statusText,
        color = connectionColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Light,
        modifier = modifier
    )
}
