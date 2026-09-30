package com.pumaconcolor.run.data

import com.pumaconcolor.run.domain.SessionState
import javax.inject.Inject
import javax.inject.Singleton

/** Persistence for finished sessions. v1 keeps nothing; v2 will back this with Room. */
interface SessionRepository {
    suspend fun save(session: SessionState)
}

@Singleton
class NoOpSessionRepository @Inject constructor() : SessionRepository {
    override suspend fun save(session: SessionState) = Unit
}
