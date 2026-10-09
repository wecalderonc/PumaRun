package com.pumarun.app.domain

import android.content.Intent

data class SessionConfig(
    val goalMeters: Double,
    /** Set directly (pace/speed target) or derived from a target time for the goal distance. */
    val targetPaceSecPerKm: Double? = null,
    /** Distance between spoken milestones; null means no distance announcements. */
    val announceIntervalMeters: Int? = 1000,
    val paceAlertsEnabled: Boolean = true,
    val backOnPaceEnabled: Boolean = true,
    val autoStopAtGoal: Boolean = false,
    val coachStyle: CoachStyle = CoachStyle.Off,
    /** Distance to cover after the countdown before the timed run starts on its own. */
    val warmupMeters: Double = 0.0,
    /** Speak every 100 m of distance left during the final kilometer. */
    val countdownLastKm: Boolean = false,
) {
    init {
        require(goalMeters > 0) { "goalMeters must be positive" }
        require(warmupMeters >= 0) { "warmupMeters must be non-negative" }
        require(announceIntervalMeters == null || announceIntervalMeters > 0)
    }

    /** Time to cover the goal distance at the target pace. */
    val targetTimeSec: Double?
        get() = targetPaceSecPerKm?.let { it * goalMeters / 1000.0 }

    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_GOAL, goalMeters)
        putExtra(EXTRA_PACE, targetPaceSecPerKm ?: -1.0)
        putExtra(EXTRA_INTERVAL, announceIntervalMeters ?: -1)
        putExtra(EXTRA_PACE_ALERTS, paceAlertsEnabled)
        putExtra(EXTRA_BACK_ON_PACE, backOnPaceEnabled)
        putExtra(EXTRA_AUTO_STOP, autoStopAtGoal)
        putExtra(EXTRA_COACH, coachStyle.name)
        putExtra(EXTRA_WARMUP, warmupMeters)
        putExtra(EXTRA_LAST_KM, countdownLastKm)
    }

    companion object {
        private const val EXTRA_GOAL = "config.goal"
        private const val EXTRA_PACE = "config.pace"
        private const val EXTRA_INTERVAL = "config.interval"
        private const val EXTRA_PACE_ALERTS = "config.paceAlerts"
        private const val EXTRA_BACK_ON_PACE = "config.backOnPace"
        private const val EXTRA_AUTO_STOP = "config.autoStop"
        private const val EXTRA_COACH = "config.coach"
        private const val EXTRA_WARMUP = "config.warmup"
        private const val EXTRA_LAST_KM = "config.lastKm"

        fun fromIntent(intent: Intent): SessionConfig? {
            val goal = intent.getDoubleExtra(EXTRA_GOAL, -1.0)
            if (goal <= 0) return null
            val pace = intent.getDoubleExtra(EXTRA_PACE, -1.0)
            val interval = intent.getIntExtra(EXTRA_INTERVAL, -1)
            val coach = intent.getStringExtra(EXTRA_COACH)
            return SessionConfig(
                goalMeters = goal,
                targetPaceSecPerKm = pace.takeIf { it > 0 },
                announceIntervalMeters = interval.takeIf { it > 0 },
                paceAlertsEnabled = intent.getBooleanExtra(EXTRA_PACE_ALERTS, true),
                backOnPaceEnabled = intent.getBooleanExtra(EXTRA_BACK_ON_PACE, true),
                autoStopAtGoal = intent.getBooleanExtra(EXTRA_AUTO_STOP, false),
                coachStyle = CoachStyle.entries.firstOrNull { it.name == coach } ?: CoachStyle.Off,
                warmupMeters = intent.getDoubleExtra(EXTRA_WARMUP, 0.0).coerceAtLeast(0.0),
                countdownLastKm = intent.getBooleanExtra(EXTRA_LAST_KM, false),
            )
        }
    }
}
