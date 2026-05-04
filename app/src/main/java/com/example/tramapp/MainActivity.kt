package com.example.tramapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.tramapp.ui.DashboardScreen
import com.example.tramapp.ui.SettingsScreen
import com.example.tramapp.ui.theme.TramAppTheme
import com.example.tramapp.ui.theme.DeepBlack
import com.example.tramapp.ui.theme.SurfaceGlass
import com.example.tramapp.ui.theme.AccentViolet
import com.example.tramapp.ui.theme.TextSecondary
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TramAppTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) { /* Handle results if needed */ }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                val navController = rememberNavController()
                val items = listOf(
                    NavigationItem("Dashboard", "dashboard", Icons.Default.Home),
                    NavigationItem("Settings", "settings", Icons.Default.Settings)
                )

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceGlass,
                            contentColor = Color.White,
                            tonalElevation = 8.dp
                        ) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { item ->
                                NavigationBarItem(
                                    icon = { Icon(item.icon, contentDescription = item.title) },
                                    label = { Text(item.title) },
                                    selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentViolet,
                                        selectedTextColor = AccentViolet,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") { DashboardScreen() }
                        composable("settings") { 
                            SettingsScreen(
                                onNavigateToMap = { locationType ->
                                    navController.navigate("map_picker/$locationType")
                                }
                            ) 
                        }
                        composable("map_picker/{locationType}") { backStackEntry ->
                            val locationType = backStackEntry.arguments?.getString("locationType") ?: "Home"
                            val settingsViewModel: com.example.tramapp.ui.SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                            val snackbarHostState = remember { SnackbarHostState() }
                            val scope = rememberCoroutineScope()

                            androidx.compose.material3.Scaffold(
                                snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
                                containerColor = androidx.compose.ui.graphics.Color.Transparent
                            ) { _ ->
                                com.example.tramapp.ui.MapPickerScreen(
                                    locationType = locationType,
                                    onLocationSelected = { lat, lng, address ->
                                        settingsViewModel.updateLocation(locationType, lat, lng, address)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("$locationType location saved!")
                                        }
                                        navController.popBackStack()
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class NavigationItem(val title: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)



