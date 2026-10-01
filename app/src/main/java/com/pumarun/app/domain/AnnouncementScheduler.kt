package com.pumarun.app.domain

sealed interface Announcement {
    data class Countdown(val seconds: Int) : Announcement
    data object Started : Announcement
    data object Paused : Announcement
    data object Resumed : Announcement
    data class Milestone(
        val distanceMeters: Double,
        val elapsedMs: Long,
        val avgPaceSecPerKm: Double?,
        /** Seconds behind (+) or ahead of (-) target; null without a target. */
        val scheduleDeltaSec: Double? = null,
    ) : Announcement
    data class Halfway(val distanceMeters: Double, val elapsedMs: Long) : Announcement
    data class GoalReached(
        val goalMeters: Double,
        val elapsedMs: Long,
        val avgPaceSecPerKm: Double?,
        val scheduleDeltaSec: Double? = null,
    ) : Announcement
    data class BelowPace(val currentPaceSecPerKm: Double, val targetPaceSecPerKm: Double) : Announcement
    data object BackOnPace : Announcement
    data class Coach(val cue: CoachCue, val style: CoachStyle) : Announcement
    data class Finished(val distanceMeters: Double, val elapsedMs: Long) : Announcement
}

/**
 * Decides what to say and when, from successive [SessionState] snapshots.
 * All timing uses active session time ([SessionState.elapsedMs]), so pauses freeze the timers.
 *
 * With a [CoachStyle] other than Off, repeated below-pace warnings become trainer pushes
 * (every [FACTS_EVERY_N_PUSHES]th one still states the numbers), on-pace running earns periodic
 * encouragement, and the finish gets "almost there" / last-200 m cheers.
 */
