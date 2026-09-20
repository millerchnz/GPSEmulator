package com.millerchnz.gpsemulator.logging

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.millerchnz.gpsemulator.MainActivity
import com.millerchnz.gpsemulator.R
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.data.Formats
import com.millerchnz.gpsemulator.data.LocationFields
import com.millerchnz.gpsemulator.data.db.LocationLogEntity
import com.millerchnz.gpsemulator.data.db.WebhookQueueEntity
import com.millerchnz.gpsemulator.data.net.WebhookUrl
import com.millerchnz.gpsemulator.data.settings.LocationProviderChoice
import com.millerchnz.gpsemulator.data.settings.LogIntervals
import com.millerchnz.gpsemulator.data.settings.LoggingSettings
import com.millerchnz.gpsemulator.data.settings.Retention
import com.millerchnz.gpsemulator.gms.awaitCompletion
import com.millerchnz.gpsemulator.location.toSample
import com.millerchnz.gpsemulator.setup.SetupRequirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Records every fix the selected provider reports for as long as logging is enabled.
 *
 * It runs as a `location` foreground service so a thirty minute interval still fires when
 * the app is not on screen, and it follows the settings flow: changing provider, interval
 * or priority re-subscribes, and switching logging off stops the service.
 */
class LocationLoggingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var active: LoggingSettings? = null
    private var fusedCallback: LocationCallback? = null
    private var managerListener: LocationListener? = null
    private var observing = false
    private var lastNotificationAtMillis = 0L
    private val inserts = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppGraph.init(this)
        if (intent?.action == ACTION_STOP) {
            AppGraph.settings.updateLogging { it.copy(enabled = false) }
            stopEverything()
            return START_NOT_STICKY
        }
        if (!goForeground()) return START_NOT_STICKY
        if (!SetupRequirements.hasFineLocation(this)) {
            LoggingStatus.onError("Precise location permission is required")
            stopEverything()
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            isRunning = true
            LoggingStatus.onStarted(SystemClock.elapsedRealtime())
            scope.launch {
                AppGraph.settings.logging.collect { settings ->
                    if (!settings.enabled) {
                        stopEverything()
                        return@collect
                    }
                    applySettings(settings)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopUpdates()
        isRunning = false
        LoggingStatus.onStopped()
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(): Boolean {
        ensureChannel()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            true
        } catch (error: Exception) {
            LoggingStatus.onError(error.message ?: "Could not start the logging service")
            stopSelf()
            false
        }
    }

    private suspend fun applySettings(settings: LoggingSettings) {
        val previous = active
        val resubscribe = previous == null ||
            previous.provider != settings.provider ||
            previous.intervalMillis != settings.intervalMillis ||
            previous.fusedPriority != settings.fusedPriority
        active = settings
        if (resubscribe) {
            stopUpdates()
            startUpdates(settings)
        }
        updateNotification(force = true)
    }

    @SuppressLint("MissingPermission")
    private suspend fun startUpdates(settings: LoggingSettings) {
        val outcome = runCatching {
            when (settings.provider) {
                LocationProviderChoice.FUSED -> {
                    val request = LocationRequest.Builder(
                        settings.fusedPriority.priority,
                        settings.intervalMillis,
                    )
                        .setMinUpdateIntervalMillis(settings.intervalMillis)
                        .setWaitForAccurateLocation(false)
                        .build()
                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.locations.forEach(::record)
                        }
                    }
                    fusedCallback = callback
                    fused.requestLocationUpdates(request, callback, Looper.getMainLooper()).awaitCompletion()
                }

                LocationProviderChoice.GPS, LocationProviderChoice.NETWORK -> {
                    val provider = if (settings.provider == LocationProviderChoice.GPS) {
                        LocationManager.GPS_PROVIDER
                    } else {
                        LocationManager.NETWORK_PROVIDER
                    }
                    val listener = PlatformLocationListener(::record)
                    locationManager.requestLocationUpdates(
                        provider,
                        settings.intervalMillis,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                    managerListener = listener
                }
            }
        }
        outcome.onFailure { error ->
            stopUpdates()
            LoggingStatus.onError(error.message ?: "Could not subscribe to ${settings.provider.label}")
        }
    }

    private fun stopUpdates() {
        fusedCallback?.let { runCatching { fused.removeLocationUpdates(it) } }
        fusedCallback = null
        managerListener?.let { runCatching { locationManager.removeUpdates(it) } }
        managerListener = null
    }

    private fun stopEverything() {
        stopUpdates()
        observing = false
        isRunning = false
        LoggingStatus.onStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun record(location: Location) {
        val requested = active?.provider ?: return
        val recordedAt = System.currentTimeMillis()
        val sample = location.toSample()
        LoggingStatus.onFix(sample, recordedAt)
        updateNotification()
        scope.launch(Dispatchers.IO) {
            val row = LocationLogEntity(
                recordedAtMillis = recordedAt,
                requestedProvider = requested.storedName,
                sample = sample,
            )
            val dao = AppGraph.database.locationLogs()
            dao.insert(row)
            if (inserts.incrementAndGet() % Retention.TRIM_EVERY == 0) {
                dao.trimTo(Retention.MAX_ROWS)
            }
            val hook = AppGraph.settings.logging.value.webhook
            if (hook.enabled) {
                val url = WebhookUrl.compose(
                    baseUrl = hook.baseUrl,
                    params = LocationFields.queryParams(LocationFields.of(row)),
                    extraParams = hook.extraParams,
                    deviceId = AppGraph.settings.deviceId(),
                ) ?: return@launch
                AppGraph.webhooks.send(WebhookQueueEntity.KIND_LOCATION, url, hook)
            }
        }
    }

    private fun updateNotification(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotificationAtMillis < NOTIFICATION_MIN_INTERVAL_MS) return
        lastNotificationAtMillis = now
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Location logging",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shown while GPS Debug Tool is recording location fixes" },
        )
    }

    private fun buildNotification(): Notification {
        val status = LoggingStatus.state.value
        val settings = active ?: AppGraph.settings.logging.value
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, LocationLoggingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val latitude = status.lastSample?.latitude
        val longitude = status.lastSample?.longitude
        val detail = if (latitude != null && longitude != null) {
            Formats.coordinates(latitude, longitude)
        } else {
            "Waiting for the first fix"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                "Logging ${settings.provider.label} · ${LogIntervals.label(settings.intervalMillis).lowercase()}",
            )
            .setContentText(detail)
            .setSubText("${Formats.count(status.sessionCount)} logged")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .build()
    }

    /**
     * Implements every [LocationListener] method explicitly. The extra methods only became
     * default interface methods in API 30, so a SAM-converted lambda would throw
     * AbstractMethodError when the platform calls them on API 26 to 29.
     */
    private class PlatformLocationListener(
        private val onFix: (Location) -> Unit,
    ) : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)

        @Deprecated("Retained so older platforms have a concrete implementation to call")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    companion object {
        const val ACTION_STOP = "com.millerchnz.gpsemulator.STOP_LOGGING"
        private const val CHANNEL_ID = "location_logging"
        private const val NOTIFICATION_ID = 43
        private const val NOTIFICATION_MIN_INTERVAL_MS = 1_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        /** Brings the service in line with the stored setting. Safe to call on every settings change. */
        fun sync(context: Context) {
            if (AppGraph.settings.logging.value.enabled) start(context) else stop(context)
        }

        private fun start(context: Context) {
            val intent = Intent(context, LocationLoggingService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { LoggingStatus.onError(it.message ?: "Could not start the logging service") }
        }

        private fun stop(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, LocationLoggingService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
