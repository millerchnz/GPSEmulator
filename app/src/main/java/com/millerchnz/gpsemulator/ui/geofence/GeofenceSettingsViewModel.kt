package com.millerchnz.gpsemulator.ui.geofence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.data.ExampleData
import com.millerchnz.gpsemulator.data.LocationFields
import com.millerchnz.gpsemulator.data.db.GeofenceEntity
import com.millerchnz.gpsemulator.data.db.WebhookQueueEntity
import com.millerchnz.gpsemulator.data.net.WebhookClient
import com.millerchnz.gpsemulator.data.net.WebhookUrl
import com.millerchnz.gpsemulator.data.settings.GeofenceSettings
import com.millerchnz.gpsemulator.geofence.GeofenceStatus
import com.millerchnz.gpsemulator.geofence.GeofenceSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GeofenceSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppGraph.settings
    private val dao = AppGraph.database.geofences()
    private val client = WebhookClient()

    val settings: StateFlow<GeofenceSettings> = store.geofence
    val status: StateFlow<GeofenceSyncResult> = GeofenceStatus.state

    val fences: StateFlow<List<GeofenceEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val eventCount: StateFlow<Int> = AppGraph.database.geofenceEvents()
        .observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pendingWebhooks: StateFlow<Int> = AppGraph.webhooks
        .observePending(WebhookQueueEntity.KIND_GEOFENCE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val deviceId: String = store.deviceId()

    val previewParams: List<Pair<String, String>> =
        LocationFields.queryParams(LocationFields.of(ExampleData.geofenceRow))

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    /**
     * Only the switch changes what is registered with Play Services. Alert toggles and webhook
     * edits are read when an event arrives, so they must not trigger a re-registration on
     * every keystroke.
     */
    fun update(transform: (GeofenceSettings) -> GeofenceSettings) {
        val wasEnabled = store.geofence.value.enabled
        store.updateGeofence(transform)
        if (store.geofence.value.enabled != wasEnabled) {
            val turnedOn = store.geofence.value.enabled
            viewModelScope.launch { AppGraph.geofences.sync(initialTrigger = turnedOn) }
        }
    }

    /** Re-registers the whole set with Play Services; every fence edit goes through here. */
    fun resync() {
        viewModelScope.launch { AppGraph.geofences.sync() }
    }

    fun setFenceEnabled(fence: GeofenceEntity, enabled: Boolean) {
        viewModelScope.launch {
            dao.upsert(fence.copy(enabled = enabled))
            AppGraph.geofences.sync()
        }
    }

    fun deleteFence(id: String) {
        viewModelScope.launch {
            dao.delete(id)
            AppGraph.geofences.sync()
        }
    }

    fun sendTestRequest() {
        val hook = settings.value.webhook
        val url = WebhookUrl.compose(hook.baseUrl, previewParams, hook.extraParams, deviceId)
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
