package com.millerchnz.gpsemulator.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.setup.SetupSnapshot
import com.millerchnz.gpsemulator.ui.MapScreen
import com.millerchnz.gpsemulator.ui.about.AboutScreen
import com.millerchnz.gpsemulator.ui.geofence.GeofenceEditorScreen
import com.millerchnz.gpsemulator.ui.geofence.GeofenceEventLogScreen
import com.millerchnz.gpsemulator.ui.geofence.GeofenceSettingsScreen
import com.millerchnz.gpsemulator.ui.logging.LocationLogScreen
import com.millerchnz.gpsemulator.ui.logging.LoggingSettingsScreen
import com.millerchnz.gpsemulator.ui.tour.FeatureTourController
import com.millerchnz.gpsemulator.ui.tour.FeatureTourOverlay
import com.millerchnz.gpsemulator.ui.tour.LocalFeatureTour
import com.millerchnz.gpsemulator.ui.tour.TourAnchors
import com.millerchnz.gpsemulator.ui.tour.tourAnchor

internal object AppRoutes {
    const val MAP = "map"
    const val ABOUT = "about"
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
    BottomDestination(AppRoutes.MAP, "Mock", Icons.Outlined.PinDrop),
    BottomDestination(AppRoutes.LOGGING, "Logging", Icons.Outlined.Timeline),
    BottomDestination(AppRoutes.GEOFENCES, "Geofences", Icons.Outlined.TrackChanges),
    BottomDestination(AppRoutes.ABOUT, "About", Icons.Outlined.Info),
)

@Composable
fun AppNavHost(snapshot: SetupSnapshot) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var savedTourIndex by rememberSaveable { mutableIntStateOf(-1) }
    val tour = remember {
        FeatureTourController(
            initialIndex = savedTourIndex,
            onFinished = { AppGraph.settings.setFeatureTourCompleted() },
            onIndex = { savedTourIndex = it },
        )
    }
    // System back moves to the previous tip. Skip all is the way out, so back cannot pop the tab
    // out from under the step that is currently showing.
    BackHandler(enabled = tour.active) { tour.previous() }
    LaunchedEffect(Unit) {
        if (!tour.active && !AppGraph.settings.isFeatureTourCompleted()) {
            tour.start()
        }
    }
    val stepRoute = tour.step?.route
    LaunchedEffect(stepRoute) {
        val route = stepRoute ?: return@LaunchedEffect
        if (currentRoute == route) return@LaunchedEffect
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalFeatureTour provides tour) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    if (bottomDestinations.any { it.route == currentRoute }) {
                        NavigationBar(modifier = Modifier.tourAnchor(TourAnchors.BOTTOM_NAV)) {
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
                    startDestination = AppRoutes.MAP,
                    // Each screen brings its own Scaffold, so mark the bar padding as consumed to stop
                    // the navigation bar inset being applied twice.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                ) {
                    composable(AppRoutes.MAP) {
                        MapScreen(mockReady = snapshot.mockReady)
                    }
                    composable(AppRoutes.ABOUT) {
                        AboutScreen(onShowHelp = tour::start)
                    }
                    composable(AppRoutes.LOGGING) {
                        LoggingSettingsScreen(
                            onOpenData = { navController.navigate(AppRoutes.LOGGING_DATA) },
                        )
                    }
                    composable(AppRoutes.LOGGING_DATA) {
                        LocationLogScreen(onBack = { navController.popBackStack() })
                    }
                    composable(AppRoutes.GEOFENCES) {
                        GeofenceSettingsScreen(
                            onOpenData = { navController.navigate(AppRoutes.GEOFENCE_DATA) },
                            onEditFence = { id -> navController.navigate(AppRoutes.geofenceEditor(id = id)) },
                            onAddFence = { latitude, longitude ->
                                navController.navigate(AppRoutes.geofenceEditor(latitude = latitude, longitude = longitude))
                            },
                        )
                    }
                    composable(AppRoutes.GEOFENCE_DATA) {
                        GeofenceEventLogScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = AppRoutes.GEOFENCE_EDITOR,
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
            FeatureTourOverlay(tour)
        }
    }
}
