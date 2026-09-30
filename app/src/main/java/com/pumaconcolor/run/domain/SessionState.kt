package com.pumaconcolor.run.domain

enum class SessionStatus { Idle, Countdown, Running, Paused, Finished }

data class SessionState(
    val status: SessionStatus = SessionStatus.Idle,
    val config: SessionConfig? = null,
    val countdownSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val elapsedMs: Long = 0L,
    val currentPaceSecPerKm: Double? = null,
    val avgPaceSecPerKm: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val goalReached: Boolean = false,
    /** Active time at the moment the goal distance was first reached. */
    val goalTimeMs: Long? = null,
    val paceAlertCount: Int = 0,
) {
    val isActive: Boolean
        get() = status == SessionStatus.Countdown || status == SessionStatus.Running ||
            status == SessionStatus.Paused

    val progress: Float
        get() = config?.let { (distanceMeters / it.goalMeters).toFloat().coerceIn(0f, 1f) } ?: 0f

    val isBelowTarget: Boolean
        get() {
            val target = config?.targetPaceSecPerKm ?: return false
            val current = currentPaceSecPerKm ?: return false
            return current > target * (1 + PACE_TOLERANCE)
        }

    /** Seconds behind (+) or ahead of (-) the target schedule at the current distance. */
    val scheduleDeltaSec: Double?
        get() {
            val target = config?.targetPaceSecPerKm ?: return null
            if (distanceMeters < MIN_DISTANCE_FOR_SCHEDULE) return null
            return scheduleDelta(elapsedMs, distanceMeters, target)
        }

    companion object {
        const val PACE_TOLERANCE = 0.05
        private const val MIN_DISTANCE_FOR_SCHEDULE = 50.0

        fun scheduleDelta(elapsedMs: Long, distanceMeters: Double, targetPaceSecPerKm: Double): Double =
            elapsedMs / 1000.0 - targetPaceSecPerKm * distanceMeters / 1000.0
    }
}
