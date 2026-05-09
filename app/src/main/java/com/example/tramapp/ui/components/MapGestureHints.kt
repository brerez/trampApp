package com.example.tramapp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*

/**
 * Animated hint for new users showing map gestures.
 */
@Composable
fun MapGestureHints(
    showLocationToggle: Boolean = true,
    showRefreshHint: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Location toggle hint
        if (showLocationToggle) {
            MapGestureItem(
                icon = Icons.Default.LocationOn,
                title = "Find Me",
                description = "Tap to use your current GPS location"
            )
        }

        // Refresh hint
        if (showRefreshHint) {
            MapGestureItem(
                icon = Icons.Default.Refresh,
                title = "Refresh",
                description = "Pull down or tap the button to update schedules"
            )
        }
    }
}

/**
 * Individual gesture hint item.
 */
@Composable
fun MapGestureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = icon,
        transitionSpec = {
            (fadeIn(animationSpec = tween(200), initialAlpha = 0.3f) + scaleIn(animationSpec = tween(200), initialScale = 0.8f)).togetherWith(fadeOut())
        }
    ) { targetIcon ->
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = targetIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = description,
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Drag indicator showing user can drag the map.
 */
@Composable
fun MapDragIndicator(
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = isDragging,
        transitionSpec = {
            if (targetState) {
                (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut())
            } else {
                fadeIn().togetherWith(fadeOut())
            }
        }
    ) { dragging ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (dragging) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = SurfaceGlass.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )

                            Text(
                                text = "Drag to explore",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pull-to-refresh indicator.
 */
@Composable
fun PullToRefreshIndicator(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = isRefreshing,
        transitionSpec = {
            if (targetState) {
                (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut())
            } else {
                fadeIn().togetherWith(fadeOut())
            }
        }
    ) { refreshing ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (refreshing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AccentCyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )

                    Text(
                        text = "Updating...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
