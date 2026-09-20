package com.millerchnz.gpsemulator.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.data.Formats
import com.millerchnz.gpsemulator.data.LocationFields
import com.millerchnz.gpsemulator.data.db.GeofenceEntity
import com.millerchnz.gpsemulator.data.db.GeofenceEventEntity
import com.millerchnz.gpsemulator.data.db.GeofenceTransition
import com.millerchnz.gpsemulator.data.db.LocationSample
import com.millerchnz.gpsemulator.data.db.WebhookQueueEntity
import com.millerchnz.gpsemulator.data.net.WebhookUrl
import com.millerchnz.gpsemulator.data.settings.GeofenceSettings
import com.millerchnz.gpsemulator.data.settings.Retention
import com.millerchnz.gpsemulator.location.toSample
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Receives the transitions Play Services reports for the registered geofences, writes one
 * row per triggering fence, raises the alert and fires the optional webhook.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val app = context.applicationContext
        AppGraph.init(app)
        val pendingResult = goAsync()
        AppGraph.scope.launch {
            try {
                handle(app, event)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context, event: GeofencingEvent) {
        val settings = AppGraph.settings.geofence.value
        val now = System.currentTimeMillis()

        if (event.hasError()) {
            store(
                context,
                settings,
                GeofenceEventEntity(
                    recordedAtMillis = now,
                    fenceId = null,
                    fenceName = null,
                    transition = GeofenceTransition.ERROR.storedName,
                    triggeringFenceIds = "",
                    fenceLatitude = null,
                    fenceLongitude = null,
                    fenceRadiusMeters = null,
                    distanceFromCenterMeters = null,
                    errorCode = event.errorCode,
                    errorMessage = GeofenceStatusCodes.getStatusCodeString(event.errorCode),
                    hasTriggeringLocation = false,
                    sample = LocationSample(),
                ),
                alert = false,
            )
            return
        }

        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceTransition.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceTransition.EXIT
            Geofence.GEOFENCE_TRANSITION_DWELL -> GeofenceTransition.DWELL
            else -> GeofenceTransition.ERROR
        }
        val location = event.triggeringLocation
        val sample = location?.toSample() ?: LocationSample()
        val triggering = event.triggeringGeofences.orEmpty()
        val allIds = triggering.joinToString(",") { it.requestId }

        if (triggering.isEmpty()) {
            store(
                context,
                settings,
                GeofenceEventEntity(
                    recordedAtMillis = now,
                    fenceId = null,
                    fenceName = null,
                    transition = transition.storedName,
                    triggeringFenceIds = "",
                    fenceLatitude = null,
                    fenceLongitude = null,
                    fenceRadiusMeters = null,
                    distanceFromCenterMeters = null,
                    errorCode = null,
                    errorMessage = null,
                    hasTriggeringLocation = location != null,
                    sample = sample,
                ),
                alert = settings.allows(transition),
            )
            return
        }

        for (triggered in triggering) {
            val fence = AppGraph.database.geofences().byId(triggered.requestId)
            store(
                context,
                settings,
                GeofenceEventEntity(
                    recordedAtMillis = now,
                    fenceId = triggered.requestId,
                    fenceName = fence?.name,
                    transition = transition.storedName,
                    triggeringFenceIds = allIds,
                    fenceLatitude = fence?.latitude,
                    fenceLongitude = fence?.longitude,
                    fenceRadiusMeters = fence?.radiusMeters,
                    distanceFromCenterMeters = distanceFromCenter(location, fence),
                    errorCode = null,
                    errorMessage = null,
                    hasTriggeringLocation = location != null,
                    sample = sample,
                ),
                alert = settings.allows(transition),
            )
        }
    }

    private suspend fun store(
        context: Context,
        settings: GeofenceSettings,
        row: GeofenceEventEntity,
        alert: Boolean,
    ) {
        val dao = AppGraph.database.geofenceEvents()
        dao.insert(row)
        if (inserts.incrementAndGet() % Retention.TRIM_EVERY == 0) {
            dao.trimTo(Retention.MAX_ROWS)
        }
        if (alert) {
            GeofenceAlerts.alert(
                context = context,
                transition = GeofenceTransition.fromStoredName(row.transition),
                fenceName = row.fenceName ?: row.fenceId ?: "geofence",
                detail = alertDetail(row),
            )
        }
        val hook = settings.webhook
        if (hook.enabled) {
            val url = WebhookUrl.compose(
                baseUrl = hook.baseUrl,
                params = LocationFields.queryParams(LocationFields.of(row)),
                extraParams = hook.extraParams,
                deviceId = AppGraph.settings.deviceId(),
            ) ?: return
            AppGraph.webhooks.send(WebhookQueueEntity.KIND_GEOFENCE, url, hook)
        }
    }

    private fun alertDetail(row: GeofenceEventEntity): String {
        val latitude = row.sample.latitude
        val longitude = row.sample.longitude
        val position = if (latitude != null && longitude != null) {
            Formats.coordinates(latitude, longitude)
        } else {
            "no location reported"
        }
        val accuracy = row.sample.accuracyMeters?.let { " · ±${Formats.decimals(it, 0)} m" }.orEmpty()
        val distance = row.distanceFromCenterMeters?.let { " · ${Formats.distance(it)} from centre" }.orEmpty()
        return position + accuracy + distance
    }

    private fun distanceFromCenter(location: Location?, fence: GeofenceEntity?): Float? {
        if (location == null || fence == null) return null
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            fence.latitude,
            fence.longitude,
            results,
        )
        return results[0]
    }

    private fun GeofenceSettings.allows(transition: GeofenceTransition): Boolean = when (transition) {
        GeofenceTransition.ENTER -> notifyOnEntry
        GeofenceTransition.EXIT -> notifyOnExit
        GeofenceTransition.DWELL -> notifyOnDwell
        GeofenceTransition.ERROR -> false
    }

    private companion object {
        val inserts = AtomicInteger(0)
    }
}
