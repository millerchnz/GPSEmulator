package com.millerchnz.gpsemulator.ui.logging

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millerchnz.gpsemulator.data.Formats
import com.millerchnz.gpsemulator.data.LocationFields
import com.millerchnz.gpsemulator.data.settings.FusedPriority
import com.millerchnz.gpsemulator.data.settings.LocationProviderChoice
import com.millerchnz.gpsemulator.data.settings.LogIntervals
import com.millerchnz.gpsemulator.data.settings.Retention
import com.millerchnz.gpsemulator.ui.MapViewModel
import com.millerchnz.gpsemulator.ui.common.BackgroundLocationNotice
import com.millerchnz.gpsemulator.ui.common.ChipRow
import com.millerchnz.gpsemulator.ui.common.FieldList
import com.millerchnz.gpsemulator.ui.common.NoticeCard
import com.millerchnz.gpsemulator.ui.common.SectionCard
import com.millerchnz.gpsemulator.ui.common.SwitchRow
import com.millerchnz.gpsemulator.ui.common.WebhookCard
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggingSettingsScreen(
    onOpenData: () -> Unit,
    viewModel: LoggingSettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val storedRows by viewModel.storedRows.collectAsStateWithLifecycle()
    val pending by viewModel.pendingWebhooks.collectAsStateWithLifecycle()
    val previewParams by viewModel.previewParams.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(status.running) {
        while (status.running) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Location logging") }) },
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
                title = "Recording",
                subtitle = "Writes every value the Android location API reports, including the fields it leaves unset.",
            ) {
                SwitchRow(
                    title = "Log locations",
                    subtitle = if (status.running) "Service running" else "Service stopped",
                    checked = settings.enabled,
                    onCheckedChange = { enabled -> viewModel.update { it.copy(enabled = enabled) } },
                )
                if (status.running) {
                    Spacer(Modifier.height(12.dp))
                    val elapsed = (nowElapsed - status.startedAtElapsedRealtime).coerceAtLeast(0L)
                    Text(
                        text = "${Formats.count(status.sessionCount)} fixes this session · running for " +
                            MapViewModel.formatElapsed(elapsed),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val sample = status.lastSample
                    if (sample?.latitude != null && sample.longitude != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Last fix ${Formats.coordinates(sample.latitude, sample.longitude)}" +
                                (status.lastFixAtMillis?.let { " at ${Formats.timeOnly(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            val error = status.error
            if (error != null) {
                NoticeCard(
                    title = "Logging problem",
                    body = error,
                    primaryLabel = "Dismiss",
                    onPrimary = viewModel::dismissError,
                )
            }

            BackgroundLocationNotice(
                body = "Without \"Allow all the time\" Android may stop delivering fixes once the app leaves the screen, " +
                    "which matters most for the longer intervals.",
            )

            SectionCard(
                title = "Provider",
                subtitle = "Which API the log reads from. The reporting provider is stored on every row as well.",
            ) {
                LocationProviderChoice.entries.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.update { it.copy(provider = choice) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.provider == choice,
                            onClick = { viewModel.update { it.copy(provider = choice) } },
                        )
                        Column {
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = choice.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (settings.provider == LocationProviderChoice.FUSED) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Request priority",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChipRow(
                        options = FusedPriority.entries,
                        selected = settings.fusedPriority,
                        label = { it.label },
                        onSelect = { priority -> viewModel.update { it.copy(fusedPriority = priority) } },
                    )
                }
            }

            SectionCard(
                title = "Frequency",
                subtitle = "The interval requested from the provider. Fixes can arrive slightly later than asked for.",
            ) {
                Text(
                    text = LogIntervals.label(settings.intervalMillis),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = LogIntervals.indexOf(settings.intervalMillis).toFloat(),
                    onValueChange = { raw ->
                        val interval = LogIntervals.optionsMillis[
                            raw.roundToInt().coerceIn(0, LogIntervals.optionsMillis.lastIndex),
                        ]
                        viewModel.update { it.copy(intervalMillis = interval) }
                    },
                    valueRange = 0f..LogIntervals.optionsMillis.lastIndex.toFloat(),
                    steps = LogIntervals.optionsMillis.size - 2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Every second", style = MaterialTheme.typography.bodySmall)
                    Text("Every 30 minutes", style = MaterialTheme.typography.bodySmall)
                }
            }

            WebhookCard(
                settings = settings.webhook,
                previewParams = previewParams,
                deviceId = viewModel.deviceId,
                pendingCount = pending,
                testing = testing,
                testResult = testResult,
                onChange = { webhook -> viewModel.update { it.copy(webhook = webhook) } },
                onTest = viewModel::sendTestRequest,
            )

            SectionCard(
                title = "Collected data",
                subtitle = "${Formats.count(storedRows)} rows stored, trimmed to the newest " +
                    "${Formats.count(Retention.MAX_ROWS)}.",
            ) {
                val sample = status.lastSample
                if (sample != null) {
                    FieldList(fields = LocationFields.of(sample))
                    Spacer(Modifier.height(12.dp))
                }
                FilledTonalButton(onClick = onOpenData, modifier = Modifier.fillMaxWidth()) {
                    Text("View collected data")
                }
            }
        }
    }
}
