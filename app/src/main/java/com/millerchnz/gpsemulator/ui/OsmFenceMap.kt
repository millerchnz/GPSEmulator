package com.millerchnz.gpsemulator.ui

import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.millerchnz.gpsemulator.data.db.GeofenceEntity
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Draws every geofence as a marker plus a radius circle.
 *
 * The geofence list screen passes the whole set with [onFenceTap] to open one for editing,
 * and the editor passes a single fence with [onFenceDragged] so the centre can be dragged.
 */
@Composable
fun OsmFenceMap(
    fences: List<GeofenceEntity>,
    cameraTarget: GeoPoint?,
    cameraGeneration: Int,
    modifier: Modifier = Modifier,
    highlightId: String? = null,
    cameraZoom: Double = 15.0,
    onMapTap: ((Double, Double) -> Unit)? = null,
    onFenceTap: ((String) -> Unit)? = null,
    onFenceDragged: ((String, Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pinSizePx = with(density) { 22.dp.roundToPx() }
    val highlightColor = MaterialTheme.colorScheme.primary.toArgb()
    val mutedColor = MaterialTheme.colorScheme.secondary.toArgb()
    val fillAlpha = 0x33000000

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            isTilesScaledToDpi = true
            setDestroyMode(false)
            controller.setZoom(cameraZoom)
            cameraTarget?.let { controller.setCenter(it) }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.overlays.clear()
            mapView.onPause()
            mapView.onDetach()
        }
    }
    val lastCameraGeneration = remember { intArrayOf(-1) }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            if (lastCameraGeneration[0] != cameraGeneration && cameraTarget != null) {
                lastCameraGeneration[0] = cameraGeneration
                map.controller.setZoom(cameraZoom)
                map.controller.animateTo(cameraTarget)
            }
            map.overlays.clear()
            if (onMapTap != null) {
                map.overlays.add(
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                onMapTap(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                onMapTap(p.latitude, p.longitude)
                                return true
                            }
                        },
                    ),
                )
            }
            fences.forEach { fence ->
                val highlighted = highlightId == null || fence.id == highlightId
                val strokeColor = if (highlighted) highlightColor else mutedColor
                val center = GeoPoint(fence.latitude, fence.longitude)
                val circle = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(center, fence.radiusMeters.toDouble())
                    outlinePaint.color = strokeColor
                    outlinePaint.strokeWidth = if (highlighted) 4f else 2f
                    fillPaint.color = strokeColor and 0x00FFFFFF or fillAlpha
                    title = fence.name
                }
                map.overlays.add(circle)

                val marker = Marker(map).apply {
                    position = center
                    title = fence.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    isDraggable = onFenceDragged != null
                    icon = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(strokeColor)
                        setSize(pinSizePx, pinSizePx)
                        setStroke((pinSizePx / 6).coerceAtLeast(2), android.graphics.Color.WHITE)
                    }
                    setOnMarkerClickListener { _, _ ->
                        onFenceTap?.invoke(fence.id)
                        onFenceTap != null
                    }
                    setOnMarkerDragListener(
                        object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) = Unit

                            override fun onMarkerDragStart(marker: Marker) = Unit

                            override fun onMarkerDragEnd(marker: Marker) {
                                onFenceDragged?.invoke(
                                    fence.id,
                                    marker.position.latitude,
                                    marker.position.longitude,
                                )
                            }
                        },
                    )
                }
                map.overlays.add(marker)
            }
            map.invalidate()
        },
    )
}

/** Picks a zoom level that keeps a fence of the given radius comfortably on screen. */
fun zoomForRadius(radiusMeters: Float): Double = when {
    radiusMeters <= 100f -> 16.0
    radiusMeters <= 250f -> 15.0
    radiusMeters <= 500f -> 14.0
    radiusMeters <= 1_000f -> 13.0
    radiusMeters <= 2_500f -> 12.0
    else -> 11.0
}

data class FenceCamera(val target: GeoPoint, val zoom: Double)

/** Centre and zoom that keep every fence (plus its radius) on screen. */
fun cameraForFences(fences: List<GeofenceEntity>): FenceCamera? {
    if (fences.isEmpty()) return null
    val minLat = fences.minOf { it.latitude }
    val maxLat = fences.maxOf { it.latitude }
    val minLng = fences.minOf { it.longitude }
    val maxLng = fences.maxOf { it.longitude }
    val extraDeg = fences.maxOf { it.radiusMeters } / 111_000.0
    val span = maxOf(maxLat - minLat, maxLng - minLng) + extraDeg * 2
    val zoom = when {
        span < 0.01 -> 15.0
        span < 0.03 -> 14.0
        span < 0.08 -> 13.0
        span < 0.2 -> 12.0
        span < 0.5 -> 11.0
        else -> 10.0
    }
    return FenceCamera(
        target = GeoPoint((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0),
        zoom = zoom,
    )
}
