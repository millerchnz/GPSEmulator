package com.millerchnz.gpsemulator.location

import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.millerchnz.gpsemulator.data.db.LocationSample

/**
 * Copies everything the platform reported about a fix.
 *
 * Fields guarded by a `hasX()` accessor stay null when the provider did not report
 * them, and fields added after API 26 stay null on older devices, so the log always
 * distinguishes "zero" from "not reported".
 */
fun Location.toSample(): LocationSample {
    return LocationSample(
        provider = provider,
        latitude = latitude,
        longitude = longitude,
        fixTimeMillis = time,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        elapsedRealtimeUncertaintyNanos =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasElapsedRealtimeUncertaintyNanos()) {
                elapsedRealtimeUncertaintyNanos
            } else {
                null
            },
        fixAgeMillis = ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L)
            .coerceAtLeast(0L),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        altitudeMeters = if (hasAltitude()) altitude else null,
        verticalAccuracyMeters = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        bearingAccuracyDegrees = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
        mslAltitudeMeters =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude()) {
                mslAltitudeMeters
            } else {
                null
            },
        mslAltitudeAccuracyMeters =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitudeAccuracy()) {
                mslAltitudeAccuracyMeters
            } else {
                null
            },
        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        },
        satelliteCount = extras?.let { bundle ->
            runCatching { if (bundle.containsKey(EXTRA_SATELLITES)) bundle.getInt(EXTRA_SATELLITES) else null }
                .getOrNull()
        },
        extras = renderExtras(extras),
    )
}

private const val EXTRA_SATELLITES = "satellites"

private fun renderExtras(bundle: Bundle?): String? {
    if (bundle == null) return null
    return runCatching {
        if (bundle.isEmpty) {
            null
        } else {
            bundle.keySet()
                .sorted()
                .joinToString(", ") { key ->
                    @Suppress("DEPRECATION")
                    val value = bundle.get(key)
                    "$key=$value"
                }
                .ifBlank { null }
        }
    }.getOrNull()
}
