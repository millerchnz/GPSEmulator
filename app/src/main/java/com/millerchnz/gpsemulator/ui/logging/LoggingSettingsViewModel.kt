package com.millerchnz.gpsemulator.ui.logging

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.data.ExampleData
import com.millerchnz.gpsemulator.data.LocationFields
import com.millerchnz.gpsemulator.data.db.WebhookQueueEntity
import com.millerchnz.gpsemulator.data.net.WebhookClient
import com.millerchnz.gpsemulator.data.net.WebhookUrl
import com.millerchnz.gpsemulator.data.settings.LoggingSettings
import com.millerchnz.gpsemulator.logging.LocationLoggingService
import com.millerchnz.gpsemulator.logging.LoggingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoggingSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppGraph.settings
    private val client = WebhookClient()

    val settings: StateFlow<LoggingSettings> = store.logging
    val status: StateFlow<LoggingStatus.State> = LoggingStatus.state

    val storedRows: StateFlow<Int> = AppGraph.database.locationLogs()
        .observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pendingWebhooks: StateFlow<Int> = AppGraph.webhooks
        .observePending(WebhookQueueEntity.KIND_LOCATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val deviceId: String = store.deviceId()

    /** The preview shows the most recent real fix once there is one, so the URL is not hypothetical. */
    val previewParams: StateFlow<List<Pair<String, String>>> = LoggingStatus.state
        .map { state ->
            val row = state.lastSample?.let { sample ->
                ExampleData.locationRow.copy(
                    recordedAtMillis = state.lastFixAtMillis ?: System.currentTimeMillis(),
                    requestedProvider = store.logging.value.provider.storedName,
                    sample = sample,
                )
            } ?: ExampleData.locationRow
            LocationFields.queryParams(LocationFields.of(row))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LocationFields.queryParams(LocationFields.of(ExampleData.locationRow)),
        )

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    /**
     * The running service watches the settings flow itself, so only a change to the switch
     * has to start or stop it. Provider, interval and webhook edits are picked up in place.
     */
    fun update(transform: (LoggingSettings) -> LoggingSettings) {
        val wasEnabled = store.logging.value.enabled
        store.updateLogging(transform)
        if (store.logging.value.enabled != wasEnabled) {
            LocationLoggingService.sync(getApplication())
        }
    }

    fun dismissError() = LoggingStatus.clearError()

    fun sendTestRequest() {
        val hook = settings.value.webhook
        val url = WebhookUrl.compose(hook.baseUrl, previewParams.value, hook.extraParams, deviceId)
        if (url == null) {
            _testResult.value = WebhookUrl.validationError(hook.baseUrl) ?: "Could not build the request URL"
            return
        }
        _testing.value = true
        _testResult.value = null
        viewModelScope.launch {
            val result = client.get(url, hook.headerName, hook.headerValue)
            _testing.value = false
            _testResult.value = result.fold(
                onSuccess = { response ->
                    val body = response.body?.takeIf { it.isNotBlank() }?.let { "\n${it.take(200)}" }.orEmpty()
                    "HTTP ${response.code}$body"
                },
                onFailure = { error -> "Failed: ${error.message ?: error::class.java.simpleName}" },
            )
        }
    }
}
