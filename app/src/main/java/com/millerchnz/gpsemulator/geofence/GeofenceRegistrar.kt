package com.millerchnz.gpsemulator.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.millerchnz.gpsemulator.data.db.GeofenceDao
import com.millerchnz.gpsemulator.data.db.GeofenceEntity
import com.millerchnz.gpsemulator.data.settings.SettingsStore
import com.millerchnz.gpsemulator.gms.awaitCompletion
import com.millerchnz.gpsemulator.setup.SetupRequirements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface GeofenceSyncResult {
    /** Geofencing is switched off, so nothing is registered with Play Services. */
    data object Disabled : GeofenceSyncResult

    data object NoFences : GeofenceSyncResult

    data class Registered(val count: Int) : GeofenceSyncResult

    data class MissingPermission(val message: String) : GeofenceSyncResult

    data class Failed(val message: String) : GeofenceSyncResult
}

/** Last known registration outcome, so the settings screen can explain what Play Services did. */
object GeofenceStatus {
    private val _state = MutableStateFlow<GeofenceSyncResult>(GeofenceSyncResult.Disabled)
    val state: StateFlow<GeofenceSyncResult> = _state

    internal fun update(result: GeofenceSyncResult) {
        _state.value = result
    }
}

/**
 * Owns the Play Services geofence registration. Every change to the fence list or to the
 * geofencing switch goes through [sync].
 *
 * Adds the current set *before* removing stale ids, so a rejected add leaves the previous
 * registration in place. [initialTrigger] is only set when the user just turned monitoring
 * on — process start and fence edits use [GeofencingRequest.INITIAL_TRIGGER_NONE] so they
 * do not fabricate ENTER/DWELL rows.
 */
class GeofenceRegistrar(
    private val context: Context,
    private val settings: SettingsStore,
    private val dao: GeofenceDao,
) {
    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }
    private val lock = Mutex()

    suspend fun sync(initialTrigger: Boolean = false): GeofenceSyncResult {
        return lock.withLock {
            val result = register(initialTrigger)
            GeofenceStatus.update(result)
            result
        }
    }

    private suspend fun register(initialTrigger: Boolean): GeofenceSyncResult {
        if (!settings.geofence.value.enabled) {
            removeAll()
            settings.setRegisteredGeofenceIds(emptySet())
            return GeofenceSyncResult.Disabled
        }
        if (!SetupRequirements.hasFineLocation(context)) {
            return GeofenceSyncResult.MissingPermission("Precise location permission is required")
        }
        if (!SetupRequirements.hasBackgroundLocation(context)) {
            return GeofenceSyncResult.MissingPermission(
                "Background location (\"Allow all the time\") is required before Play Services will register a geofence",
            )
        }
        val fences = dao.all().filter { it.enabled && it.hasAnyTransition }
        if (fences.isEmpty()) {
            removeAll()
            settings.setRegisteredGeofenceIds(emptySet())
            return GeofenceSyncResult.NoFences
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                if (initialTrigger) {
                    GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL
                } else {
                    // 21.4.0 has no INITIAL_TRIGGER_NONE constant; 0 means do not alert on add.
                    0
                },
            )
            .addGeofences(fences.map { it.toPlayServicesGeofence() })
            .build()
        val added = runCatching { addGeofences(request) }
        if (added.isFailure) {
            return GeofenceSyncResult.Failed(describe(added.exceptionOrNull()!!))
        }

        val newIds = fences.map { it.id }.toSet()
        val stale = settings.registeredGeofenceIds() - newIds
        removeIds(stale)
        settings.setRegisteredGeofenceIds(newIds)
        return GeofenceSyncResult.Registered(fences.size)
    }

    @SuppressLint("MissingPermission")
    private suspend fun addGeofences(request: GeofencingRequest) {
        client.addGeofences(request, pendingIntent(context)).awaitCompletion()
    }

    private suspend fun removeIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        runCatching { client.removeGeofences(ids.toList()).awaitCompletion() }
    }

    suspend fun removeAll() {
        runCatching { client.removeGeofences(pendingIntent(context)).awaitCompletion() }
    }

    private fun describe(error: Throwable): String {
        return when (error) {
            is ApiException -> GeofenceStatusCodes.getStatusCodeString(error.statusCode)
            else -> error.message ?: error::class.java.simpleName
        }
    }

    private fun GeofenceEntity.toPlayServicesGeofence(): Geofence {
        var transitions = 0
        if (watchEnter) transitions = transitions or Geofence.GEOFENCE_TRANSITION_ENTER
        if (watchExit) transitions = transitions or Geofence.GEOFENCE_TRANSITION_EXIT
        if (watchDwell) transitions = transitions or Geofence.GEOFENCE_TRANSITION_DWELL
        return Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(latitude, longitude, GeofenceEntity.clampRadius(radiusMeters))
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions)
            .setNotificationResponsiveness(responsivenessMillis)
            .apply { if (watchDwell) setLoiteringDelay(loiteringDelayMillis) }
            .build()
    }

    companion object {
        private const val ACTION_GEOFENCE_EVENT = "com.millerchnz.gpsemulator.GEOFENCE_EVENT"

        /**
         * Geofencing hands this intent back with the transition extras filled in, so it has
         * to be mutable on API 31+.
         */
        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
                .setAction(ACTION_GEOFENCE_EVENT)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }
    }
}
