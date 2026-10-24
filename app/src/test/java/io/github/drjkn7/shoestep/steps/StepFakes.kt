package io.github.drjkn7.shoestep.steps

import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.model.StepsStatus

/** In-memory [StepStore] with the same guards as the real UPDATEs in StepsDao. */
class FakeStepStore : StepStore {
    val segments = linkedMapOf<String, SegmentEntity>()

    fun add(
        id: String,
        start: Long,
        end: Long?,
        steps: Long? = null,
        status: StepsStatus = StepsStatus.PENDING,
        sessionId: String = "s1",
        meters: Double? = null,
        distanceStatus: StepsStatus = StepsStatus.PENDING,
    ) {
        segments[id] = SegmentEntity(
            id, sessionId, start, end, steps, status, lastReadUtc = null,
            distanceMeters = meters, distanceStatus = distanceStatus,
        )
    }

    operator fun get(id: String): SegmentEntity = segments.getValue(id)

    /** Runs just before each save, to simulate another change landing mid-read. */
    var beforeSave: (() -> Unit)? = null

    override suspend fun unresolvedClosedSegments(limit: Int) = unresolved(limit) { it.stepsStatus }

    override suspend fun unresolvedClosedDistanceSegments(limit: Int) = unresolved(limit) { it.distanceStatus }

    private fun unresolved(limit: Int, status: (SegmentEntity) -> StepsStatus) = segments.values
        .filter { it.endUtc != null && status(it) in UNRESOLVED }
        .sortedWith(compareBy({ status(it) != StepsStatus.PENDING }, { it.endUtc }, { it.id }))
        .take(limit)

    override suspend fun segments(sessionId: String) =
        segments.values.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.startUtc }, { it.id }))

    override suspend fun saveSteps(
        segmentId: String,
        expectedEndUtc: Long,
        steps: Long?,
        status: StepsStatus,
        readAtUtc: Long,
    ): Boolean {
        beforeSave?.invoke()
        val current = segments[segmentId] ?: return false
        if (current.endUtc != expectedEndUtc || current.stepsStatus !in UNRESOLVED) return false
        segments[segmentId] = current.copy(steps = steps, stepsStatus = status, lastReadUtc = readAtUtc)
        return true
    }

    override suspend fun saveDistance(
        segmentId: String,
        expectedEndUtc: Long,
        meters: Double?,
        status: StepsStatus,
        readAtUtc: Long,
    ): Boolean {
        beforeSave?.invoke()
        val current = segments[segmentId] ?: return false
        if (current.endUtc != expectedEndUtc || current.distanceStatus !in UNRESOLVED) return false
        segments[segmentId] = current.copy(distanceMeters = meters, distanceStatus = status, distanceReadUtc = readAtUtc)
        return true
    }

    private companion object {
        val UNRESOLVED = setOf(StepsStatus.PENDING, StepsStatus.PROVISIONAL)
    }
}

/** [StepsSource] whose answers each test sets; records every read. */
class FakeStepsSource(
    var access: StepsAccess = StepsAccess.READY,
    /** Answer for a read of (start, end); defaults to no data. */
    var answer: (start: Long, end: Long) -> StepsRead = { _, _ -> StepsRead.NoData },
    /** Off by default, so the steps tests see no distance reads. */
    var distanceAllowed: Boolean = false,
    var distanceAnswer: (start: Long, end: Long) -> DistanceRead = { _, _ -> DistanceRead.NoData },
) : StepsSource {
    val reads = mutableListOf<Pair<Long, Long>>()
    val distanceReads = mutableListOf<Pair<Long, Long>>()

    override suspend fun access() = access

    override suspend fun read(startUtc: Long, endUtc: Long): StepsRead {
        reads += startUtc to endUtc
        return answer(startUtc, endUtc)
    }

    override suspend fun distanceAllowed() = distanceAllowed

    override suspend fun readDistance(startUtc: Long, endUtc: Long): DistanceRead {
        distanceReads += startUtc to endUtc
        return distanceAnswer(startUtc, endUtc)
    }
}
