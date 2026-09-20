package com.millerchnz.gpsemulator.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogIntervalsTest {
    @Test
    fun spansOneSecondToThirtyMinutes() {
        assertEquals(1_000L, LogIntervals.optionsMillis.first())
        assertEquals(30 * 60_000L, LogIntervals.optionsMillis.last())
        assertTrue(LogIntervals.optionsMillis.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun coercesToTheNearestOption() {
        assertEquals(1_000L, LogIntervals.coerce(0L))
        assertEquals(5_000L, LogIntervals.coerce(4_400L))
        assertEquals(30 * 60_000L, LogIntervals.coerce(Long.MAX_VALUE / 2))
    }

    @Test
    fun indexIsAlwaysInRange() {
        LogIntervals.optionsMillis.forEachIndexed { index, millis ->
            assertEquals(index, LogIntervals.indexOf(millis))
        }
        assertEquals(0, LogIntervals.indexOf(-5L))
    }

    @Test
    fun labelsReadNaturally() {
        assertEquals("Every second", LogIntervals.label(1_000L))
        assertEquals("Every 30 seconds", LogIntervals.label(30_000L))
        assertEquals("Every minute", LogIntervals.label(60_000L))
        assertEquals("Every 30 minutes", LogIntervals.label(30 * 60_000L))
    }
}
