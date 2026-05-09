package com.example.tramapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Unified navigation helper for consistent bottom bar behavior.
 */
@Composable
fun rememberNavigation(
    navController: NavHostController,
    selectedRoute: String = "dashboard"
): (String) -> Unit {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    return remember(navController, selectedRoute) {
        { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
}

/**
 * Check if a route is currently selected.
 */
@Composable
fun isRouteSelected(
    navController: NavHostController,
    route: String
): Boolean {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    return currentDestination?.hierarchy?.any { it.route == route } ?: false
}
