package com.millerchnz.gpsemulator.ui.geofence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millerchnz.gpsemulator.data.Formats
import com.millerchnz.gpsemulator.data.LocationFields
import com.millerchnz.gpsemulator.data.db.GeofenceEventEntity
import com.millerchnz.gpsemulator.data.db.GeofenceTransition
import com.millerchnz.gpsemulator.data.export.LogExporter
import com.millerchnz.gpsemulator.ui.common.BackButton
import com.millerchnz.gpsemulator.ui.common.ChipRow
import com.millerchnz.gpsemulator.ui.common.ConfirmClearDialog
import com.millerchnz.gpsemulator.ui.common.ExportMenu
import com.millerchnz.gpsemulator.ui.common.FieldList
import com.millerchnz.gpsemulator.ui.common.TimeRange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceEventLogScreen(
    onBack: () -> Unit,
    viewModel: GeofenceEventLogViewModel = viewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val matching by viewModel.matchingCount.collectAsStateWithLifecycle()
    val fences by viewModel.fences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var exportMenuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geofence events") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    Box {
                        IconButton(onClick = { exportMenuOpen = true }) {
                            Icon(Icons.Outlined.IosShare, contentDescription = "Export")
                        }
                        ExportMenu(
                            expanded = exportMenuOpen,
                            onDismiss = { exportMenuOpen = false },
                            onExport = { format ->
                                viewModel.export(
                                    format = format,
                                    onReady = { uri ->
                                        context.startActivity(LogExporter.shareIntent(uri, format))
                                    },
                                    onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
                                )
                            },
                        )
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Delete all")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ChipRow(
                    options = TimeRange.entries,
                    selected = filter.range,
                    label = { it.label },
                    onSelect = viewModel::setRange,
                )
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    options = listOf(null) + GeofenceTransition.entries,
                    selected = filter.transition,
                    label = { it?.label ?: "Any transition" },
                    onSelect = viewModel::setTransition,
                )
                if (fences.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        options = listOf(null) + fences.map { it.id },
                        selected = filter.fenceId,
                        label = { id -> id?.let { key -> fences.first { it.id == key }.name } ?: "Any fence" },
                        onSelect = viewModel::setFence,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (matching > rows.size) {
                        "Showing the newest ${Formats.count(rows.size)} of ${Formats.count(matching)} matching events"
                    } else {
                        "${Formats.count(matching)} matching events"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No geofence transitions recorded for this filter yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.id }) { row -> EventRow(row) }
                    if (matching > rows.size) {
                        item(key = "load-more") {
                            TextButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Load older events (${Formats.count(matching - rows.size)} remaining)")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmClearDialog(
            recordLabel = "geofence events",
            onConfirm = viewModel::clearAll,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun EventRow(row: GeofenceEventEntity) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    val transition = GeofenceTransition.fromStoredName(row.transition)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${transition.label} ${row.fenceName ?: row.fenceId ?: ""}".trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (transition == GeofenceTransition.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = Formats.timeOnly(row.recordedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            val latitude = row.sample.latitude
            val longitude = row.sample.longitude
            Text(
                text = row.errorMessage
                    ?: if (latitude != null && longitude != null) {
                        Formats.coordinates(latitude, longitude)
                    } else {
                        "No triggering location reported"
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildList {
                    row.distanceFromCenterMeters?.let { add("${Formats.distance(it)} from centre") }
                    row.fenceRadiusMeters?.let { add("r ${Formats.decimals(it, 0)} m") }
                    row.sample.accuracyMeters?.let { add("±${Formats.decimals(it, 1)} m") }
                    if (row.sample.isMock == true) add("mock")
                }.joinToString(" · ").ifBlank { "No additional values reported" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                FieldList(fields = LocationFields.of(row))
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap for all reported values",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
