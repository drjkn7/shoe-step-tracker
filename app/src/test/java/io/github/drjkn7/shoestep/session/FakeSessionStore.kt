package io.github.drjkn7.shoestep.session

import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.SessionWithSteps
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.data.model.StepsStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [SessionStore] for JVM unit tests. Transactions really roll back: the maps are copied
 * before the block runs and put back if it throws. Set [failOn] to make one write method throw.
 */
class FakeSessionStore : SessionStore {
    val shoes = linkedMapOf<String, ShoeEntity>()
    val sessions = linkedMapOf<String, SessionEntity>()
    val segments = linkedMapOf<String, SegmentEntity>()
    var activeId: String? = null

    /** Name of a write method that should throw, to test rollback. */
    var failOn: String? = null
    var transactionCount = 0
        private set

    private val version = MutableStateFlow(0)

    fun addShoe(id: String, archived: Boolean = false) {
        shoes[id] = ShoeEntity(id, null, "Shoe $id", "", "", ShoeType.WALKING, null, "EUR", null, 0, archived, 0)
    }

    override suspend fun <R> transaction(block: suspend () -> R): R {
        transactionCount++
        val saved = Triple(LinkedHashMap(sessions), LinkedHashMap(segments), activeId)
        try {
            return block().also { version.value++ }
        } catch (e: Throwable) {
            sessions.clear(); sessions.putAll(saved.first)
            segments.clear(); segments.putAll(saved.second)
            activeId = saved.third
            throw e
        }
    }

    override suspend fun findShoe(id: String) = shoes[id]
    override suspend fun activeSessionId() = activeId
    override suspend fun session(id: String) = sessions[id]
    override suspend fun segments(sessionId: String) =
        segments.values.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.startUtc }, { it.id }))
    override suspend fun unendedSessions() = sessions.values.filter { it.state != SessionState.ENDED }

    override suspend fun insertSession(session: SessionEntity) {
        check("insertSession")
        require(session.id !in sessions) { "duplicate session id" }
        sessions[session.id] = session
    }

    override suspend fun updateSession(session: SessionEntity) {
        check("updateSession")
        if (session.id in sessions) sessions[session.id] = session
    }

    override suspend fun insertSegment(segment: SegmentEntity) {
        check("insertSegment")
        require(segment.id !in segments) { "duplicate segment id" }
        segments[segment.id] = segment
    }

    override suspend fun updateSegment(segment: SegmentEntity) {
        check("updateSegment")
        if (segment.id in segments) segments[segment.id] = segment
    }

    override suspend fun setActive(sessionId: String) {
        check("setActive")
        activeId = sessionId
    }

    override suspend fun clearActive() {
        check("clearActive")
        activeId = null
    }

    override fun observeActive(): Flow<ActiveSessionWithShoe?> = version.map {
        val session = activeId?.let { sessions[it] } ?: return@map null
        val shoe = shoes.getValue(session.shoeId)
        ActiveSessionWithShoe(session, shoe.name, shoe.accentColor)
    }

    override fun observeHistory(shoeId: String): Flow<List<SessionWithSteps>> = version.map {
        sessions.values.filter { it.shoeId == shoeId }.sortedByDescending { it.startUtc }.map { s ->
            val own = segments.values.filter { it.sessionId == s.id }
            SessionWithSteps(
                session = s,
                totalSteps = own.sumOf { it.steps ?: 0 },
                activeMillis = own.sumOf { g -> g.endUtc?.let { maxOf(0, it - g.startUtc) } ?: 0 },
                segmentCount = own.size,
                hasUnfinalSteps = own.any {
                    it.endUtc != null && it.stepsStatus in setOf(StepsStatus.PENDING, StepsStatus.PROVISIONAL)
                },
                hasPendingSteps = own.any { it.endUtc != null && it.stepsStatus == StepsStatus.PENDING },
            )
        }
    }

    private fun check(method: String) {
        if (failOn == method) throw IllegalStateException("simulated failure in $method")
    }
}
