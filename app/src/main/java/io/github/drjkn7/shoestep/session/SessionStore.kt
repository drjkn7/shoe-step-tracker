package io.github.drjkn7.shoestep.session

import androidx.room3.withWriteTransaction
import io.github.drjkn7.shoestep.data.db.ActiveSessionEntity
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.SessionWithSteps
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The storage [SessionManager] needs, kept small so the manager's logic can be unit-tested on the
 * JVM with an in-memory fake. Only SessionManager may call the write methods.
 */
interface SessionStore {
    /** Runs [block] atomically: if it throws, none of its writes are kept. */
    suspend fun <R> transaction(block: suspend () -> R): R

    suspend fun findShoe(id: String): ShoeEntity?
    suspend fun activeSessionId(): String?
    suspend fun session(id: String): SessionEntity?

    /** Oldest first. */
    suspend fun segments(sessionId: String): List<SegmentEntity>
    suspend fun unendedSessions(): List<SessionEntity>

    suspend fun insertSession(session: SessionEntity)
    suspend fun updateSession(session: SessionEntity)
    suspend fun insertSegment(segment: SegmentEntity)
    suspend fun updateSegment(segment: SegmentEntity)
    suspend fun setActive(sessionId: String)
    suspend fun clearActive()

    fun observeActive(): Flow<ActiveSessionWithShoe?>
    fun observeHistory(shoeId: String): Flow<List<SessionWithSteps>>
}

/** The real store: Room DAOs, with each [transaction] being one SQLite write transaction. */
class RoomSessionStore(private val db: ShoeStepDatabase) : SessionStore {
    private val shoes = db.shoeDao()
    private val sessions = db.sessionDao()
    private val active = db.activeSessionDao()

    override suspend fun <R> transaction(block: suspend () -> R): R = db.withWriteTransaction { block() }

    override suspend fun findShoe(id: String) = shoes.getById(id)
    override suspend fun activeSessionId() = active.getActiveSessionId()
    override suspend fun session(id: String) = sessions.getSession(id)
    override suspend fun segments(sessionId: String) = sessions.getSegments(sessionId)
    override suspend fun unendedSessions() = sessions.getUnendedSessions()

    override suspend fun insertSession(session: SessionEntity) = sessions.insertSession(session)
    override suspend fun updateSession(session: SessionEntity) {
        sessions.updateSession(session)
    }
    override suspend fun insertSegment(segment: SegmentEntity) = sessions.insertSegment(segment)
    override suspend fun updateSegment(segment: SegmentEntity) {
        sessions.updateSegment(segment)
    }
    override suspend fun setActive(sessionId: String) = active.setActive(ActiveSessionEntity(sessionId = sessionId))
    override suspend fun clearActive() = active.clear()

    override fun observeActive() = active.observeActiveSessionWithShoe()
    override fun observeHistory(shoeId: String) = sessions.observeHistory(shoeId)
}
