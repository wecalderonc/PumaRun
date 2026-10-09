package com.pumarun.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Query(
        "SELECT id, finishedAtEpochMs, distanceMeters, elapsedMs, avgPaceSecPerKm, goalMeters, goalReached, " +
            "targetPaceSecPerKm, goalTimeMs, paceAlertCount FROM runs ORDER BY finishedAtEpochMs DESC",
    )
    fun observeAll(): Flow<List<RunRow>>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: Long): RunEntity?

    @Insert
    suspend fun insert(run: RunEntity): Long

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: Long)
}
