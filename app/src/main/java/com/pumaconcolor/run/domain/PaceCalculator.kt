package com.pumaconcolor.run.domain

/**
 * Rolling-window pace from (active time, cumulative distance) samples.
 * Times are active (non-paused) session time so pauses never distort the window.
 */
class PaceCalculator(
    private val windowMs: Long = 30_000L,
    private val minWindowDistanceMeters: Double = 20.0,
) {
    private data class Sample(val timeMs: Long, val distanceMeters: Double)

    private val samples = ArrayDeque<Sample>()

    fun add(activeTimeMs: Long, cumulativeDistanceMeters: Double) {
        samples.addLast(Sample(activeTimeMs, cumulativeDistanceMeters))
        // Keep one sample older than the window so the span always covers the full window.
        while (samples.size > 2 && samples[1].timeMs <= activeTimeMs - windowMs) {
            samples.removeFirst()
        }
    }

    /** Current pace in seconds per km, or null if there is not enough recent movement. */
    fun currentPace(nowActiveMs: Long): Double? {
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val dist = last.distanceMeters - first.distanceMeters
        if (dist < minWindowDistanceMeters) return null
        val spanMs = maxOf(nowActiveMs, last.timeMs) - first.timeMs
        if (spanMs <= 0) return null
        return (spanMs / 1000.0) / (dist / 1000.0)
    }

    fun reset() = samples.clear()

    companion object {
        fun averagePace(elapsedMs: Long, distanceMeters: Double): Double? =
            if (distanceMeters >= 10.0 && elapsedMs > 0) (elapsedMs / 1000.0) / (distanceMeters / 1000.0)
            else null
    }
}
