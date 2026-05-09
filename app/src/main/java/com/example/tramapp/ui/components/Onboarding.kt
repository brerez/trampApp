package com.example.tramapp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*

/**
 * Onboarding/tutorial flow for new users.
 */
@Composable
fun OnboardingFlow(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 3

    Column(modifier = modifier.fillMaxSize()) {
        // Progress indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { index ->
                val isActive = index == currentPage
                Surface(
                    color = if (isActive) AccentViolet else Color.Gray.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier.size(8.dp)
                ) {}
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Page content with animation
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
            }
        ) { page ->
            OnboardingPage(page, totalPages) {
                if (page < totalPages - 1) {
                    currentPage++
                } else {
                    onComplete()
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Next/Complete button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                    } else {
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = Color.White
                ),
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (currentPage == totalPages - 1) {
                    Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Individual onboarding page.
 */
@Composable
fun OnboardingPage(
    page: Int,
    totalPages: Int,
    onNext: () -> Unit
) {
    when (page) {
        0 -> OnboardingPage1(onNext = onNext)
        1 -> OnboardingPage2(onNext = onNext)
        2 -> OnboardingPage3(onNext = onNext)
    }
}

/**
 * Page 1: Welcome and main feature.
 */
@Composable
fun OnboardingPage1(
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Welcome to TramApp",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Discover nearby trams and plan your route with ease.",
            fontSize = 16.sp,
            color = TextSecondary.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(100.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Find your nearest tram station",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

/**
 * Page 2: Smart features.
 */
@Composable
fun OnboardingPage2(
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Smart Features",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Get real-time updates for your favorite destinations.",
            fontSize = 16.sp,
            color = TextSecondary.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = HomeGlow,
                modifier = Modifier.size(100.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Set favorite destinations",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

/**
 * Page 3: Customization.
 */
@Composable
fun OnboardingPage3(
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Customize Your Experience",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Adjust settings to match your preferences.",
            fontSize = 16.sp,
            color = TextSecondary.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(100.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Toggle GPS tracking",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

/**
 * Quick tips for new users.
 */
@Composable
fun QuickTips(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Tip 1
        AnimatedContent(
            targetState = Icons.Default.LocationOn,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
            }
        ) { icon ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Find Me",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Tap to use your current GPS location",
                        fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tip 2
        AnimatedContent(
            targetState = Icons.Default.Refresh,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
            }
        ) { icon ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Refresh",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Pull down or tap the button to update schedules",
                        fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
