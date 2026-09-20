package com.millerchnz.gpsemulator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geofences")
data class GeofenceEntity(
    /** Also used verbatim as the Play Services geofence request id. */
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val watchEnter: Boolean = true,
    val watchExit: Boolean = true,
    val watchDwell: Boolean = false,
    val loiteringDelayMillis: Int = DEFAULT_LOITERING_DELAY_MS,
    val responsivenessMillis: Int = 0,
    val enabled: Boolean = true,
    val createdAtMillis: Long,
) {
    val hasAnyTransition: Boolean
        get() = watchEnter || watchExit || watchDwell

    companion object {
        const val DEFAULT_RADIUS_METERS = 50f
        const val MIN_RADIUS_METERS = 1f
        const val MAX_RADIUS_METERS = 250f
        const val DEFAULT_LOITERING_DELAY_MS = 60_000

        fun clampRadius(meters: Float): Float = meters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
    }
}
