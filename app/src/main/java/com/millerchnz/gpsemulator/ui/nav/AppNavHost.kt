package com.millerchnz.gpsemulator.ui.nav

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.millerchnz.gpsemulator.setup.SetupSnapshot
import com.millerchnz.gpsemulator.ui.MapScreen
import com.millerchnz.gpsemulator.ui.geofence.GeofenceEditorScreen
import com.millerchnz.gpsemulator.ui.geofence.GeofenceEventLogScreen
import com.millerchnz.gpsemulator.ui.geofence.GeofenceSettingsScreen
import com.millerchnz.gpsemulator.ui.logging.LocationLogScreen
import com.millerchnz.gpsemulator.ui.logging.LoggingSettingsScreen

private object Routes {
    const val MAP = "map"
    const val LOGGING = "logging"
    const val LOGGING_DATA = "logging/data"
    const val GEOFENCES = "geofences"
    const val GEOFENCE_DATA = "geofences/data"
    const val GEOFENCE_EDITOR = "geofences/edit?id={id}&lat={lat}&lng={lng}"

    fun geofenceEditor(id: String? = null, latitude: Double? = null, longitude: Double? = null): String =
        "geofences/edit?id=${id.orEmpty()}&lat=${latitude ?: ""}&lng=${longitude ?: ""}"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.MAP, "Mock", Icons.Outlined.PinDrop),
    BottomDestination(Routes.LOGGING, "Logging", Icons.Outlined.Timeline),
    BottomDestination(Routes.GEOFENCES, "Geofences", Icons.Outlined.TrackChanges),
)

@Composable
fun AppNavHost(snapshot: SetupSnapshot) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (bottomDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute == destination.route) return@NavigationBarItem
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            // Each screen brings its own Scaffold, so mark the bar padding as consumed to stop
            // the navigation bar inset being applied twice.
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.MAP) {
                MapScreen(mockReady = snapshot.mockReady)
            }
            composable(Routes.LOGGING) {
                LoggingSettingsScreen(onOpenData = { navController.navigate(Routes.LOGGING_DATA) })
            }
            composable(Routes.LOGGING_DATA) {
                LocationLogScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GEOFENCES) {
                GeofenceSettingsScreen(
                    onOpenData = { navController.navigate(Routes.GEOFENCE_DATA) },
                    onEditFence = { id -> navController.navigate(Routes.geofenceEditor(id = id)) },
                    onAddFence = { latitude, longitude ->
                        navController.navigate(Routes.geofenceEditor(latitude = latitude, longitude = longitude))
                    },
                )
            }
            composable(Routes.GEOFENCE_DATA) {
                GeofenceEventLogScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.GEOFENCE_EDITOR,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                    navArgument("lng") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val arguments = entry.arguments
                GeofenceEditorScreen(
                    fenceId = arguments?.getString("id")?.takeIf { it.isNotBlank() },
                    initialLatitude = arguments?.getString("lat")?.toDoubleOrNull(),
                    initialLongitude = arguments?.getString("lng")?.toDoubleOrNull(),
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}
