package com.millerchnz.gpsemulator.data

import com.millerchnz.gpsemulator.data.db.GeofenceEventEntity
import com.millerchnz.gpsemulator.data.db.GeofenceTransition
import com.millerchnz.gpsemulator.data.db.LocationLogEntity
import com.millerchnz.gpsemulator.data.db.LocationSample

/**
 * Stand-in values so the webhook URL preview shows a realistic request before any
 * data has been collected.
 */
object ExampleData {
    private val sample = LocationSample(
        provider = "fused",
        latitude = -36.8485,
        longitude = 174.7633,
        fixTimeMillis = 1_700_000_000_000L,
        elapsedRealtimeNanos = 123_456_789_000L,
        fixAgeMillis = 42L,
        accuracyMeters = 4.8f,
        altitudeMeters = 21.3,
        verticalAccuracyMeters = 1.6f,
        speedMetersPerSecond = 1.4f,
        speedAccuracyMetersPerSecond = 0.3f,
        bearingDegrees = 271.5f,
        bearingAccuracyDegrees = 12.0f,
        isMock = false,
        satelliteCount = 14,
    )

    val locationRow = LocationLogEntity(
        recordedAtMillis = 1_700_000_000_000L,
        requestedProvider = "FUSED",
        sample = sample,
    )

    val geofenceRow = GeofenceEventEntity(
        recordedAtMillis = 1_700_000_000_000L,
        fenceId = "8f2c1b90",
        fenceName = "Home",
        transition = GeofenceTransition.ENTER.storedName,
        triggeringFenceIds = "8f2c1b90",
        fenceLatitude = -36.8485,
        fenceLongitude = 174.7633,
        fenceRadiusMeters = 150f,
        distanceFromCenterMeters = 96.2f,
        errorCode = null,
        errorMessage = null,
        hasTriggeringLocation = true,
        sample = sample,
    )
}
