package com.pumarun.app

import com.pumarun.app.domain.TrackPoint

/** Builds points moving due north; 1 degree of latitude is about 111,195 m. */
object TestPoints {
    private const val METERS_PER_DEG_LAT = 111_195.08

    fun north(
        meters: Double,
        timeMs: Long,
        accuracy: Float = 5f,
        speed: Float? = null,
        originLat: Double = 19.4326,
        originLon: Double = -99.1332,
    ) = TrackPoint(
        latitude = originLat + meters / METERS_PER_DEG_LAT,
        longitude = originLon,
        accuracyMeters = accuracy,
        timeMs = timeMs,
        speedMps = speed,
    )
}
