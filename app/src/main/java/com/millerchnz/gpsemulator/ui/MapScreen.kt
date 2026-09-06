package com.millerchnz.gpsemulator.ui

import android.content.ClipData
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millerchnz.gpsemulator.ui.theme.PinRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var jumpText by remember { mutableStateOf("") }
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshRunning()
        }
    }
    LaunchedEffect(state.isMocking, state.mockStartedAtElapsedRealtime) {
        if (!state.isMocking || state.mockStartedAtElapsedRealtime == 0L) return@LaunchedEffect
        while (true) {
            nowElapsed = SystemClock.elapsedRealtime()
            viewModel.refreshRunning()
            delay(1_000)
        }
    }
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissError()
    }

    Box(Modifier.fillMaxSize()) {
        OsmPinMap(
            latitude = state.latitude,
            longitude = state.longitude,
            cameraGeneration = state.cameraGeneration,
            pinColor = if (state.isMocking) MaterialTheme.colorScheme.primary else PinRed,
            onMapTap = { lat, lng -> viewModel.dropPin(lat, lng) },
            onPinDragged = { lat, lng -> viewModel.dropPin(lat, lng) },
            modifier = Modifier.fillMaxSize(),
        )
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = if (state.isMocking) "Feeding this location to the phone" else "Drop a pin, then apply it",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.isMocking && state.mockStartedAtElapsedRealtime > 0L) {
                    Spacer(Modifier.height(8.dp))
                    MockElapsedTimer(
                        elapsedMs = (nowElapsed - state.mockStartedAtElapsedRealtime).coerceAtLeast(0L),
                    )
                }
                Spacer(Modifier.height(8.dp))
                CoordinateRow(
                    latitude = state.latitude,
                    longitude = state.longitude,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = jumpText,
                        onValueChange = { jumpText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Jump to lat, lng") },
                        placeholder = { Text("-36.8485, 174.7633") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            if (viewModel.applyCoordinates(jumpText)) {
                                jumpText = ""
                            }
                        },
                    ) {
                        Text("Go")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Accuracy ${state.accuracyMeters.toInt()} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.accuracyMeters,
                    onValueChange = { viewModel.setAccuracy(it) },
                    valueRange = 1f..50f,
                )
                Button(
                    onClick = { viewModel.toggleMock() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isMocking) {
                        Icon(Icons.Outlined.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop mock location")
                    } else {
                        Text("Apply mock location")
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )
    }
}

@Composable
private fun MockElapsedTimer(
    elapsedMs: Long,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Enabled for ${MapViewModel.formatElapsed(elapsedMs)}",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CoordinateRow(
    latitude: Double,
    longitude: Double,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val text = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Tap the map or drag the pin to move",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("coordinates", text)))
                }
                Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
            },
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy coordinates")
        }
    }
}
