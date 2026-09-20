package com.millerchnz.gpsemulator.ui.geofence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.millerchnz.gpsemulator.geofence.GeofenceSyncResult
import com.millerchnz.gpsemulator.ui.OsmFenceMap
import com.millerchnz.gpsemulator.ui.cameraForFences
import com.millerchnz.gpsemulator.ui.common.BackgroundLocationNotice
import com.millerchnz.gpsemulator.ui.common.NoticeCard
import com.millerchnz.gpsemulator.ui.common.SectionCard
import com.millerchnz.gpsemulator.ui.common.SwitchRow
import com.millerchnz.gpsemulator.ui.common.WebhookCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceSettingsScreen(
    onOpenData: () -> Unit,
    onEditFence: (String) -> Unit,
    onAddFence: (Double?, Double?) -> Unit,
    viewModel: GeofenceSettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val fences by viewModel.fences.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val eventCount by viewModel.eventCount.collectAsStateWithLifecycle()
    val pending by viewModel.pendingWebhooks.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Geofences") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(
                title = "Monitoring",
                subtitle = "Registers your fences with the Play Services geofencing API, which reports " +
                    "transitions even while the app is closed.",
            ) {
                SwitchRow(
                    title = "Watch geofences",
                    subtitle = status.describe(),
                    checked = settings.enabled,
                    onCheckedChange = { enabled -> viewModel.update { it.copy(enabled = enabled) } },
                )
            }

            (status as? GeofenceSyncResult.Failed)?.let { failed ->
                NoticeCard(title = "Play Services rejected the registration", body = failed.message)
            }
            (status as? GeofenceSyncResult.MissingPermission)?.let { missing ->
                NoticeCard(title = "Cannot register geofences", body = missing.message)
            }

            BackgroundLocationNotice(
                body = "Play Services will not deliver geofence transitions without \"Allow all the time\".",
                onGranted = viewModel::resync,
            )

            SectionCard(
                title = "Alerts",
                subtitle = "Shown as a toast while the app is open, and as a notification when it is not.",
            ) {
                SwitchRow(
                    title = "Alert on entry",
                    checked = settings.notifyOnEntry,
                    onCheckedChange = { on -> viewModel.update { it.copy(notifyOnEntry = on) } },
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Alert on exit",
                    checked = settings.notifyOnExit,
                    onCheckedChange = { on -> viewModel.update { it.copy(notifyOnExit = on) } },
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Alert on dwell",
                    subtitle = "Only fires for fences that watch the dwell transition",
                    checked = settings.notifyOnDwell,
                    onCheckedChange = { on -> viewModel.update { it.copy(notifyOnDwell = on) } },
                )
            }

            SectionCard(
                title = "Fenced locations",
                subtitle = "Tap the map to add a fence, or tap a circle to edit it. Every fence carries its own radius.",
            ) {
                val overviewCamera = cameraForFences(fences)
                OsmFenceMap(
                    fences = fences,
                    cameraTarget = overviewCamera?.target,
                    // Only recentre when the list goes from empty to non-empty, so edits don't steal the pan.
                    cameraGeneration = if (fences.isEmpty()) 0 else 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    cameraZoom = overviewCamera?.zoom ?: 13.0,
                    onMapTap = { latitude, longitude -> onAddFence(latitude, longitude) },
                    onFenceTap = onEditFence,
                )
                Spacer(Modifier.height(12.dp))
                if (fences.isEmpty()) {
                    Text(
                        text = "No fences yet. Tap the map or use Add geofence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                } else {
                    fences.forEach { fence ->
                        FenceRow(
                            fence = fence,
                            onEdit = { onEditFence(fence.id) },
                            onDelete = { viewModel.deleteFence(fence.id) },
                            onToggle = { enabled -> viewModel.setFenceEnabled(fence, enabled) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Button(onClick = { onAddFence(null, null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add geofence")
                }
            }

            WebhookCard(
                settings = settings.webhook,
                previewParams = viewModel.previewParams,
                deviceId = viewModel.deviceId,
                pendingCount = pending,
                testing = testing,
                testResult = testResult,
                onChange = { webhook -> viewModel.update { it.copy(webhook = webhook) } },
                onTest = viewModel::sendTestRequest,
            )

            SectionCard(
                title = "Collected data",
                subtitle = "${Formats.count(eventCount)} transitions recorded, each with every value the " +
                    "triggering location reported.",
            ) {
                FilledTonalButton(onClick = onOpenData, modifier = Modifier.fillMaxWidth()) {
                    Text("View collected events")
                }
            }
        }
    }
}

@Composable
private fun FenceRow(
    fence: GeofenceEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(fence.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = Formats.coordinates(fence.latitude, fence.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildList {
                        add("r ${Formats.decimals(fence.radiusMeters, 0)} m")
                        if (fence.watchEnter) add("enter")
                        if (fence.watchExit) add("exit")
                        if (fence.watchDwell) add("dwell ${fence.loiteringDelayMillis / 1_000}s")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = fence.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${fence.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${fence.name}")
            }
        }
    }
}

private fun GeofenceSyncResult.describe(): String = when (this) {
    is GeofenceSyncResult.Disabled -> "Not registered"
    is GeofenceSyncResult.NoFences -> "Enabled, but no fences are active"
    is GeofenceSyncResult.Registered -> "$count fence(s) registered with Play Services"
    is GeofenceSyncResult.MissingPermission -> "Blocked: $message"
    is GeofenceSyncResult.Failed -> "Registration failed: $message"
}
