package com.millerchnz.gpsemulator.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.millerchnz.gpsemulator.data.export.LogExporter
import java.util.concurrent.TimeUnit

enum class TimeRange(val label: String, private val windowMillis: Long?) {
    ALL("All time", null),
    LAST_HOUR("Last hour", TimeUnit.HOURS.toMillis(1)),
    LAST_DAY("Last 24 hours", TimeUnit.DAYS.toMillis(1)),
    LAST_WEEK("Last 7 days", TimeUnit.DAYS.toMillis(7)),
    ;

    /** Evaluated when the filter changes, so the window is anchored to the moment it was picked. */
    fun startMillis(now: Long = System.currentTimeMillis()): Long = windowMillis?.let { now - it } ?: 0L
}

@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
fun ExportMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onExport: (LogExporter.Format) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        LogExporter.Format.entries.forEach { format ->
            DropdownMenuItem(
                text = { Text("Export as ${format.label}") },
                onClick = {
                    onDismiss()
                    onExport(format)
                },
            )
        }
    }
}

@Composable
fun ConfirmClearDialog(
    recordLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all $recordLabel?") },
        text = { Text("This removes every stored record. It cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text("Delete all")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
