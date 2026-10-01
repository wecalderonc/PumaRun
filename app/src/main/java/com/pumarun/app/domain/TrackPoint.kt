package com.pumarun.app.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A GPS fix, decoupled from android.location.Location so domain logic is unit-testable. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    /** Monotonic timestamp (SystemClock.elapsedRealtime base), in ms. */
    val timeMs: Long,
    val speedMps: Float? = null,
) {
    fun distanceTo(other: TrackPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_008.8
    }
}
