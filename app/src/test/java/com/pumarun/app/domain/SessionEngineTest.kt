package com.pumarun.app.domain

import com.pumarun.app.TestPoints.north
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private var now = 0L
    private val engine = SessionEngine(SessionConfig(goalMeters = 300.0), clock = { now })

    private fun runFor(seconds: Int, startMeters: Double, mps: Double): Double {
        var d = startMeters
        repeat(seconds) {
            now += 1_000
            d += mps
            engine.onLocation(north(d, now))
            engine.tick()
        }
        return d
    }

    @Test
    fun ignoresFixesBeforeStart() {
        engine.onLocation(north(0.0, now))
        now += 1_000
        engine.onLocation(north(50.0, now))
        assertEquals(0.0, engine.snapshot(SessionState()).distanceMeters, 0.0)
    }

    @Test
    fun tracksDistanceTimeAndGoal() {
        engine.start()
        engine.onLocation(north(0.0, now))
        runFor(120, 0.0, 3.0)
        val s = engine.snapshot(SessionState())
        assertEquals(360.0, s.distanceMeters, 6.0)
        assertEquals(120_000L, s.elapsedMs)
        assertTrue(s.goalReached)
        assertEquals(333.3, s.currentPaceSecPerKm!!, 15.0)
        assertNotNull(s.avgPaceSecPerKm)
    }

    @Test
    fun pausedTimeAndMovementAreExcluded() {
        engine.start()
        engine.onLocation(north(0.0, now))
        var d = runFor(30, 0.0, 3.0)
        engine.pause()

        // 60 s walking elsewhere while paused.
        repeat(60) {
            now += 1_000
            d += 2.0
            engine.onLocation(north(d, now))
        }
        val paused = engine.snapshot(SessionState())
        assertEquals(30_000L, paused.elapsedMs)

        engine.resume()
        runFor(30, d, 3.0)
        val s = engine.snapshot(SessionState())
        assertEquals(60_000L, s.elapsedMs)
        assertEquals(180.0, s.distanceMeters, 12.0)
        assertFalse(s.goalReached)
    }

    @Test
    fun mapPositionUpdatesBeforeStartAndTrackSplitsOnPause() {
        engine.onLocation(north(0.0, now))
        val before = engine.snapshot(SessionState())
        assertNotNull(before.position)
        assertTrue(before.track.isEmpty())

        engine.start()
        engine.onLocation(north(0.0, now))
        runFor(20, 0.0, 3.0)
        val running = engine.snapshot(SessionState())
        assertEquals(1, running.track.size)
        assertTrue(running.track.single().size >= 2)

        engine.pause()
        val gap = north(400.0, now + 1_000)
        now += 1_000
        engine.onLocation(gap)
        assertEquals(1, engine.snapshot(SessionState()).track.size)

        engine.resume()
        engine.onLocation(gap)
        now += 4_000
        val last = north(412.0, now)
        engine.onLocation(last)
        val after = engine.snapshot(SessionState())
        assertEquals(2, after.track.size)
        assertTrue(after.track[1].size >= 2)
        assertEquals(last.latitude, after.position!!.latitude, 1e-6)
    }

    @Test
    fun warmupDoesNotCountTowardTheGoalAndTheClockRestarts() {
        var clock = 0L
        val warming = SessionEngine(
            SessionConfig(goalMeters = 200.0, warmupMeters = 100.0),
            clock = { clock },
        )
        warming.start()
        warming.onLocation(north(0.0, clock))
        var traveled = 0.0
        repeat(28) {
            clock += 1_000
            traveled += 3.0
            warming.onLocation(north(traveled, clock))
        }
        val during = warming.snapshot(SessionState())
        assertTrue(during.warmingUp)
        assertFalse(during.goalReached)
        assertTrue("warmup distance ${during.distanceMeters}", during.distanceMeters in 60.0..99.0)

        repeat(40) {
            clock += 1_000
            traveled += 3.0
            warming.onLocation(north(traveled, clock))
        }
        val race = warming.snapshot(SessionState())
        assertFalse(race.warmingUp)
        assertTrue("race distance ${race.distanceMeters}", race.distanceMeters in 20.0..140.0)
        assertTrue("race clock ${race.elapsedMs} vs total $clock", race.elapsedMs < clock - 20_000)
        assertFalse(race.goalReached)
        assertEquals(1, race.track.size)
    }

    @Test
    fun warmupLongerThanTheGoalDoesNotFinishTheRun() {
        var clock = 0L
        val engine = SessionEngine(
            SessionConfig(goalMeters = 50.0, warmupMeters = 120.0),
            clock = { clock },
        )
        engine.start()
        engine.onLocation(north(0.0, clock))
        var traveled = 0.0
        repeat(30) {
            clock += 1_000
            traveled += 3.0
            engine.onLocation(north(traveled, clock))
        }
        val during = engine.snapshot(SessionState())
        assertTrue(during.warmingUp)
        assertTrue(during.distanceMeters > 50.0)
        assertFalse(during.goalReached)
    }
}
