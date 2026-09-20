package com.millerchnz.gpsemulator.data

import com.millerchnz.gpsemulator.data.db.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFieldsTest {
    @Test
    fun keysAreUniqueSoCsvAndQueryStringsStayValid() {
        val locationKeys = LocationFields.of(ExampleData.locationRow).map { it.key }
        val geofenceKeys = LocationFields.of(ExampleData.geofenceRow).map { it.key }
        assertEquals(locationKeys.size, locationKeys.distinct().size)
        assertEquals(geofenceKeys.size, geofenceKeys.distinct().size)
    }

    @Test
    fun describesEveryLocationApiValue() {
        val keys = LocationFields.of(ExampleData.locationRow).map { it.key }
        val expected = listOf(
            "provider", "lat", "lon", "accuracy", "altitude", "vertical_accuracy",
            "msl_altitude", "msl_altitude_accuracy", "speed", "speed_accuracy", "bearing",
            "bearing_accuracy", "fix_time", "fix_age_ms", "elapsed_realtime_nanos",
            "elapsed_realtime_uncertainty_nanos", "is_mock", "satellites", "extras",
        )
        assertTrue(keys.containsAll(expected))
    }

    @Test
    fun unreportedValuesAreKeptAsNullRatherThanZero() {
        val fields = LocationFields.of(LocationSample())
        assertTrue(fields.all { it.raw == null })
        assertTrue(fields.all { it.display == "—" })
    }

    @Test
    fun webhookParamsSkipUnreportedValues() {
        val params = LocationFields.queryParams(LocationFields.of(LocationSample()))
        assertTrue(params.isEmpty())

        val real = LocationFields.queryParams(LocationFields.of(ExampleData.locationRow))
        assertTrue(real.any { it.first == "lat" })
        assertTrue(real.none { it.first == "msl_altitude" })
    }

    @Test
    fun geofenceEventsCarryFenceAndLocationValues() {
        val fields = LocationFields.of(ExampleData.geofenceRow).associate { it.key to it.raw }
        assertEquals("ENTER", fields["transition"])
        assertEquals("Home", fields["fence_name"])
        assertEquals("150.0", fields["fence_radius"])
        assertEquals("96.2", fields["distance_from_center"])
        assertEquals("-36.8485000", fields["lat"])
    }
}
