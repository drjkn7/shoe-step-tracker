package io.github.drjkn7.shoestep.steps

import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.model.StepsStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deferred step reconciliation. Segments are saved PENDING (steps unknown) when a session runs;
 * this fills them in from the [StepsSource] while the app is open:
 *
 * - A count is saved as PROVISIONAL, and re-checked on later runs because wearables sync late.
 * - Once a segment ended at least [SETTLE_MILLIS] ago, its count becomes FINAL and stops changing.
 * - No data after the settle time: UNAVAILABLE (left out of totals), or the last provisional
 *   count becomes FINAL so a number the user already saw is never lost.
 * - Read errors leave the segment for the next run, except for segments older than
 *   [HISTORY_LIMIT_MILLIS], which Health Connect may refuse to read at all: those settle too.
 * - Without access (no Health Connect or no permission) nothing changes: "counted later".
 *
 * Only this class writes step counts. It never changes session state (SessionManager's job).
 */
class StepReconciler(
    private val store: StepStore,
    private val source: StepsSource,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Fills in or re-checks closed segments. Safe to call often; calls run one at a time. Returns rows saved. */
    suspend fun reconcile(): Int = mutex.withLock {
        if (source.access() != StepsAccess.READY) return 0
        var saved = 0
        for (segment in store.unresolvedClosedSegments(MAX_PER_RUN)) {
            val endUtc = segment.endUtc ?: continue
            val read = readOrZero(segment.startUtc, endUtc)
            val now = clock.nowUtcMillis()
            val outcome = decide(segment.steps, endUtc, read, now) ?: continue
            if (store.saveSteps(segment.id, endUtc, outcome.steps, outcome.status, now)) saved++
        }
        saved
    }

    /**
     * Approximate steps for a running or paused session, for display only (nothing is saved).
     * Segments with a saved count use it; the others are read live up to now.
     * Null when steps can't be read right now (the UI shows "counted later").
     */
    suspend fun stepsSoFar(sessionId: String): Long? {
        if (source.access() != StepsAccess.READY) return null
        val now = clock.nowUtcMillis()
        var total = 0L
        for (segment in store.segments(sessionId)) {
            val known = segment.steps
            total += when {
                known != null -> known
                segment.stepsStatus == StepsStatus.UNAVAILABLE -> 0
                else -> when (val read = readOrZero(segment.startUtc, segment.endUtc ?: now)) {
                    is StepsRead.Count -> read.steps
                    StepsRead.NoData -> 0
                    StepsRead.Failed -> return null
                }
            }
        }
        return total
    }

    /** A zero-length (or clock-reversed) time span has no steps; no need to ask. */
    private suspend fun readOrZero(startUtc: Long, endUtc: Long): StepsRead =
        if (endUtc <= startUtc) StepsRead.Count(0) else source.read(startUtc, endUtc)

    data class Outcome(val steps: Long?, val status: StepsStatus)

    companion object {
        /** How long after a segment ends its count may still change (product owner: one day). */
        const val SETTLE_MILLIS = 24L * 60 * 60 * 1000

        /** Health Connect only lets apps read about 30 days before permission was first granted. */
        const val HISTORY_LIMIT_MILLIS = 30L * 24 * 60 * 60 * 1000

        /** Upper bound on reads per run, to stay well inside Health Connect's rate limits. */
        const val MAX_PER_RUN = 100

        /**
         * What to save for a closed segment that currently has [previousSteps] (null if PENDING),
         * ended at [endUtc], given [read] at [nowUtc]. Null = save nothing, try again next run.
         * A clock set backwards (now before the end) counts as "not settled yet".
         */
        fun decide(previousSteps: Long?, endUtc: Long, read: StepsRead, nowUtc: Long): Outcome? {
            val age = nowUtc - endUtc
            val settled = age >= SETTLE_MILLIS
            return when (read) {
                is StepsRead.Count ->
                    Outcome(read.steps, if (settled) StepsStatus.FINAL else StepsStatus.PROVISIONAL)
                StepsRead.NoData -> if (settled) settle(previousSteps) else null
                StepsRead.Failed -> if (age >= HISTORY_LIMIT_MILLIS) settle(previousSteps) else null
            }
        }

        private fun settle(previousSteps: Long?) =
            if (previousSteps != null) Outcome(previousSteps, StepsStatus.FINAL) else Outcome(null, StepsStatus.UNAVAILABLE)
    }
}
