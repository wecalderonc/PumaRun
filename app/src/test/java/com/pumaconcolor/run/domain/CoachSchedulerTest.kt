package com.pumaconcolor.run.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachSchedulerTest {

    private fun config(style: CoachStyle, goal: Double = 20_000.0, target: Double? = 300.0) =
        SessionConfig(goalMeters = goal, targetPaceSecPerKm = target, announceIntervalMeters = null, coachStyle = style)

    /** One update per second at the pace returned by [paceAt]; returns (second, announcement). */
    private fun simulate(config: SessionConfig, seconds: Int, paceAt: (Int) -> Double): List<Pair<Int, Announcement>> {
        val scheduler = AnnouncementScheduler(config)
        val out = mutableListOf<Pair<Int, Announcement>>()
        var distance = 0.0
        for (s in 1..seconds) {
            val pace = paceAt(s)
            distance += 1000.0 / pace
            val state = SessionState(
                status = SessionStatus.Running, config = config, distanceMeters = distance,
                elapsedMs = s * 1000L, currentPaceSecPerKm = pace, avgPaceSecPerKm = pace,
            )
            scheduler.update(state).forEach { out += s to it }
        }
        return out
    }

    private fun List<Pair<Int, Announcement>>.slowCues() =
        map { it.second }.filter { it is Announcement.BelowPace || (it as? Announcement.Coach)?.cue == CoachCue.Push }

    @Test
    fun harderStylesPushMoreOften() {
        val counts = CoachStyle.entries.associateWith { style ->
            simulate(config(style), 600) { t -> if (t <= 100) 300.0 else 360.0 }.slowCues().size
        }
        assertTrue(counts.toString(), counts[CoachStyle.Soft]!! < counts[CoachStyle.Medium]!!)
        assertTrue(counts.toString(), counts[CoachStyle.Medium]!! < counts[CoachStyle.Hard]!!)
        assertTrue(counts.toString(), counts[CoachStyle.Hard]!! < counts[CoachStyle.Annoying]!!)
    }

    @Test
    fun firstWarningStatesNumbersThenPushesWithFactsEveryThird() {
        val cues = simulate(config(CoachStyle.Medium), 600) { t -> if (t <= 100) 300.0 else 360.0 }.slowCues()
        assertTrue(cues[0] is Announcement.BelowPace)
        assertTrue(cues[1] is Announcement.Coach)
        assertTrue(cues[2] is Announcement.Coach)
        assertTrue(cues[3] is Announcement.BelowPace)
    }

    @Test
    fun offStyleNeverUsesCoachPhrases() {
        val events = simulate(config(CoachStyle.Off, goal = 2_000.0), 700) { t -> if (t in 101..200) 360.0 else 290.0 }
        assertTrue(events.none { it.second is Announcement.Coach })
        assertTrue(events.any { it.second == Announcement.BackOnPace })
    }

    @Test
    fun backOnPaceUsesTrainerVoice() {
        val events = simulate(config(CoachStyle.Hard), 400) { t -> if (t in 101..150) 360.0 else 290.0 }
        assertTrue(events.any { it.second == Announcement.Coach(CoachCue.BackOnPace, CoachStyle.Hard) })
        assertTrue(events.none { it.second == Announcement.BackOnPace })
    }

    @Test
    fun encouragementWhileOnPaceFollowsStyleInterval() {
        val medium = simulate(config(CoachStyle.Medium), 600) { 295.0 }
            .filter { (it.second as? Announcement.Coach)?.cue == CoachCue.Encourage }.map { it.first }
        assertEquals(listOf(150, 300, 450, 600), medium)
        val annoying = simulate(config(CoachStyle.Annoying), 600) { 295.0 }
            .count { (it.second as? Announcement.Coach)?.cue == CoachCue.Encourage }
        assertEquals(10, annoying)
    }

    @Test
    fun encouragementWorksWithoutATarget() {
        val events = simulate(config(CoachStyle.Medium, target = null), 400) { 330.0 }
        assertTrue(events.any { (it.second as? Announcement.Coach)?.cue == CoachCue.Encourage })
    }

    @Test
    fun finishCheersFireOnceBeforeGoal() {
        val events = simulate(config(CoachStyle.Soft, goal = 5_000.0, target = null), 1_700) { 300.0 }.map { it.second }
        val almost = events.indexOfFirst { (it as? Announcement.Coach)?.cue == CoachCue.AlmostThere }
        val final = events.indexOfFirst { (it as? Announcement.Coach)?.cue == CoachCue.FinalMeters }
        val goal = events.indexOfFirst { it is Announcement.GoalReached }
        assertTrue("almost=$almost final=$final goal=$goal", almost in 0 until final && final < goal)
        assertEquals(1, events.count { (it as? Announcement.Coach)?.cue == CoachCue.AlmostThere })
        assertEquals(1, events.count { (it as? Announcement.Coach)?.cue == CoachCue.FinalMeters })
    }

    @Test
    fun coachGoesQuietAfterGoal() {
        // Goal at 1 km on pace, then a slow cool-down: no more pushes or warnings.
        val events = simulate(config(CoachStyle.Annoying, goal = 1_000.0), 600) { t -> if (t <= 300) 295.0 else 450.0 }
        val goalAt = events.first { it.second is Announcement.GoalReached }.first
        assertTrue(events.none { it.first > goalAt && (it.second is Announcement.BelowPace || it.second is Announcement.Coach) })
    }

    @Test
    fun milestonesAndGoalReportScheduleDelta() {
        val config = SessionConfig(goalMeters = 2_000.0, targetPaceSecPerKm = 300.0, announceIntervalMeters = 500)
        val s = AnnouncementScheduler(config)
        val atMilestone = s.update(SessionState(SessionStatus.Running, config, distanceMeters = 500.0, elapsedMs = 140_000))
        assertEquals(-10.0, (atMilestone.single() as Announcement.Milestone).scheduleDeltaSec!!, 1e-9)

        val atGoal = s.update(
            SessionState(SessionStatus.Running, config, distanceMeters = 2_003.0, elapsedMs = 612_000, goalTimeMs = 611_000)
        )
        val goal = atGoal.single() as Announcement.GoalReached
        assertEquals(611_000L, goal.elapsedMs)
        assertEquals(11.0, goal.scheduleDeltaSec!!, 1e-9)
    }
}
