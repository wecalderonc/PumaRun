package com.pumarun.app.data

import com.pumarun.app.domain.LatLon

data class SavedRun(
    val id: Long,
    val finishedAtEpochMs: Long,
    val distanceMeters: Double,
    val elapsedMs: Long,
    val avgPaceSecPerKm: Double?,
    val goalMeters: Double?,
    val goalReached: Boolean,
    val track: List<List<LatLon>>,
)
