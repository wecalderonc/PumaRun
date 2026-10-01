package com.pumarun.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY finishedAtEpochMs DESC")
    fun observeAll(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: Long): RunEntity?

    @Insert
    suspend fun insert(run: RunEntity)

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: Long)
}
