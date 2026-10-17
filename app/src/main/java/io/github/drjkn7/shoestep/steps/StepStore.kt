package io.github.drjkn7.shoestep.steps

import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.data.model.StepsStatus

/** The storage StepReconciler needs; a small interface so the rules can be unit-tested on the JVM. */
interface StepStore {
    /** Closed PENDING (first) or PROVISIONAL segments, oldest first within each, at most [limit]. */
    suspend fun unresolvedClosedSegments(limit: Int): List<SegmentEntity>

    suspend fun segments(sessionId: String): List<SegmentEntity>

    /**
     * Saves a result if the segment still ends at [expectedEndUtc] and is still PENDING or
     * PROVISIONAL. Returns false (and changes nothing) otherwise.
     */
    suspend fun saveSteps(
        segmentId: String,
        expectedEndUtc: Long,
        steps: Long?,
        status: StepsStatus,
        readAtUtc: Long,
    ): Boolean
}

class RoomStepStore(db: ShoeStepDatabase) : StepStore {
    private val dao = db.stepsDao()

    override suspend fun unresolvedClosedSegments(limit: Int) = dao.getUnresolvedClosedSegments(limit)

    override suspend fun segments(sessionId: String) = dao.getSegments(sessionId)

    override suspend fun saveSteps(
        segmentId: String,
        expectedEndUtc: Long,
        steps: Long?,
        status: StepsStatus,
        readAtUtc: Long,
    ): Boolean = dao.saveSteps(segmentId, expectedEndUtc, steps, status, readAtUtc) == 1
}
