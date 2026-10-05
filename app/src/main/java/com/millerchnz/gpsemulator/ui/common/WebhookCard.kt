package com.millerchnz.gpsemulator.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.millerchnz.gpsemulator.data.net.WebhookUrl
import com.millerchnz.gpsemulator.data.settings.WebhookSettings

/**
 * Shared webhook configuration block: base URL, static extras, optional auth header, a live
 * preview of the composed request and a one-off test send.
 */
@Composable
fun WebhookCard(
    settings: WebhookSettings,
    previewParams: List<Pair<String, String>>,
    deviceId: String,
    pendingCount: Int,
    testing: Boolean,
    testResult: String?,
    onChange: (WebhookSettings) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    switchModifier: Modifier = Modifier,
) {
    val urlError = if (settings.enabled && settings.baseUrl.isNotBlank()) {
        WebhookUrl.validationError(settings.baseUrl)
    } else {
        null
    }
    val preview = WebhookUrl.compose(settings.baseUrl, previewParams, settings.extraParams, deviceId)

    SectionCard(
        title = "HTTP GET webhook",
        subtitle = "Every record is also sent as query parameters on a GET request. Values the API did not report are left out of the URL.",
        modifier = modifier,
    ) {
        SwitchRow(
            title = "Send a GET for every record",
            checked = settings.enabled,
            onCheckedChange = { onChange(settings.copy(enabled = it)) },
            modifier = switchModifier,
        )
        if (settings.enabled) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = { onChange(settings.copy(baseUrl = it.trim())) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = urlError != null,
                label = { Text("Base URL") },
                placeholder = { Text("https://example.com/gpslog") },
                supportingText = urlError?.let { { Text(it) } },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = deviceId,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                label = { Text("Device id") },
                supportingText = { Text("Sent as device= on every request so this phone can be picked on the map") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.extraParams,
                onValueChange = { onChange(settings.copy(extraParams = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Extra parameters") },
                placeholder = { Text("token=abc123") },
                supportingText = { Text("key=value pairs, separated by & or newlines") },
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = settings.headerName,
                    onValueChange = { onChange(settings.copy(headerName = it.trim())) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Header") },
                    placeholder = { Text("Authorization") },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = settings.headerValue,
                    onValueChange = { onChange(settings.copy(headerValue = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Value") },
                    placeholder = { Text("Bearer …") },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Request preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = preview ?: "Enter a valid base URL to see the request",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 6,
                    modifier = Modifier
                        .padding(10.dp)
                        .horizontalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onTest,
                    enabled = !testing && preview != null,
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Send test request")
                }
            }
            if (testResult != null) {
                Spacer(Modifier.height(8.dp))
                Column {
                    Text(
                        text = testResult,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            if (pendingCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$pendingCount request(s) queued — they will be retried when the device is back online",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
