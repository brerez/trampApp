package com.example.tramapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified loading indicator that provides consistent UX across the app.
 *
 * LoadingType options:
 * - Skeleton: Shows content placeholders (best for initial load)
 * - Spinner: Traditional circular spinner (for quick operations)
 * - Shimmer: Animated shimmer effect (for refresh actions)
 */
enum class LoadingType {
    SKELETON,
    SPINNER,
    SHIMMER
}

/**
 * Unified loading indicator composable.
 */
@Composable
fun UnifiedLoading(
    type: LoadingType = LoadingType.SKELETON,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    isLoading: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    showContentWhileLoading: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (type) {
            LoadingType.SKELETON -> ContentPlaceholder(content = content)
            LoadingType.SPINNER -> CircularProgressIndicator()
            LoadingType.SHIMMER -> ShimmerEffect(content = content)
        }

        if (showContentWhileLoading && !isLoading) {
            content()
        }
    }
}

/**
 * Skeleton placeholder that shows the structure of content before it loads.
 */
@Composable
fun ContentPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    skeletonCount: Int = 3
) {
    Box(modifier = modifier) {
        // Show skeletons while loading
        repeat(skeletonCount) { index ->
            SkeletonRow(
                modifier = Modifier.offset(y = (index * 40).dp),
                width = when (index % 3) {
                    0 -> 60.dp
                    1 -> 80.dp
                    else -> 100.dp
                }
            )
        }

        // Show actual content when loaded
        content()
    }
}

/**
 * Skeleton row component for list items.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    width: Dp = 100.dp
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth()
            .then(modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        val shimmerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

        // Create staggered shimmer effect
        repeat(5) { i ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(modifier)
                    .offset(x = width * i),
                contentAlignment = Alignment.CenterStart
            ) {
                val alpha = if (i % 2 == 0) 0.15f else 0.25f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val shimmer = rememberShimmerEffect(
                        color = highlightColor,
                        baseColor = shimmerColor,
                        animationDuration = 1500L,
                        delay = (i % 2).toLong() * 300L
                    )

                    val density = LocalDensity.current
                    val px = with(density) { width.roundToPx() }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "•".repeat((px / 8) + 1),
                            color = shimmerColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shimmer effect for refresh actions.
 */
@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    animationDuration: Long = 1500L,
    delay: Long = 300L
) {
    val shimmer = rememberShimmerEffect(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        baseColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        animationDuration = animationDuration,
        delay = delay
    )

    Box(modifier = modifier) {
        content()
    }
}

/**
 * Shimmer effect state that can be animated.
 */
@Composable
fun rememberShimmerEffect(
    color: Color,
    baseColor: Color,
    animationDuration: Long,
    delay: Long
): ShimmerEffectState {
    return remember {
        ShimmerEffectState(color = color, baseColor = baseColor, duration = animationDuration, delay = delay)
    }
}

/**
 * Shimmer effect state.
 */
data class ShimmerEffectState(
    val color: Color,
    val baseColor: Color,
    val duration: Long,
    val delay: Long
)
