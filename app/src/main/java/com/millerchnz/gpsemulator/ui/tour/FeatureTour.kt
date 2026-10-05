package com.millerchnz.gpsemulator.ui.tour

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.millerchnz.gpsemulator.ui.nav.AppRoutes
import kotlinx.coroutines.flow.first

/**
 * Anchors the tour can point at. [BOTTOM_NAV] is not a step; the scrim leaves it visible
 * so the tabs stay readable while the tour moves between them.
 */
object TourAnchors {
    const val APPLY_MOCK = "apply_mock"
    const val LOG_ENABLED = "log_enabled"
    const val LOG_SEND = "log_send"
    const val LOG_VIEW = "log_view"
    const val FENCE_ENABLED = "fence_enabled"
    const val FENCE_ADD = "fence_add"
    const val FENCE_ALERTS = "fence_alerts"
    const val FENCE_SEND = "fence_send"
    const val FENCE_EVENTS = "fence_events"
    const val BOTTOM_NAV = "bottom_nav"
}

data class TourStep(
    val anchor: String,
    val route: String,
    val title: String,
    val body: String,
)

/**
 * One pass across the three tabs: mock, then everything on Logging, then everything on Geofences.
 * Order matches how someone would turn the features on, without jumping backwards.
 */
val TourSteps: List<TourStep> = listOf(
    TourStep(
        anchor = TourAnchors.APPLY_MOCK,
        route = AppRoutes.MAP,
        title = "Mock location",
        body = "Drop a pin, drag it, or type coordinates, then apply them. Other apps will read that " +
            "point as this phone's GPS position. In Developer options, set \"Select mock location app\" " +
            "to GPS Debug Tool first.",
    ),
    TourStep(
        anchor = TourAnchors.LOG_ENABLED,
        route = AppRoutes.LOGGING,
        title = "Location logging",
        body = "Turn on Log locations to store the phone's real fixes on a timer, including fields " +
            "Android leaves unset. With location allowed all the time, it keeps running in the background.",
    ),
    TourStep(
        anchor = TourAnchors.LOG_SEND,
        route = AppRoutes.LOGGING,
        title = "Send location logs",
        body = "Turn on Send a GET for every record to forward each fix to your server. Add the base URL, " +
            "then use the test button to check the request.",
    ),
    TourStep(
        anchor = TourAnchors.LOG_VIEW,
        route = AppRoutes.LOGGING,
        title = "Logged locations",
        body = "View collected data opens every stored fix, with each value the location API reported.",
    ),
    TourStep(
        anchor = TourAnchors.FENCE_ENABLED,
        route = AppRoutes.GEOFENCES,
        title = "Geofencing",
        body = "Turn on Watch geofences to register your fences with Play Services. Entry, exit, and dwell " +
            "can be reported even while this app is closed.",
    ),
    TourStep(
        anchor = TourAnchors.FENCE_ADD,
        route = AppRoutes.GEOFENCES,
        title = "Add a geofence",
        body = "Tap the map, or Add geofence, to place a location. Each fence has its own centre, radius, " +
            "and which transitions it watches.",
    ),
    TourStep(
        anchor = TourAnchors.FENCE_ALERTS,
        route = AppRoutes.GEOFENCES,
        title = "Geofence notifications",
        body = "Choose alerts for entry, exit, and dwell. You get a toast while the app is open, and a " +
            "notification when it is not.",
    ),
    TourStep(
        anchor = TourAnchors.FENCE_SEND,
        route = AppRoutes.GEOFENCES,
        title = "Send geofence events",
        body = "This switch sends each transition to your server as a GET, the same way location logs can. " +
            "Set a URL here if you want those events forwarded.",
    ),
    TourStep(
        anchor = TourAnchors.FENCE_EVENTS,
        route = AppRoutes.GEOFENCES,
        title = "Geofence events",
        body = "View collected events lists every entry, exit, and dwell, along with the location that triggered it.",
    ),
)

val LocalFeatureTour = staticCompositionLocalOf<FeatureTourController?> { null }

/**
 * Step index for the feature tour. Bounds are reported by [tourAnchor] and read by the overlay.
 * [onFinished] runs when the tour is completed or skipped, so the first-run flag can be stored.
 */
class FeatureTourController(
    initialIndex: Int = -1,
    private val onFinished: () -> Unit = {},
    private val onIndex: (Int) -> Unit = {},
) {
    var index by mutableIntStateOf(initialIndex.coerceIn(-1, TourSteps.lastIndex))
        private set

    val bounds = mutableStateMapOf<String, Rect>()

    val step: TourStep? get() = TourSteps.getOrNull(index)
    val active: Boolean get() = step != null
    val isLast: Boolean get() = index == TourSteps.lastIndex

    fun start() = moveTo(0)

    fun next() {
        if (!active) return
        if (isLast) finish() else moveTo(index + 1)
    }

    fun previous() {
        if (index > 0) moveTo(index - 1)
    }

    fun skipAll() {
        if (active) finish()
    }

    fun report(key: String, rect: Rect) {
        val stable = rect.rounded()
        if (bounds[key] == stable) return
        bounds[key] = stable
    }

    fun forget(key: String) {
        bounds.remove(key)
    }

    private fun finish() {
        moveTo(-1)
        bounds.clear()
        onFinished()
    }

    private fun moveTo(value: Int) {
        index = value
        onIndex(value)
    }
}

private fun Rect.rounded(): Rect = Rect(
    left = left.toInt().toFloat(),
    top = top.toInt().toFloat(),
    right = right.toInt().toFloat(),
    bottom = bottom.toInt().toFloat(),
)

/**
 * Reports this node's bounds and, when it is the current step, scrolls it into view with room
 * underneath for the speech bubble.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tourAnchor(key: String): Modifier {
    val tour = LocalFeatureTour.current
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var nodeSize by remember { mutableStateOf(IntSize.Zero) }
    var positioned by remember { mutableStateOf(false) }
    val active = tour?.step?.anchor == key

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { positioned }.first { it }
        val size = nodeSize
        val extraBelow = with(density) { 220.dp.toPx() }
        requester.bringIntoView(
            Rect(
                left = 0f,
                top = 0f,
                right = size.width.toFloat().coerceAtLeast(1f),
                bottom = size.height + extraBelow,
            ),
        )
    }
    DisposableEffect(tour, key) {
        onDispose { tour?.forget(key) }
    }
    if (tour == null) return this
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            val size = coordinates.size
            if (size.width <= 0 || size.height <= 0) return@onGloballyPositioned
            nodeSize = size
            positioned = true
            tour.report(key, coordinates.boundsInRoot())
        }
}
