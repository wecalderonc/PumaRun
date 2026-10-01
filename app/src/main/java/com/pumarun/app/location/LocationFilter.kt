package com.pumarun.app.location

import com.pumarun.app.domain.TrackPoint

/**
 * Rejects noisy GPS fixes and suppresses stationary drift.
 *
 * The anchor only moves once the runner is farther from it than the fix's accuracy radius
 * (clamped to [minSegmentMeters]..[maxSegmentMeters]), so jitter while standing still adds nothing.
 *
 * Over gaps longer than [longGapMs] the speed limit drops to [maxSustainedSpeedMps]: 12 m/s is
 * plausible between two 1 s fixes, but not averaged over a long signal loss.
 * After [reanchorAfterJumps] consecutive jumps the anchor itself is assumed bad and is replaced
 * (without adding distance), so one outlier cannot block tracking forever.
 */
class LocationFilter(
    private val maxAccuracyMeters: Float = 20f,
    private val maxSpeedMps: Double = 12.0,
    private val maxSustainedSpeedMps: Double = 8.0,
    private val longGapMs: Long = 10_000L,
    private val reanchorAfterJumps: Int = 3,
    private val maxAgeMs: Long = 10_000L,
    private val minSegmentMeters: Double = 3.0,
    private val maxSegmentMeters: Double = 10.0,
    private val stationarySpeedMps: Float = 0.3f,
) {
    sealed interface Result {
        data class Accepted(val segmentMeters: Double) : Result
        data class Rejected(val reason: Reason) : Result
    }

    enum class Reason { PoorAccuracy, Stale, Jump, OutOfOrder }

    private var anchor: TrackPoint? = null
    private var consecutiveJumps = 0

    fun accept(point: TrackPoint, nowMs: Long): Result {
        if (point.accuracyMeters > maxAccuracyMeters) return Result.Rejected(Reason.PoorAccuracy)
        if (nowMs - point.timeMs > maxAgeMs) return Result.Rejected(Reason.Stale)

        val last = anchor ?: run {
            anchor = point
            return Result.Accepted(0.0)
        }

        val dtSec = (point.timeMs - last.timeMs) / 1000.0
        if (dtSec <= 0) return Result.Rejected(Reason.OutOfOrder)

        val d = last.distanceTo(point)
        val speedLimit = if (dtSec * 1000 > longGapMs) maxSustainedSpeedMps else maxSpeedMps
        if (d / dtSec > speedLimit) {
            if (++consecutiveJumps >= reanchorAfterJumps) {
                anchor = point
                consecutiveJumps = 0
                return Result.Accepted(0.0)
            }
            return Result.Rejected(Reason.Jump)
        }
        consecutiveJumps = 0

        val threshold = point.accuracyMeters.toDouble().coerceIn(minSegmentMeters, maxSegmentMeters)
        val reportsStationary = point.speedMps != null && point.speedMps < stationarySpeedMps
        if (d < threshold || (reportsStationary && d < maxSegmentMeters)) {
            return Result.Accepted(0.0)
        }

        anchor = point
        return Result.Accepted(d)
    }

    fun reset() {
        anchor = null
        consecutiveJumps = 0
    }
}
