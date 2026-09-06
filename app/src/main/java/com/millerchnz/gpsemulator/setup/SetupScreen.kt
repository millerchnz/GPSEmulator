package com.millerchnz.gpsemulator.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberSetupSnapshot(): SetupSnapshot {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(SetupRequirements.snapshot(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                snapshot = SetupRequirements.snapshot(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return snapshot
}

@Composable
fun SetupScreen(
    snapshot: SetupSnapshot,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onRefresh() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRefresh() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PinDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Before you start",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "GPS Emulator can only feed a fake location to other apps after these checks pass. Nothing on the map runs until they do.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            RequirementCard(
                icon = Icons.Outlined.MyLocation,
                title = "Precise location",
                body = "Needed so the mock feed can replace the real GPS provider.",
                done = snapshot.hasFineLocation,
                actionLabel = "Grant",
                onAction = {
                    locationLauncher.launch(SetupRequirements.locationPermissions)
                },
            )
            Spacer(Modifier.height(12.dp))
            RequirementCard(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                body = "A persistent notification is required while a mock location is active.",
                done = snapshot.hasNotifications,
                actionLabel = "Grant",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            RequirementCard(
                icon = Icons.Outlined.LocationOn,
                title = "Device location on",
                body = "Turn on Location in system settings so apps actually listen for a position.",
                done = snapshot.locationEnabled,
                actionLabel = "Open settings",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
            )
            Spacer(Modifier.height(12.dp))
            RequirementCard(
                icon = Icons.Outlined.BugReport,
                title = "Developer options",
                body = "Unlock Developer options (tap Build number seven times), then return here.",
                done = snapshot.developerOptionsEnabled,
                actionLabel = "Open settings",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                },
            )
            Spacer(Modifier.height(12.dp))
            RequirementCard(
                icon = Icons.Outlined.PinDrop,
                title = "Select mock location app",
                body = "In Developer options, set “Select mock location app” to GPS Emulator.",
                done = snapshot.isMockLocationApp,
                actionLabel = "Open developer options",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
            if (!snapshot.allReady) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("App permission settings")
                }
            }
        }
    }
}

@Composable
private fun RequirementCard(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (done) Icons.Outlined.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!done) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
