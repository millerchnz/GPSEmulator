package com.millerchnz.gpsemulator.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapViewModelTest {
    @Test
    fun parseDecimalPair() {
        val parsed = MapViewModel.parseCoordinates("-36.8485, 174.7633")
        assertEquals(-36.8485, parsed!!.first, 0.00001)
        assertEquals(174.7633, parsed.second, 0.00001)
    }

    @Test
    fun parseSpaceSeparated() {
        val parsed = MapViewModel.parseCoordinates("-36.8485 174.7633")
        assertEquals(-36.8485, parsed!!.first, 0.00001)
        assertEquals(174.7633, parsed.second, 0.00001)
    }

    @Test
    fun rejectOutOfRange() {
        assertNull(MapViewModel.parseCoordinates("91, 0"))
        assertNull(MapViewModel.parseCoordinates("0, 181"))
        assertNull(MapViewModel.parseCoordinates("not coordinates"))
    }

    @Test
    fun formatElapsedUnderAnHour() {
        assertEquals("0:00", MapViewModel.formatElapsed(0))
        assertEquals("0:05", MapViewModel.formatElapsed(5_000))
        assertEquals("1:02", MapViewModel.formatElapsed(62_000))
        assertEquals("12:45", MapViewModel.formatElapsed(12 * 60_000L + 45_000L))
    }

    @Test
    fun formatElapsedOverAnHour() {
        assertEquals("1:00:00", MapViewModel.formatElapsed(3_600_000))
        assertEquals("1:02:03", MapViewModel.formatElapsed(3_723_000))
    }
}
