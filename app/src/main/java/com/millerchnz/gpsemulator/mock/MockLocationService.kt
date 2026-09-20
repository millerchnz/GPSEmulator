package com.millerchnz.gpsemulator.mock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.millerchnz.gpsemulator.MainActivity
import com.millerchnz.gpsemulator.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class MockLocationService : Service() {
    private val engine by lazy { MockLocationEngine(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private val target = AtomicReference(Pin(DEFAULT_LAT, DEFAULT_LNG, DEFAULT_ACCURACY))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMocking()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                readTarget(intent)
                updateNotification()
            }
            else -> {
                readTarget(intent)
                startMocking()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMocking(stopSelf = false)
        scope.cancel()
        super.onDestroy()
    }

    private fun startMocking() {
        ensureChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            stopMocking()
            return
        }
        val started = engine.start()
        if (started.isFailure) {
            stopMocking()
            return
        }
        ticker?.cancel()
        if (startedAtElapsedRealtime == 0L) {
            startedAtElapsedRealtime = SystemClock.elapsedRealtime()
        }
        ticker = scope.launch {
            while (isActive) {
                val pin = target.get()
                val result = engine.push(pin.latitude, pin.longitude, pin.accuracyMeters)
                if (result.isFailure) {
                    stopMocking()
                    break
                }
                delay(1_000)
            }
        }
        isRunning = true
    }

    private fun stopMocking(stopSelf: Boolean = true) {
        ticker?.cancel()
        ticker = null
        engine.stop()
        isRunning = false
        startedAtElapsedRealtime = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (stopSelf) stopSelf()
    }

    private fun readTarget(intent: Intent?) {
        if (intent == null) return
        if (!intent.hasExtra(EXTRA_LAT) || !intent.hasExtra(EXTRA_LNG)) return
        target.set(
            Pin(
                latitude = intent.getDoubleExtra(EXTRA_LAT, DEFAULT_LAT),
                longitude = intent.getDoubleExtra(EXTRA_LNG, DEFAULT_LNG),
                accuracyMeters = intent.getFloatExtra(EXTRA_ACCURACY, DEFAULT_ACCURACY),
            ),
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mock location",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Shown while GPS Debug Tool is feeding a fake location"
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pin = target.get()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val coords = String.format(
            Locale.US,
            "%.6f, %.6f",
            pin.latitude,
            pin.longitude,
        )
        val startedAtWallClock = if (startedAtElapsedRealtime > 0L) {
            System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedAtElapsedRealtime)
        } else {
            System.currentTimeMillis()
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mocking GPS")
            .setContentText(coords)
            .setWhen(startedAtWallClock)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .build()
    }

    private data class Pin(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
    )

    companion object {
        const val ACTION_START = "com.millerchnz.gpsemulator.START_MOCK"
        const val ACTION_STOP = "com.millerchnz.gpsemulator.STOP_MOCK"
        const val ACTION_UPDATE = "com.millerchnz.gpsemulator.UPDATE_MOCK"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_ACCURACY = "accuracy"
        const val DEFAULT_LAT = -36.8485
        const val DEFAULT_LNG = 174.7633
        const val DEFAULT_ACCURACY = 5f
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 42

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var startedAtElapsedRealtime: Long = 0L
            private set

        fun start(context: Context, latitude: Double, longitude: Double, accuracyMeters: Float) {
            if (startedAtElapsedRealtime == 0L) {
                startedAtElapsedRealtime = SystemClock.elapsedRealtime()
            }
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LAT, latitude)
                putExtra(EXTRA_LNG, longitude)
                putExtra(EXTRA_ACCURACY, accuracyMeters)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun update(context: Context, latitude: Double, longitude: Double, accuracyMeters: Float) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_LAT, latitude)
                putExtra(EXTRA_LNG, longitude)
                putExtra(EXTRA_ACCURACY, accuracyMeters)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        private fun stopIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
