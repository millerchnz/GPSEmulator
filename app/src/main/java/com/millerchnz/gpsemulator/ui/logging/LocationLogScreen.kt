package com.millerchnz.gpsemulator.ui.logging

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
import com.millerchnz.gpsemulator.data.db.LocationLogEntity
import com.millerchnz.gpsemulator.data.export.LogExporter
import com.millerchnz.gpsemulator.data.settings.LocationProviderChoice
import com.millerchnz.gpsemulator.ui.common.BackButton
import com.millerchnz.gpsemulator.ui.common.ChipRow
import com.millerchnz.gpsemulator.ui.common.ConfirmClearDialog
import com.millerchnz.gpsemulator.ui.common.ExportMenu
import com.millerchnz.gpsemulator.ui.common.FieldList
import com.millerchnz.gpsemulator.ui.common.TimeRange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationLogScreen(
    onBack: () -> Unit,
    viewModel: LocationLogViewModel = viewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val matching by viewModel.matchingCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var exportMenuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location log") },
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
                    options = listOf(null) + LocationProviderChoice.entries,
                    selected = filter.provider,
                    label = { it?.label ?: "Any provider" },
                    onSelect = viewModel::setProvider,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (matching > rows.size) {
                        "Showing the newest ${Formats.count(rows.size)} of ${Formats.count(matching)} matching rows"
                    } else {
                        "${Formats.count(matching)} matching rows"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No fixes recorded for this filter yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.id }) { row -> LogRow(row) }
                    if (matching > rows.size) {
                        item(key = "load-more") {
                            TextButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Load older rows (${Formats.count(matching - rows.size)} remaining)")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmClearDialog(
            recordLabel = "location rows",
            onConfirm = viewModel::clearAll,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun LogRow(row: LocationLogEntity) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
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
                    text = Formats.timeOnly(row.recordedAtMillis),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = row.sample.provider ?: row.requestedProvider,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            val latitude = row.sample.latitude
            val longitude = row.sample.longitude
            Text(
                text = if (latitude != null && longitude != null) {
                    Formats.coordinates(latitude, longitude)
                } else {
                    "No position reported"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildList {
                    row.sample.accuracyMeters?.let { add("±${Formats.decimals(it, 1)} m") }
                    row.sample.speedMetersPerSecond?.let { add("${Formats.decimals(it, 2)} m/s") }
                    row.sample.altitudeMeters?.let { add("${Formats.decimals(it, 0)} m alt") }
                    if (row.sample.isMock == true) add("mock")
                }.joinToString(" · ").ifBlank { "No accuracy reported" },
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
