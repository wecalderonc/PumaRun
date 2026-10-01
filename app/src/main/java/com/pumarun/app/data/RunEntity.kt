package com.pumarun.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val finishedAtEpochMs: Long,
    val distanceMeters: Double,
    val elapsedMs: Long,
    val avgPaceSecPerKm: Double?,
    val goalMeters: Double?,
    val goalReached: Boolean,
    val track: String,
) {
    fun toSavedRun() = SavedRun(
        id = id,
        finishedAtEpochMs = finishedAtEpochMs,
        distanceMeters = distanceMeters,
        elapsedMs = elapsedMs,
        avgPaceSecPerKm = avgPaceSecPerKm,
        goalMeters = goalMeters,
        goalReached = goalReached,
        track = TrackCodec.decode(track),
    )
}
