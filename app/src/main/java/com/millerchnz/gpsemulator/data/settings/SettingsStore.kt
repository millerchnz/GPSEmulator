package com.millerchnz.gpsemulator.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * SharedPreferences-backed settings. Services need to read these synchronously on
 * start, so this deliberately stays a blocking store with StateFlows layered on top
 * for the Compose screens.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _logging = MutableStateFlow(readLogging())
    val logging: StateFlow<LoggingSettings> = _logging

    private val _geofence = MutableStateFlow(readGeofence())
    val geofence: StateFlow<GeofenceSettings> = _geofence

    fun readLogging(): LoggingSettings {
        return LoggingSettings(
            enabled = prefs.getBoolean(KEY_LOG_ENABLED, false),
            provider = LocationProviderChoice.fromStoredName(prefs.getString(KEY_LOG_PROVIDER, null)),
            intervalMillis = LogIntervals.coerce(prefs.getLong(KEY_LOG_INTERVAL, LogIntervals.defaultMillis)),
            fusedPriority = FusedPriority.fromStoredName(prefs.getString(KEY_LOG_PRIORITY, null)),
            webhook = readWebhook(PREFIX_LOG),
        )
    }

    fun readGeofence(): GeofenceSettings {
        return GeofenceSettings(
            enabled = prefs.getBoolean(KEY_FENCE_ENABLED, false),
            notifyOnEntry = prefs.getBoolean(KEY_FENCE_NOTIFY_ENTRY, true),
            notifyOnExit = prefs.getBoolean(KEY_FENCE_NOTIFY_EXIT, true),
            notifyOnDwell = prefs.getBoolean(KEY_FENCE_NOTIFY_DWELL, true),
            webhook = readWebhook(PREFIX_FENCE),
        )
    }

    fun updateLogging(transform: (LoggingSettings) -> LoggingSettings) {
        val updated = transform(_logging.value)
        prefs.edit {
            putBoolean(KEY_LOG_ENABLED, updated.enabled)
            putString(KEY_LOG_PROVIDER, updated.provider.storedName)
            putLong(KEY_LOG_INTERVAL, updated.intervalMillis)
            putString(KEY_LOG_PRIORITY, updated.fusedPriority.storedName)
            writeWebhook(PREFIX_LOG, updated.webhook)
        }
        _logging.value = updated
    }

    fun updateGeofence(transform: (GeofenceSettings) -> GeofenceSettings) {
        val updated = transform(_geofence.value)
        prefs.edit {
            putBoolean(KEY_FENCE_ENABLED, updated.enabled)
            putBoolean(KEY_FENCE_NOTIFY_ENTRY, updated.notifyOnEntry)
            putBoolean(KEY_FENCE_NOTIFY_EXIT, updated.notifyOnExit)
            putBoolean(KEY_FENCE_NOTIFY_DWELL, updated.notifyOnDwell)
            writeWebhook(PREFIX_FENCE, updated.webhook)
        }
        _geofence.value = updated
    }

    /** Play Services request ids last known to be registered, used to drop stale fences after a successful add. */
    fun registeredGeofenceIds(): Set<String> {
        return prefs.getStringSet(KEY_REGISTERED_FENCE_IDS, emptySet())?.toSet().orEmpty()
    }

    fun setRegisteredGeofenceIds(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_REGISTERED_FENCE_IDS, ids.toSet()) }
    }

    /**
     * Stable per-install id attached to every webhook as `device=`. Generated once and
     * kept in prefs so logging and geofence requests from this phone stay grouped.
     */
    fun deviceId(): String = synchronized(this) {
        val existing = prefs.getString(KEY_DEVICE_ID, null)?.trim()
        if (!existing.isNullOrEmpty()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit(commit = true) { putString(KEY_DEVICE_ID, generated) }
        generated
    }

    private fun readWebhook(prefix: String): WebhookSettings {
        return WebhookSettings(
            enabled = prefs.getBoolean(prefix + KEY_HOOK_ENABLED, false),
            baseUrl = prefs.getString(prefix + KEY_HOOK_URL, "").orEmpty(),
            extraParams = prefs.getString(prefix + KEY_HOOK_PARAMS, "").orEmpty(),
            headerName = prefs.getString(prefix + KEY_HOOK_HEADER_NAME, "").orEmpty(),
            headerValue = prefs.getString(prefix + KEY_HOOK_HEADER_VALUE, "").orEmpty(),
        )
    }

    private fun SharedPreferences.Editor.writeWebhook(
        prefix: String,
        webhook: WebhookSettings,
    ): SharedPreferences.Editor {
        return putBoolean(prefix + KEY_HOOK_ENABLED, webhook.enabled)
            .putString(prefix + KEY_HOOK_URL, webhook.baseUrl)
            .putString(prefix + KEY_HOOK_PARAMS, webhook.extraParams)
            .putString(prefix + KEY_HOOK_HEADER_NAME, webhook.headerName)
            .putString(prefix + KEY_HOOK_HEADER_VALUE, webhook.headerValue)
    }

    private companion object {
        const val PREFS = "gps_explorer_settings"

        const val KEY_LOG_ENABLED = "logging_enabled"
        const val KEY_LOG_PROVIDER = "logging_provider"
        const val KEY_LOG_INTERVAL = "logging_interval"
        const val KEY_LOG_PRIORITY = "logging_priority"

        const val KEY_FENCE_ENABLED = "geofence_enabled"
        const val KEY_FENCE_NOTIFY_ENTRY = "geofence_notify_entry"
        const val KEY_FENCE_NOTIFY_EXIT = "geofence_notify_exit"
        const val KEY_FENCE_NOTIFY_DWELL = "geofence_notify_dwell"
        const val KEY_REGISTERED_FENCE_IDS = "registered_geofence_ids"
        const val KEY_DEVICE_ID = "device_id"

        const val PREFIX_LOG = "logging_hook_"
        const val PREFIX_FENCE = "geofence_hook_"
        const val KEY_HOOK_ENABLED = "enabled"
        const val KEY_HOOK_URL = "url"
        const val KEY_HOOK_PARAMS = "params"
        const val KEY_HOOK_HEADER_NAME = "header_name"
        const val KEY_HOOK_HEADER_VALUE = "header_value"
    }
}
