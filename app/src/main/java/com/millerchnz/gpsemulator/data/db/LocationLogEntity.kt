package com.millerchnz.gpsemulator.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_logs",
    indices = [Index("recordedAtMillis")],
)
data class LocationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall clock time this row was written, as opposed to the fix time. */
    val recordedAtMillis: Long,
    /** The provider the user asked us to listen to, which may differ from the one that answered. */
    val requestedProvider: String,
    @Embedded val sample: LocationSample,
)
