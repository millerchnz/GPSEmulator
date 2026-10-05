package com.millerchnz.gpsemulator.ui.tour

import com.millerchnz.gpsemulator.ui.nav.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureTourTest {
    @Test
    fun walksMockThenLoggingThenGeofences() {
        assertEquals(
            listOf(
                TourAnchors.APPLY_MOCK to AppRoutes.MAP,
                TourAnchors.LOG_ENABLED to AppRoutes.LOGGING,
                TourAnchors.LOG_SEND to AppRoutes.LOGGING,
                TourAnchors.LOG_VIEW to AppRoutes.LOGGING,
                TourAnchors.FENCE_ENABLED to AppRoutes.GEOFENCES,
                TourAnchors.FENCE_ADD to AppRoutes.GEOFENCES,
                TourAnchors.FENCE_ALERTS to AppRoutes.GEOFENCES,
                TourAnchors.FENCE_SEND to AppRoutes.GEOFENCES,
                TourAnchors.FENCE_EVENTS to AppRoutes.GEOFENCES,
            ),
            TourSteps.map { it.anchor to it.route },
        )
        assertEquals(TourSteps.map { it.anchor }.distinct().size, TourSteps.size)
        assertTrue(TourSteps.all { it.title.isNotBlank() && it.body.isNotBlank() })
    }

    @Test
    fun nextReachesTheEndThenFinishesOnce() {
        var finished = 0
        val tour = FeatureTourController(onFinished = { finished++ })
        assertFalse(tour.active)
        tour.next()
        tour.skipAll()
        assertEquals(0, finished)

        tour.start()
        assertEquals(TourSteps.first(), tour.step)
        repeat(TourSteps.lastIndex) { tour.next() }
        assertTrue(tour.isLast)
        assertEquals(TourSteps.last().anchor, tour.step?.anchor)
        tour.next()
        assertFalse(tour.active)
        assertNull(tour.step)
        assertEquals(1, finished)
    }

    @Test
    fun skipAllFinishesFromAnyStep() {
        var finished = 0
        val tour = FeatureTourController(onFinished = { finished++ })
        tour.start()
        tour.next()
        tour.skipAll()
        assertFalse(tour.active)
        assertEquals(1, finished)
    }

    @Test
    fun previousStopsAtTheFirstStep() {
        val seen = mutableListOf<Int>()
        val tour = FeatureTourController(onIndex = { seen += it })
        tour.start()
        tour.next()
        tour.next()
        tour.previous()
        tour.previous()
        tour.previous()
        assertEquals(0, tour.index)
        assertEquals(listOf(0, 1, 2, 1, 0), seen)
    }

    @Test
    fun helpRestartsAfterTheTourWasFinished() {
        val tour = FeatureTourController()
        tour.start()
        tour.skipAll()
        tour.start()
        assertEquals(0, tour.index)
        assertEquals(TourAnchors.APPLY_MOCK, tour.step?.anchor)
    }
}
