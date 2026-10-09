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
    val targetPaceSecPerKm: Double? = null,
    val goalTimeMs: Long? = null,
    val paceAlertCount: Int = 0,
    val track: List<List<LatLon>> = emptyList(),
)
