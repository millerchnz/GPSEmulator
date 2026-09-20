package com.millerchnz.gpsemulator.ui.common

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.millerchnz.gpsemulator.setup.SetupRequirements

@Composable
fun NoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodySmall)
            if (primaryLabel != null || secondaryLabel != null) {
                Spacer(Modifier.height(12.dp))
                Row {
                    if (primaryLabel != null && onPrimary != null) {
                        Button(
                            onClick = onPrimary,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(primaryLabel)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        OutlinedButton(
                            onClick = onSecondary,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(secondaryLabel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Background location is a separate grant from API 29, and from API 30 the system usually
 * refuses to show a dialog at all, so the card offers the request and a direct route to
 * app settings. It disappears once the permission is held.
 */
@Composable
fun BackgroundLocationNotice(
    body: String,
    modifier: Modifier = Modifier,
    onGranted: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(SetupRequirements.hasBackgroundLocation(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = SetupRequirements.hasBackgroundLocation(context)
                if (now && !granted) onGranted()
                granted = now
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val now = SetupRequirements.hasBackgroundLocation(context)
        if (now) onGranted()
        granted = now
    }

    if (granted) return

    NoticeCard(
        title = "Background location not granted",
        body = body,
        modifier = modifier,
        primaryLabel = "Request",
        onPrimary = { launcher.launch(SetupRequirements.BACKGROUND_LOCATION_PERMISSION) },
        secondaryLabel = "App settings",
        onSecondary = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}