class AnnouncementScheduler(
    private val config: SessionConfig,
    private val paceTolerance: Double = SessionState.PACE_TOLERANCE,
    private val sustainMs: Long = config.coachStyle.paceSustainMs,
    private val cooldownMs: Long = config.coachStyle.paceRepeatMs,
    private val warmupMeters: Double = 200.0,
    private val minGoalForHalfwayMeters: Double = 1_000.0,
) {
    private val style = config.coachStyle

    private var nextMilestoneMeters: Double? = config.announceIntervalMeters?.toDouble()
    private var halfwayAnnounced = config.goalMeters < minGoalForHalfwayMeters
    private var goalAnnounced = false
    private var almostThereAnnounced = !style.cheersFinish || config.goalMeters < MIN_GOAL_FOR_FINISH_CHEERS
    private var finalMetersAnnounced = !style.cheersFinish || config.goalMeters < MIN_GOAL_FOR_FINAL_METERS

    private var belowSinceMs: Long? = null
    private var lastAlertMs: Long? = null
    private var awaitingRecovery = false
    private var pushesThisStreak = 0
    private var lastCoachSpeechMs = 0L

    var paceAlertCount: Int = 0
        private set

    fun update(state: SessionState): List<Announcement> {
        if (state.status != SessionStatus.Running) return emptyList()
        val out = mutableListOf<Announcement>()
        val distance = state.distanceMeters
        val target = config.targetPaceSecPerKm

        var milestone: Announcement.Milestone? = null
        nextMilestoneMeters?.let { next ->
            var n = next
            var passed: Double? = null
            while (distance >= n) {
                passed = n
                n += config.announceIntervalMeters!!
            }
            nextMilestoneMeters = n
            if (passed != null) {
                milestone = Announcement.Milestone(
                    passed, state.elapsedMs, state.avgPaceSecPerKm,
                    target?.let { SessionState.scheduleDelta(state.elapsedMs, passed, it) },
                )
            }
        }

        val goalNow = !goalAnnounced && distance >= config.goalMeters
        val halfwayNow = !halfwayAnnounced && !goalNow && distance >= config.goalMeters / 2
        if (goalNow) {
            goalAnnounced = true
            halfwayAnnounced = true
            almostThereAnnounced = true
            finalMetersAnnounced = true
            val goalTime = state.goalTimeMs ?: state.elapsedMs
            out += Announcement.GoalReached(
                config.goalMeters, goalTime, state.avgPaceSecPerKm,
                target?.let { SessionState.scheduleDelta(goalTime, config.goalMeters, it) },
            )
        } else if (halfwayNow) {
            halfwayAnnounced = true
            out += Announcement.Halfway(distance, state.elapsedMs)
        } else {
            milestone?.let { out += it }
        }

        val paceCue = evaluatePace(state)
        if (paceCue != null) {
            out += paceCue
        } else if (!goalNow) {
            coachCue(state)?.let { out += it }
        }
        return out
    }

    private fun evaluatePace(state: SessionState): Announcement? {
        val target = config.targetPaceSecPerKm ?: return null
        if (!config.paceAlertsEnabled || goalAnnounced) return null
        if (state.distanceMeters < warmupMeters) {
            belowSinceMs = null
            return null
        }
        val current = state.currentPaceSecPerKm ?: return null
        val now = state.elapsedMs

        if (current > target * (1 + paceTolerance)) {
            val since = belowSinceMs ?: now.also { belowSinceMs = it }
            val cooledDown = lastAlertMs?.let { now - it >= cooldownMs } ?: true
            if (now - since >= sustainMs && cooledDown) {
                lastAlertMs = now
                lastCoachSpeechMs = now
                paceAlertCount++
                val isRepeat = awaitingRecovery
                awaitingRecovery = true
                if (style == CoachStyle.Off || !isRepeat || ++pushesThisStreak % FACTS_EVERY_N_PUSHES == 0) {
                    return Announcement.BelowPace(current, target)
                }
                return Announcement.Coach(CoachCue.Push, style)
            }
            return null
        }

        belowSinceMs = null
        if (awaitingRecovery && current <= target) {
            awaitingRecovery = false
            pushesThisStreak = 0
            if (config.backOnPaceEnabled) {
                lastCoachSpeechMs = now
                return if (style == CoachStyle.Off) Announcement.BackOnPace
                else Announcement.Coach(CoachCue.BackOnPace, style)
            }
        }
        return null
    }

    private fun coachCue(state: SessionState): Announcement? {
        if (style == CoachStyle.Off) return null
        val now = state.elapsedMs
        val remaining = config.goalMeters - state.distanceMeters

        if (!finalMetersAnnounced && remaining <= FINAL_METERS) {
            finalMetersAnnounced = true
            almostThereAnnounced = true
            lastCoachSpeechMs = now
            return Announcement.Coach(CoachCue.FinalMeters, style)
        }
        if (!almostThereAnnounced && state.distanceMeters >= config.goalMeters * ALMOST_THERE_FRACTION) {
            almostThereAnnounced = true
            lastCoachSpeechMs = now
            return Announcement.Coach(CoachCue.AlmostThere, style)
        }

        val every = style.encourageEveryMs ?: return null
        if (goalAnnounced || awaitingRecovery || state.distanceMeters < warmupMeters) return null
        if (state.currentPaceSecPerKm == null) return null
        val target = config.targetPaceSecPerKm
        val onPace = target == null || !config.paceAlertsEnabled ||
            state.currentPaceSecPerKm <= target * (1 + paceTolerance)
        if (onPace && now - lastCoachSpeechMs >= every) {
            lastCoachSpeechMs = now
            return Announcement.Coach(CoachCue.Encourage, style)
        }
        return null
    }

    companion object {
        const val FACTS_EVERY_N_PUSHES = 3
        private const val ALMOST_THERE_FRACTION = 0.85
        private const val FINAL_METERS = 200.0
        private const val MIN_GOAL_FOR_FINISH_CHEERS = 800.0
        private const val MIN_GOAL_FOR_FINAL_METERS = 2_000.0
    }
}
