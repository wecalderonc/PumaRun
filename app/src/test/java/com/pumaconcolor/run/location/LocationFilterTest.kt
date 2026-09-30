package com.pumaconcolor.run.location

import com.pumaconcolor.run.TestPoints.north
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFilterTest {

    private val filter = LocationFilter()

    private fun LocationFilter.Result.meters() = (this as LocationFilter.Result.Accepted).segmentMeters

    @Test
    fun firstFixIsAnchorWithNoDistance() {
        assertEquals(0.0, filter.accept(north(0.0, 1_000), 1_000).meters(), 0.0)
    }

    @Test
    fun steadyRunAccumulatesDistance() {
        var total = 0.0
        for (i in 0..100) {
            val t = i * 1_000L
            total += filter.accept(north(i * 3.0, t), t).meters()
        }
        assertEquals(300.0, total, 3.0)
    }

    @Test
    fun rejectsPoorAccuracy() {
        filter.accept(north(0.0, 0), 0)
        val result = filter.accept(north(10.0, 1_000, accuracy = 35f), 1_000)
        assertEquals(LocationFilter.Result.Rejected(LocationFilter.Reason.PoorAccuracy), result)
    }

    @Test
    fun rejectsStaleFix() {
        val result = filter.accept(north(0.0, 0), 20_000)
        assertEquals(LocationFilter.Result.Rejected(LocationFilter.Reason.Stale), result)
    }

    @Test
    fun rejectsGpsJumpAndKeepsAnchor() {
        filter.accept(north(0.0, 0), 0)
        val jump = filter.accept(north(100.0, 1_000), 1_000)
        assertEquals(LocationFilter.Result.Rejected(LocationFilter.Reason.Jump), jump)
        // Next realistic fix is measured from the original anchor.
        assertEquals(6.0, filter.accept(north(6.0, 2_000), 2_000).meters(), 0.1)
    }

    @Test
    fun longGapUsesStricterSpeedLimit() {
        filter.accept(north(0.0, 0), 0)
        // 820 m after an 80 s signal loss is 10.25 m/s: fine for one second, impossible to sustain.
        val result = filter.accept(north(820.0, 80_000), 80_000)
        assertEquals(LocationFilter.Result.Rejected(LocationFilter.Reason.Jump), result)
        // A plausible run through the same gap (250 m in 80 s) still counts.
        val tunnel = LocationFilter()
        tunnel.accept(north(0.0, 0), 0)
        assertEquals(250.0, tunnel.accept(north(250.0, 80_000), 80_000).meters(), 0.5)
    }

    @Test
    fun reanchorsAfterRepeatedJumpsWithoutAddingDistance() {
        filter.accept(north(0.0, 0), 0)
        filter.accept(north(900.0, 1_000), 1_000)
        filter.accept(north(903.0, 2_000), 2_000)
        assertEquals(0.0, filter.accept(north(906.0, 3_000), 3_000).meters(), 0.0)
        // Tracking resumes from the new anchor.
        assertEquals(6.0, filter.accept(north(912.0, 5_000), 5_000).meters(), 0.1)
    }

    @Test
    fun stationaryJitterAddsNothing() {
        filter.accept(north(0.0, 0), 0)
        var total = 0.0
        val jitter = listOf(1.5, -2.0, 2.5, -1.0, 3.0, -2.5, 1.0, 2.0, -3.0, 0.5)
        jitter.forEachIndexed { i, offset ->
            val t = (i + 1) * 1_000L
            total += filter.accept(north(offset, t, accuracy = 6f), t).meters()
        }
        assertEquals(0.0, total, 0.0)
    }

    @Test
    fun reportedStationarySpeedSuppressesMediumDrift() {
        filter.accept(north(0.0, 0), 0)
        val drift = filter.accept(north(8.0, 5_000, accuracy = 4f, speed = 0.1f), 5_000)
        assertEquals(0.0, drift.meters(), 0.0)
    }

    @Test
    fun resetStartsNewChain() {
        filter.accept(north(0.0, 0), 0)
        filter.reset()
        // After a pause the runner may have walked elsewhere; that gap must not count.
        assertEquals(0.0, filter.accept(north(500.0, 60_000), 60_000).meters(), 0.0)
        assertTrue(filter.accept(north(510.0, 63_000), 63_000).meters() > 9.0)
    }
}
