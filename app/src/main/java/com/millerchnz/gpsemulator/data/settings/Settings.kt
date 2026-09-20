package com.millerchnz.gpsemulator.data.settings

import com.google.android.gms.location.Priority
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class LocationProviderChoice(
    val storedName: String,
    val label: String,
    val description: String,
) {
    FUSED("FUSED", "Fused", "Play Services FusedLocationProviderClient"),
    GPS("GPS", "GPS", "LocationManager GPS_PROVIDER"),
    NETWORK("NETWORK", "Network", "LocationManager NETWORK_PROVIDER"),
    ;

    companion object {
        fun fromStoredName(value: String?): LocationProviderChoice {
            return entries.firstOrNull { it.storedName == value } ?: FUSED
        }
    }
}

enum class FusedPriority(
    val storedName: String,
    val label: String,
    val priority: Int,
) {
    HIGH_ACCURACY("HIGH_ACCURACY", "High accuracy", Priority.PRIORITY_HIGH_ACCURACY),
    BALANCED("BALANCED", "Balanced power", Priority.PRIORITY_BALANCED_POWER_ACCURACY),
    LOW_POWER("LOW_POWER", "Low power", Priority.PRIORITY_LOW_POWER),
    PASSIVE("PASSIVE", "Passive", Priority.PRIORITY_PASSIVE),
    ;

    companion object {
        fun fromStoredName(value: String?): FusedPriority {
            return entries.firstOrNull { it.storedName == value } ?: HIGH_ACCURACY
        }
    }
}

/** The selectable logging intervals, from once a second to once every thirty minutes. */
object LogIntervals {
    val optionsMillis: List<Long> = listOf(
        1_000L,
        2_000L,
        5_000L,
        10_000L,
        15_000L,
        30_000L,
        TimeUnit.MINUTES.toMillis(1),
        TimeUnit.MINUTES.toMillis(2),
        TimeUnit.MINUTES.toMillis(5),
        TimeUnit.MINUTES.toMillis(10),
        TimeUnit.MINUTES.toMillis(15),
        TimeUnit.MINUTES.toMillis(30),
    )

    val defaultMillis: Long = 5_000L

    fun coerce(millis: Long): Long = optionsMillis.minByOrNull { kotlin.math.abs(it - millis) } ?: defaultMillis

    fun indexOf(millis: Long): Int = optionsMillis.indexOf(coerce(millis)).coerceAtLeast(0)

    fun label(millis: Long): String {
        val seconds = millis / 1_000L
        return when {
            seconds < 60 -> if (seconds == 1L) "Every second" else "Every $seconds seconds"
            seconds == 60L -> "Every minute"
            else -> String.format(Locale.US, "Every %d minutes", seconds / 60)
        }
    }
}

data class WebhookSettings(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    /** Free text, `key=value` pairs separated by `&` or newlines, appended to every request. */
    val extraParams: String = "",
    val headerName: String = "",
    val headerValue: String = "",
)

data class LoggingSettings(
    val enabled: Boolean = false,
    val provider: LocationProviderChoice = LocationProviderChoice.FUSED,
    val intervalMillis: Long = LogIntervals.defaultMillis,
    val fusedPriority: FusedPriority = FusedPriority.HIGH_ACCURACY,
    val webhook: WebhookSettings = WebhookSettings(),
)

data class GeofenceSettings(
    val enabled: Boolean = false,
    val notifyOnEntry: Boolean = true,
    val notifyOnExit: Boolean = true,
    val notifyOnDwell: Boolean = true,
    val webhook: WebhookSettings = WebhookSettings(),
)

/** Newest-row cap applied to both log tables. */
object Retention {
    const val MAX_ROWS = 100_000

    /** Rows written between retention sweeps; the trim query is a full scan, so don't run it every insert. */
    const val TRIM_EVERY = 250
}
