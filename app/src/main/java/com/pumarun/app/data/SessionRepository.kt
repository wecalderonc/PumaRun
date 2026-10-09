package com.pumarun.app.data

import com.pumarun.app.domain.SessionState
import com.pumarun.app.domain.TrackSimplifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface SessionRepository {
    /** Returns the new row id, or null when there is nothing worth keeping. */
    suspend fun save(session: SessionState): Long?
    fun observeRuns(): Flow<List<SavedRun>>
    suspend fun get(id: Long): SavedRun?
    suspend fun delete(id: Long)
}

@Singleton
class RoomSessionRepository @Inject constructor(
    private val dao: RunDao,
) : SessionRepository {
    override suspend fun save(session: SessionState): Long? {
        if (session.elapsedMs <= 0 && session.distanceMeters <= 0 && session.track.all { it.isEmpty() }) return null
        return dao.insert(
            RunEntity(
                finishedAtEpochMs = System.currentTimeMillis(),
                distanceMeters = session.distanceMeters,
                elapsedMs = session.elapsedMs,
                avgPaceSecPerKm = session.avgPaceSecPerKm,
                goalMeters = session.config?.goalMeters,
                goalReached = session.goalReached,
                targetPaceSecPerKm = session.config?.targetPaceSecPerKm,
                goalTimeMs = session.goalTimeMs,
                paceAlertCount = session.paceAlertCount,
                track = TrackCodec.encode(session.track),
            ),
        )
    }

    override fun observeRuns(): Flow<List<SavedRun>> = dao.observeAll().map { rows -> rows.map { it.toSavedRun() } }

    override suspend fun get(id: Long): SavedRun? {
        val entity = dao.get(id) ?: return null
        return withContext(Dispatchers.Default) {
            entity.toSavedRun().let { it.copy(track = TrackSimplifier.simplify(it.track)) }
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
