package com.pumaconcolor.run.domain

import com.pumaconcolor.run.location.LocationFilter

/**
 * Pure run-session bookkeeping: active time, filtered distance and pace.
 * [clock] must be monotonic (SystemClock.elapsedRealtime in production).
 */
class SessionEngine(
    private val config: SessionConfig,
    private val clock: () -> Long,
    private val filter: LocationFilter = LocationFilter(),
    private val paceCalculator: PaceCalculator = PaceCalculator(),
) {
    private var accumulatedMs = 0L
    private var runningSinceMs: Long? = null
    private var distanceMeters = 0.0
    private var lastAccuracy: Float? = null
    private var goalTimeMs: Long? = null

    val isRunning: Boolean get() = runningSinceMs != null

    fun start() {
        if (runningSinceMs == null) runningSinceMs = clock()
    }

    fun pause() {
        val since = runningSinceMs ?: return
        accumulatedMs += clock() - since
        runningSinceMs = null
        filter.reset()
        paceCalculator.reset()
    }

    fun resume() = start()

    fun elapsedMs(): Long = accumulatedMs + (runningSinceMs?.let { clock() - it } ?: 0L)

    /** Feeds a raw GPS fix. Returns the distance (m) added by this fix. */
    fun onLocation(point: TrackPoint): Double {
        lastAccuracy = point.accuracyMeters
        if (!isRunning) return 0.0
        val result = filter.accept(point, clock())
        if (result is LocationFilter.Result.Accepted && result.segmentMeters > 0) {
            distanceMeters += result.segmentMeters
            if (goalTimeMs == null && distanceMeters >= config.goalMeters) goalTimeMs = elapsedMs()
            paceCalculator.add(elapsedMs(), distanceMeters)
            return result.segmentMeters
        }
        return 0.0
    }

    /** Called periodically (about 1 Hz) so pace decays toward "stopped" when no movement arrives. */
    fun tick() {
        if (isRunning) paceCalculator.add(elapsedMs(), distanceMeters)
    }

    fun snapshot(base: SessionState): SessionState {
        val elapsed = elapsedMs()
        return base.copy(
            config = config,
            distanceMeters = distanceMeters,
            elapsedMs = elapsed,
            currentPaceSecPerKm = if (isRunning) paceCalculator.currentPace(elapsed) else base.currentPaceSecPerKm,
            avgPaceSecPerKm = PaceCalculator.averagePace(elapsed, distanceMeters),
            gpsAccuracyMeters = lastAccuracy,
            goalReached = distanceMeters >= config.goalMeters,
            goalTimeMs = goalTimeMs,
        )
    }
}
