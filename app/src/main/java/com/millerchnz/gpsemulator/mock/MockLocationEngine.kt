package com.millerchnz.gpsemulator.mock

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import java.util.concurrent.atomic.AtomicBoolean

class MockLocationEngine(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private val providersInstalled = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun start(): Result<Unit> {
        return runCatching {
            installProviders()
            if (playServicesAvailable()) {
                fused.setMockMode(true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun push(latitude: Double, longitude: Double, accuracyMeters: Float): Result<Unit> {
        return runCatching {
            if (!providersInstalled.get()) {
                installProviders()
            }
            val location = buildLocation(latitude, longitude, accuracyMeters)
            PROVIDERS.forEach { name ->
                val named = Location(location).apply { provider = name }
                try {
                    locationManager.setTestProviderEnabled(name, true)
                    locationManager.setTestProviderLocation(name, named)
                } catch (_: IllegalArgumentException) {
                    addProvider(name)
                    locationManager.setTestProviderEnabled(name, true)
                    locationManager.setTestProviderLocation(name, named)
                }
            }
            if (playServicesAvailable()) {
                fused.setMockMode(true)
                fused.setMockLocation(Location(location).apply { provider = "fused" })
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (playServicesAvailable()) {
            runCatching { fused.setMockMode(false) }
        }
        PROVIDERS.forEach { name ->
            runCatching { locationManager.setTestProviderEnabled(name, false) }
            runCatching { locationManager.removeTestProvider(name) }
        }
        providersInstalled.set(false)
    }

    private fun installProviders() {
        PROVIDERS.forEach(::addProvider)
        providersInstalled.set(true)
    }

    @Suppress("DEPRECATION")
    private fun addProvider(name: String) {
        runCatching { locationManager.removeTestProvider(name) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager.addTestProvider(
                name,
                ProviderProperties.Builder()
                    .setHasNetworkRequirement(false)
                    .setHasSatelliteRequirement(name == LocationManager.GPS_PROVIDER)
                    .setHasCellRequirement(false)
                    .setHasMonetaryCost(false)
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build(),
            )
        } else {
            locationManager.addTestProvider(
                name,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE,
            )
        }
        locationManager.setTestProviderEnabled(name, true)
    }

    private fun buildLocation(latitude: Double, longitude: Double, accuracyMeters: Float): Location {
        return Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            altitude = 0.0
            bearing = 0f
            speed = 0f
            accuracy = accuracyMeters.coerceAtLeast(1f)
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 0.1f
                speedAccuracyMetersPerSecond = 0.1f
                verticalAccuracyMeters = accuracyMeters.coerceAtLeast(1f)
            }
        }
    }

    private fun playServicesAvailable(): Boolean {
        return GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
    }

    companion object {
        private val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            "fused",
        )
    }
}
