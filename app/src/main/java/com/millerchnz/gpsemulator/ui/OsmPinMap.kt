package com.millerchnz.gpsemulator.ui

import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun OsmPinMap(
    latitude: Double,
    longitude: Double,
    cameraGeneration: Int,
    pinColor: Color,
    onMapTap: (Double, Double) -> Unit,
    onPinDragged: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pinSizePx = with(density) { 28.dp.roundToPx() }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            isTilesScaledToDpi = true
            setDestroyMode(false)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(latitude, longitude))
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
    val lastCameraGen = remember { intArrayOf(-1) }
    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            if (lastCameraGen[0] != cameraGeneration) {
                lastCameraGen[0] = cameraGeneration
                map.controller.setZoom(15.0)
                map.controller.animateTo(GeoPoint(latitude, longitude))
            }
            map.overlays.clear()
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
            val marker = Marker(map)
            marker.position = GeoPoint(latitude, longitude)
            marker.title = "Mock location"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.isDraggable = true
            marker.icon = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pinColor.toArgb())
                setSize(pinSizePx, pinSizePx)
                setStroke((pinSizePx / 6).coerceAtLeast(2), android.graphics.Color.WHITE)
            }
            marker.setOnMarkerDragListener(
                object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(marker: Marker) {}
                    override fun onMarkerDragStart(marker: Marker) {}
                    override fun onMarkerDragEnd(marker: Marker) {
                        val p = marker.position
                        onPinDragged(p.latitude, p.longitude)
                    }
                },
            )
            map.overlays.add(marker)
            map.invalidate()
        },
    )
}
