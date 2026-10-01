package com.pumarun.app.data

import com.pumarun.app.domain.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface SessionRepository {
    suspend fun save(session: SessionState)
    fun observeRuns(): Flow<List<SavedRun>>
    suspend fun get(id: Long): SavedRun?
    suspend fun delete(id: Long)
}

@Singleton
class RoomSessionRepository @Inject constructor(
    private val dao: RunDao,
) : SessionRepository {
    override suspend fun save(session: SessionState) {
        if (session.elapsedMs <= 0 && session.distanceMeters <= 0 && session.track.all { it.isEmpty() }) return
        dao.insert(
            RunEntity(
                finishedAtEpochMs = System.currentTimeMillis(),
                distanceMeters = session.distanceMeters,
                elapsedMs = session.elapsedMs,
                avgPaceSecPerKm = session.avgPaceSecPerKm,
                goalMeters = session.config?.goalMeters,
                goalReached = session.goalReached,
                track = TrackCodec.encode(session.track),
            ),
        )
    }

    override fun observeRuns(): Flow<List<SavedRun>> = dao.observeAll().map { rows -> rows.map { it.toSavedRun() } }

    override suspend fun get(id: Long): SavedRun? = dao.get(id)?.toSavedRun()

    override suspend fun delete(id: Long) = dao.delete(id)
}
