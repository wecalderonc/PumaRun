package com.pumarun.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementSchedulerTest {

    private fun running(
        config: SessionConfig,
        distance: Double,
        elapsedMs: Long,
        pace: Double? = null,
    ) = SessionState(
        status = SessionStatus.Running,
        config = config,
        distanceMeters = distance,
        elapsedMs = elapsedMs,
        currentPaceSecPerKm = pace,
        avgPaceSecPerKm = pace,
    )

    /** Simulates a run at constant pace, sampling once per second, and collects announcements. */
    private fun simulate(
        config: SessionConfig,
        scheduler: AnnouncementScheduler,
        seconds: Int,
        paceAt: (Int) -> Double,
    ): List<Pair<Int, Announcement>> {
        val out = mutableListOf<Pair<Int, Announcement>>()
        var distance = 0.0
        for (s in 1..seconds) {
            val pace = paceAt(s)
            distance += 1000.0 / pace
            scheduler.update(running(config, distance, s * 1000L, pace)).forEach { out += s to it }
        }
        return out
    }

    @Test
    fun milestonesEveryKilometer() {
        val config = SessionConfig(goalMeters = 10_000.0, announceIntervalMeters = 1000)
        val s = AnnouncementScheduler(config)
        val events = listOf(900.0, 1000.0, 1500.0, 2001.0, 2500.0).flatMap {
            s.update(running(config, it, 0))
        }
        assertEquals(
            listOf(1000.0, 2000.0),
            events.filterIsInstance<Announcement.Milestone>().map { it.distanceMeters },
        )
    }

    @Test
    fun skippedMilestonesCollapseToLatest() {
        val config = SessionConfig(goalMeters = 10_000.0, announceIntervalMeters = 500)
        val s = AnnouncementScheduler(config)
        val events = s.update(running(config, 1_600.0, 0))
        assertEquals(listOf(Announcement.Milestone(1_500.0, 0, null)), events)
    }

    @Test
    fun noneModeHasNoMilestonesButStillHalfwayAndGoal() {
        val config = SessionConfig(goalMeters = 4_000.0, announceIntervalMeters = null)
        val s = AnnouncementScheduler(config)
        val events = listOf(1_000.0, 2_000.0, 3_000.0, 4_000.0).flatMap { s.update(running(config, it, 0)) }
        assertEquals(2, events.size)
        assertTrue(events[0] is Announcement.Halfway)
        assertTrue(events[1] is Announcement.GoalReached)
    }

    @Test
    fun goalReplacesCoincidingMilestoneAndMilestonesContinueAfter() {
        val config = SessionConfig(goalMeters = 2_000.0, announceIntervalMeters = 1000)
        val s = AnnouncementScheduler(config)
        val atHalf = s.update(running(config, 1_000.0, 0))
        val atGoal = s.update(running(config, 2_000.0, 0))
        val after = s.update(running(config, 3_000.0, 0))
        assertTrue(atHalf.single() is Announcement.Halfway)
        assertTrue(atGoal.single() is Announcement.GoalReached)
        assertEquals(Announcement.Milestone(3_000.0, 0, null), after.single())
    }

    @Test
    fun noHalfwayForShortGoals() {
        val config = SessionConfig(goalMeters = 800.0, announceIntervalMeters = null)
        val s = AnnouncementScheduler(config)
        assertTrue(s.update(running(config, 400.0, 0)).isEmpty())
    }

    @Test
    fun ignoresNonRunningStates() {
        val config = SessionConfig(goalMeters = 1_000.0)
        val s = AnnouncementScheduler(config)
        val paused = running(config, 5_000.0, 0).copy(status = SessionStatus.Paused)
        assertTrue(s.update(paused).isEmpty())
    }

    @Test
    fun paceAlertNeedsSustainedSlowdownAndRespectsCooldown() {
        val config = SessionConfig(
            goalMeters = 20_000.0, targetPaceSecPerKm = 300.0,
            announceIntervalMeters = null, backOnPaceEnabled = false,
        )
        val s = AnnouncementScheduler(config)
        // On pace for the first 120 s, then consistently slow (360 s/km).
        val alerts = simulate(config, s, 400) { t -> if (t <= 120) 300.0 else 360.0 }
            .filter { it.second is Announcement.BelowPace }
            .map { it.first }

        assertEquals(136, alerts.first())
        alerts.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 60) }
        assertEquals(alerts.size, s.paceAlertCount)
    }

    @Test
    fun withinToleranceDoesNotAlert() {
        val config = SessionConfig(goalMeters = 20_000.0, targetPaceSecPerKm = 300.0, announceIntervalMeters = null)
        val s = AnnouncementScheduler(config)
        val events = simulate(config, s, 300) { 312.0 } // 4% slower, tolerance is 5%
        assertTrue(events.none { it.second is Announcement.BelowPace })
    }

    @Test
    fun noAlertsDuringWarmup() {
        val config = SessionConfig(goalMeters = 20_000.0, targetPaceSecPerKm = 200.0, announceIntervalMeters = null)
        val s = AnnouncementScheduler(config)
        // At 600 s/km, 200 m takes 120 s; alert may only fire 15 s after passing warm-up.
        val first = simulate(config, s, 200) { 600.0 }.first { it.second is Announcement.BelowPace }.first
        assertTrue("first alert at $first", first >= 135)
    }

    @Test
    fun alertsDisabledOrNoTargetMeansSilence() {
        val noTarget = SessionConfig(goalMeters = 20_000.0, announceIntervalMeters = null)
        val disabled = noTarget.copy(targetPaceSecPerKm = 300.0, paceAlertsEnabled = false)
        listOf(noTarget, disabled).forEach { config ->
            val events = simulate(config, AnnouncementScheduler(config), 300) { 400.0 }
            assertTrue(events.none { it.second is Announcement.BelowPace })
        }
    }

    @Test
    fun backOnPaceIsSpokenOnceAfterRecovery() {
        val config = SessionConfig(goalMeters = 20_000.0, targetPaceSecPerKm = 300.0, announceIntervalMeters = null)
        val s = AnnouncementScheduler(config)
        val events = simulate(config, s, 400) { t ->
            when {
                t <= 100 -> 300.0
                t <= 150 -> 360.0
                else -> 290.0
            }
        }.map { it.second }
        assertEquals(1, events.count { it is Announcement.BelowPace })
        assertEquals(1, events.count { it == Announcement.BackOnPace })
        assertTrue(events.indexOfFirst { it is Announcement.BelowPace } < events.indexOf(Announcement.BackOnPace))
    }

    @Test
    fun backOnPaceCanBeDisabled() {
        val config = SessionConfig(
            goalMeters = 20_000.0, targetPaceSecPerKm = 300.0,
            announceIntervalMeters = null, backOnPaceEnabled = false,
        )
        val events = simulate(config, AnnouncementScheduler(config), 400) { t -> if (t in 101..150) 360.0 else 290.0 }
        assertTrue(events.none { it.second == Announcement.BackOnPace })
    }
}
