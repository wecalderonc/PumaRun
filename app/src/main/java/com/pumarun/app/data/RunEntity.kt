package com.pumarun.app.data

import androidx.room.ColumnInfo
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
    val targetPaceSecPerKm: Double? = null,
    val goalTimeMs: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val paceAlertCount: Int = 0,
) {
    fun toSavedRun() = SavedRun(
        id = id,
        finishedAtEpochMs = finishedAtEpochMs,
        distanceMeters = distanceMeters,
        elapsedMs = elapsedMs,
        avgPaceSecPerKm = avgPaceSecPerKm,
        goalMeters = goalMeters,
        goalReached = goalReached,
        targetPaceSecPerKm = targetPaceSecPerKm,
        goalTimeMs = goalTimeMs,
        paceAlertCount = paceAlertCount,
        track = TrackCodec.decode(track),
    )
}

/** List row. The route blob stays out of this query so opening Tracks does not decode it. */
data class RunRow(
    val id: Long,
    val finishedAtEpochMs: Long,
    val distanceMeters: Double,
    val elapsedMs: Long,
    val avgPaceSecPerKm: Double?,
    val goalMeters: Double?,
    val goalReached: Boolean,
    val targetPaceSecPerKm: Double?,
    val goalTimeMs: Long?,
    val paceAlertCount: Int,
) {
    fun toSavedRun() = SavedRun(
        id = id,
        finishedAtEpochMs = finishedAtEpochMs,
        distanceMeters = distanceMeters,
        elapsedMs = elapsedMs,
        avgPaceSecPerKm = avgPaceSecPerKm,
        goalMeters = goalMeters,
        goalReached = goalReached,
        targetPaceSecPerKm = targetPaceSecPerKm,
        goalTimeMs = goalTimeMs,
        paceAlertCount = paceAlertCount,
    )
}
