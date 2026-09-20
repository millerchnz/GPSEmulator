package com.millerchnz.gpsemulator.ui.geofence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millerchnz.gpsemulator.data.Formats
import com.millerchnz.gpsemulator.data.db.GeofenceEntity
import com.millerchnz.gpsemulator.ui.OsmFenceMap
import com.millerchnz.gpsemulator.ui.common.BackButton
import com.millerchnz.gpsemulator.ui.common.ChipRow
import com.millerchnz.gpsemulator.ui.common.SectionCard
import com.millerchnz.gpsemulator.ui.common.SwitchRow
import com.millerchnz.gpsemulator.ui.zoomForRadius
import org.osmdroid.util.GeoPoint

private val LOITERING_OPTIONS_SECONDS = listOf(30, 60, 300, 600)
private val RESPONSIVENESS_OPTIONS_SECONDS = listOf(0, 30, 120, 300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceEditorScreen(
    fenceId: String?,
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDone: () -> Unit,
    viewModel: GeofenceEditorViewModel = viewModel(),
) {
    LaunchedEffect(fenceId, initialLatitude, initialLongitude) {
        viewModel.load(fenceId, initialLatitude, initialLongitude)
    }
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isNew by viewModel.isNew.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add geofence" else "Edit geofence") },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { viewModel.delete(onDone) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete fence")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val fence = draft
        if (fence == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OsmFenceMap(
                fences = listOf(fence),
                cameraTarget = GeoPoint(fence.latitude, fence.longitude),
                cameraGeneration = 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp)),
                cameraZoom = zoomForRadius(fence.radiusMeters),
                onMapTap = { latitude, longitude ->
                    viewModel.update { it.copy(latitude = latitude, longitude = longitude) }
                },
                onFenceDragged = { _, latitude, longitude ->
                    viewModel.update { it.copy(latitude = latitude, longitude = longitude) }
                },
            )
            Text(
                text = "Centre ${Formats.coordinates(fence.latitude, fence.longitude)} — tap the map or drag the pin",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = fence.name,
                onValueChange = { name -> viewModel.update { it.copy(name = name) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("Home") },
            )

            SectionCard(
                title = "Radius",
                subtitle = "1 m to 250 m.",
            ) {
                Text(
                    text = Formats.distance(fence.radiusMeters),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = fence.radiusMeters,
                    onValueChange = { radius -> viewModel.update { it.copy(radiusMeters = radius) } },
                    valueRange = GeofenceEntity.MIN_RADIUS_METERS..GeofenceEntity.MAX_RADIUS_METERS,
                )
            }

            SectionCard(
                title = "Transitions to watch",
                subtitle = "Maps directly onto the transition types passed to Geofence.Builder.",
            ) {
                SwitchRow(
                    title = "Entry",
                    checked = fence.watchEnter,
                    onCheckedChange = { on -> viewModel.update { it.copy(watchEnter = on) } },
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Exit",
                    checked = fence.watchExit,
                    onCheckedChange = { on -> viewModel.update { it.copy(watchExit = on) } },
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Dwell",
                    subtitle = "Fires after the device stays inside for the loitering delay",
                    checked = fence.watchDwell,
                    onCheckedChange = { on -> viewModel.update { it.copy(watchDwell = on) } },
                )
                if (fence.watchDwell) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Loitering delay",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChipRow(
                        options = LOITERING_OPTIONS_SECONDS,
                        selected = fence.loiteringDelayMillis / 1_000,
                        label = ::secondsLabel,
                        onSelect = { seconds ->
                            viewModel.update { it.copy(loiteringDelayMillis = seconds * 1_000) }
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Notification responsiveness",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "A larger value lets Android batch transitions to save power.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = RESPONSIVENESS_OPTIONS_SECONDS,
                    selected = fence.responsivenessMillis / 1_000,
                    label = { if (it == 0) "Fastest" else secondsLabel(it) },
                    onSelect = { seconds ->
                        viewModel.update { it.copy(responsivenessMillis = seconds * 1_000) }
                    },
                )
            }

            if (!fence.hasAnyTransition) {
                Text(
                    text = "Pick at least one transition before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = fence.hasAnyTransition,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isNew) "Add geofence" else "Save changes")
            }
        }
    }
}

private fun secondsLabel(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 && seconds < 3_600 -> "${seconds / 60} min"
    else -> "${seconds / 3_600} h"
}
