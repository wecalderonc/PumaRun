package com.pumaconcolor.run.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceCalculatorTest {

    @Test
    fun steadyPaceIsReported() {
        val calc = PaceCalculator()
        // 3 m/s = 333.3 s/km
        for (s in 0..60) calc.add(s * 1_000L, s * 3.0)
        assertEquals(333.3, calc.currentPace(60_000)!!, 1.0)
    }

    @Test
    fun windowOnlyReflectsRecentPace() {
        val calc = PaceCalculator(windowMs = 30_000)
        var d = 0.0
        for (s in 0..60) {
            d += 2.0 // slow: 500 s/km
            calc.add(s * 1_000L, d)
        }
        for (s in 61..100) {
            d += 4.0 // fast: 250 s/km
            calc.add(s * 1_000L, d)
        }
        assertEquals(250.0, calc.currentPace(100_000)!!, 10.0)
    }

    @Test
    fun stoppedRunnerHasNoPace() {
        val calc = PaceCalculator()
        for (s in 0..40) calc.add(s * 1_000L, 100.0)
        assertNull(calc.currentPace(40_000))
    }

    @Test
    fun notEnoughSamples() {
        val calc = PaceCalculator()
        assertNull(calc.currentPace(0))
        calc.add(0, 0.0)
        assertNull(calc.currentPace(0))
    }

    @Test
    fun resetClearsWindow() {
        val calc = PaceCalculator()
        for (s in 0..30) calc.add(s * 1_000L, s * 3.0)
        calc.reset()
        assertNull(calc.currentPace(30_000))
    }

    @Test
    fun averagePace() {
        assertEquals(300.0, PaceCalculator.averagePace(1_500_000, 5_000.0)!!, 0.001)
        assertNull(PaceCalculator.averagePace(10_000, 0.0))
    }
}
