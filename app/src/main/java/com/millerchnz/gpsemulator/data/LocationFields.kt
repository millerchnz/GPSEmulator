package com.millerchnz.gpsemulator.data

import com.millerchnz.gpsemulator.data.db.GeofenceEventEntity
import com.millerchnz.gpsemulator.data.db.LocationLogEntity
import com.millerchnz.gpsemulator.data.db.LocationSample

/**
 * One reported value. [key] is the query parameter / CSV column / JSON key, [raw] is
 * the machine-readable value, and [unit] is display-only.
 *
 * A null [raw] means the platform did not report the value for this fix.
 */
data class SampleField(
    val key: String,
    val label: String,
    val raw: String?,
    val unit: String? = null,
) {
    val display: String
        get() = when {
            raw == null -> "—"
            unit == null -> raw
            else -> "$raw $unit"
        }
}

/**
 * The single source of truth for how a fix is described. The log detail UI, CSV and
 * JSON exports, and the webhook query string all read from here, so a new field only
 * has to be added once.
 */
object LocationFields {
    fun of(sample: LocationSample): List<SampleField> = listOf(
        SampleField("provider", "Reporting provider", sample.provider),
        SampleField("lat", "Latitude", sample.latitude?.let { Formats.decimals(it, 7) }, "°"),
        SampleField("lon", "Longitude", sample.longitude?.let { Formats.decimals(it, 7) }, "°"),
        SampleField("accuracy", "Horizontal accuracy", sample.accuracyMeters?.let { Formats.decimals(it, 2) }, "m"),
        SampleField("altitude", "Altitude (WGS84)", sample.altitudeMeters?.let { Formats.decimals(it, 2) }, "m"),
        SampleField(
            "vertical_accuracy",
            "Vertical accuracy",
            sample.verticalAccuracyMeters?.let { Formats.decimals(it, 2) },
            "m",
        ),
        SampleField("msl_altitude", "Altitude above MSL", sample.mslAltitudeMeters?.let { Formats.decimals(it, 2) }, "m"),
        SampleField(
            "msl_altitude_accuracy",
            "MSL altitude accuracy",
            sample.mslAltitudeAccuracyMeters?.let { Formats.decimals(it, 2) },
            "m",
        ),
        SampleField("speed", "Speed", sample.speedMetersPerSecond?.let { Formats.decimals(it, 3) }, "m/s"),
        SampleField(
            "speed_accuracy",
            "Speed accuracy",
            sample.speedAccuracyMetersPerSecond?.let { Formats.decimals(it, 3) },
            "m/s",
        ),
        SampleField("bearing", "Bearing", sample.bearingDegrees?.let { Formats.decimals(it, 2) }, "°"),
        SampleField(
            "bearing_accuracy",
            "Bearing accuracy",
            sample.bearingAccuracyDegrees?.let { Formats.decimals(it, 2) },
            "°",
        ),
        SampleField("fix_time", "Fix time (UTC)", sample.fixTimeMillis?.let { Formats.iso(it) }),
        SampleField("fix_time_ms", "Fix time (epoch ms)", sample.fixTimeMillis?.toString()),
        SampleField("fix_age_ms", "Fix age", sample.fixAgeMillis?.toString(), "ms"),
        SampleField("elapsed_realtime_nanos", "Elapsed realtime", sample.elapsedRealtimeNanos?.toString(), "ns"),
        SampleField(
            "elapsed_realtime_uncertainty_nanos",
            "Elapsed realtime uncertainty",
            sample.elapsedRealtimeUncertaintyNanos?.let { Formats.decimals(it, 0) },
            "ns",
        ),
        SampleField("is_mock", "Mocked fix", sample.isMock?.toString()),
        SampleField("satellites", "Satellites in fix", sample.satelliteCount?.toString()),
        SampleField("extras", "Provider extras", sample.extras),
    )

    fun of(row: LocationLogEntity): List<SampleField> = buildList {
        add(SampleField("recorded_at", "Logged at (UTC)", Formats.iso(row.recordedAtMillis)))
        add(SampleField("recorded_at_ms", "Logged at (epoch ms)", row.recordedAtMillis.toString()))
        add(SampleField("requested_provider", "Requested provider", row.requestedProvider))
        addAll(of(row.sample))
    }

    fun of(row: GeofenceEventEntity): List<SampleField> = buildList {
        add(SampleField("recorded_at", "Logged at (UTC)", Formats.iso(row.recordedAtMillis)))
        add(SampleField("recorded_at_ms", "Logged at (epoch ms)", row.recordedAtMillis.toString()))
        add(SampleField("transition", "Transition", row.transition))
        add(SampleField("fence_id", "Fence id", row.fenceId))
        add(SampleField("fence_name", "Fence name", row.fenceName))
        add(SampleField("fence_lat", "Fence latitude", row.fenceLatitude?.let { Formats.decimals(it, 7) }, "°"))
        add(SampleField("fence_lon", "Fence longitude", row.fenceLongitude?.let { Formats.decimals(it, 7) }, "°"))
        add(SampleField("fence_radius", "Fence radius", row.fenceRadiusMeters?.let { Formats.decimals(it, 1) }, "m"))
        add(
            SampleField(
                "distance_from_center",
                "Distance from centre",
                row.distanceFromCenterMeters?.let { Formats.decimals(it, 1) },
                "m",
            ),
        )
        add(SampleField("triggering_fence_ids", "All triggering fences", row.triggeringFenceIds.ifBlank { null }))
        add(SampleField("error_code", "Error code", row.errorCode?.toString()))
        add(SampleField("error", "Error", row.errorMessage))
        add(SampleField("has_triggering_location", "Carried a location", row.hasTriggeringLocation.toString()))
        addAll(of(row.sample))
    }

    /** Query parameters for a webhook: reported values only, so an absent field is visibly absent. */
    fun queryParams(fields: List<SampleField>): List<Pair<String, String>> =
        fields.mapNotNull { field -> field.raw?.let { field.key to it } }
}
