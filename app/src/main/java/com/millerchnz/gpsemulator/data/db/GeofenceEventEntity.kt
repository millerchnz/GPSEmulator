package com.millerchnz.gpsemulator.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "geofence_events",
    indices = [Index("recordedAtMillis")],
)
data class GeofenceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAtMillis: Long,
    val fenceId: String?,
    val fenceName: String?,
    /** One of [GeofenceTransition]'s stored names, or ERROR when the event carried a failure. */
    val transition: String,
    /** Play Services can report several fences in one event; all ids are kept here. */
    val triggeringFenceIds: String,
    val fenceLatitude: Double?,
    val fenceLongitude: Double?,
    val fenceRadiusMeters: Float?,
    val distanceFromCenterMeters: Float?,
    val errorCode: Int?,
    val errorMessage: String?,
    val hasTriggeringLocation: Boolean,
    @Embedded val sample: LocationSample,
)

enum class GeofenceTransition(val storedName: String, val label: String) {
    ENTER("ENTER", "Entered"),
    EXIT("EXIT", "Exited"),
    DWELL("DWELL", "Dwelling"),
    ERROR("ERROR", "Error"),
    ;

    companion object {
        fun fromStoredName(value: String?): GeofenceTransition {
            return entries.firstOrNull { it.storedName == value } ?: ERROR
        }
    }
}
