package com.millerchnz.gpsemulator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A composed GET that could not be delivered. The dispatcher drains this table
 * when connectivity returns.
 */
@Entity(tableName = "webhook_queue")
data class WebhookQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "location" or "geofence", so the settings pages can show their own pending count. */
    val kind: String,
    val url: String,
    val headerName: String?,
    val headerValue: String?,
    val createdAtMillis: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
) {
    companion object {
        const val KIND_LOCATION = "location"
        const val KIND_GEOFENCE = "geofence"
        const val MAX_ATTEMPTS = 25
        const val MAX_QUEUE_SIZE = 5_000
    }
}
