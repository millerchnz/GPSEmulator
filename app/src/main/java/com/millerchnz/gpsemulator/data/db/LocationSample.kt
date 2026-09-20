package com.millerchnz.gpsemulator.data.db

/**
 * Every value the Android [android.location.Location] API can hand back.
 *
 * Each field is nullable because the platform only populates a value when the
 * matching `hasX()` accessor reports true, and because some fields only exist on
 * newer API levels. A null therefore means "the API did not report this", which
 * is information worth keeping in a diagnostic log.
 */
data class LocationSample(
    val provider: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fixTimeMillis: Long? = null,
    val elapsedRealtimeNanos: Long? = null,
    val elapsedRealtimeUncertaintyNanos: Double? = null,
    val fixAgeMillis: Long? = null,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val verticalAccuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val speedAccuracyMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
    val mslAltitudeMeters: Double? = null,
    val mslAltitudeAccuracyMeters: Float? = null,
    val isMock: Boolean? = null,
    val satelliteCount: Int? = null,
    val extras: String? = null,
)
