package com.example.tramapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tramapp.ui.theme.*

/**
 * Bottom sheet navigation for consistent modal flows.
 */
@Composable
fun BottomSheetDialog(
    title: String,
    content: @Composable () -> Unit,
    onDismiss: (() -> Unit)? = null,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    if (!expanded) {
        onDismiss?.invoke()
        return
    }

    AlertDialog(
        onDismissRequest = { expanded = false },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(onClick = { expanded = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                    }
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        },
        confirmButton = {},
        dismissButton = {},
        containerColor = SurfaceGlass,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = modifier.widthIn(max = 600.dp)
    )
}

/**
 * Bottom sheet navigation for primary actions.
 */
@Composable
fun PrimaryActionBottomSheet(
    title: String,
    content: @Composable () -> Unit,
    onDismiss: (() -> Unit)? = null,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    BottomSheetDialog(
        title = title,
        content = content,
        onDismiss = onDismiss,
        showBackButton = showBackButton,
        modifier = modifier
    )
}

/**
 * Quick action bottom sheet for primary user actions.
 */
@Composable
fun QuickActionBottomSheet(
    title: String,
    content: @Composable () -> Unit,
    onDismiss: (() -> Unit)? = null,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    BottomSheetDialog(
        title = title,
        content = content,
        onDismiss = onDismiss,
        showBackButton = showBackButton,
        modifier = modifier
    )
}
