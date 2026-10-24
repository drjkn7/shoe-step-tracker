package io.github.drjkn7.shoestep.steps

import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.model.StepsStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deferred reconciliation of steps and distance. Segments are saved PENDING (unknown) when a session
 * runs; this fills them in from the [StepsSource] while the app is open. Steps and distance each have
 * their own value and status, and follow the same rules:
 *
 * - A value is saved as PROVISIONAL, and re-checked on later runs because wearables sync late.
 * - Once a segment ended at least [SETTLE_MILLIS] ago, its value becomes FINAL and stops changing.
 * - No data after the settle time: UNAVAILABLE (left out of totals), or the last provisional
 *   value becomes FINAL so a number the user already saw is never lost.
 * - Read errors leave the segment for the next run, except for segments older than
 *   [HISTORY_LIMIT_MILLIS], which Health Connect may refuse to read at all: those settle too.
 * - Without access nothing changes. Steps need the steps permission, distance the distance
 *   permission; refusing one never holds the other back.
 *
 * Each run reads Health Connect at most [MAX_PER_RUN] times in all. When both are allowed, steps get
 * up to half and distance the rest, so a backfill of older sessions spreads over several runs and
 * neither kind can hold the other back.
 *
 * Only this class writes steps and distances. It never changes session state (SessionManager's job).
 */
class StepReconciler(
    private val store: StepStore,
    private val source: StepsSource,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Fills in or re-checks closed segments. Safe to call often; calls run one at a time. Returns values saved. */
    suspend fun reconcile(): Int = mutex.withLock {
        var saved = 0
        var reads = 0
        val distanceAllowed = source.distanceAllowed()
        if (source.access() == StepsAccess.READY) {
            // With distance allowed, steps get half the reads so neither can crowd out the other
            // (provisional counts are re-checked every run for a day); whatever steps leave, distance may use.
            for (segment in store.unresolvedClosedSegments(if (distanceAllowed) MAX_PER_RUN / 2 else MAX_PER_RUN)) {
                val endUtc = segment.endUtc ?: continue
                if (endUtc > segment.startUtc) reads++
                val read = readOrZero(segment.startUtc, endUtc)
                val now = clock.nowUtcMillis()
                val outcome = decide(segment.steps, endUtc, read, now) ?: continue
                if (store.saveSteps(segment.id, endUtc, outcome.steps, outcome.status, now)) saved++
            }
        }
        val budget = MAX_PER_RUN - reads
        if (budget > 0 && distanceAllowed) {
            for (segment in store.unresolvedClosedDistanceSegments(budget)) {
                val endUtc = segment.endUtc ?: continue
                val read = readDistanceOrZero(segment.startUtc, endUtc)
                val now = clock.nowUtcMillis()
                val outcome = decideDistance(segment.distanceMeters, endUtc, read, now) ?: continue
                if (store.saveDistance(segment.id, endUtc, outcome.meters, outcome.status, now)) saved++
            }
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

    /** A zero-length (or clock-reversed) time span has no distance; no need to ask. */
    private suspend fun readDistanceOrZero(startUtc: Long, endUtc: Long): DistanceRead =
        if (endUtc <= startUtc) DistanceRead.Meters(0.0) else source.readDistance(startUtc, endUtc)

    data class Outcome(val steps: Long?, val status: StepsStatus)

    data class DistanceOutcome(val meters: Double?, val status: StepsStatus)

    companion object {
        /** How long after a segment ends its value may still change (product owner: one day). */
        const val SETTLE_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Health Connect lets apps read about 30 days before a permission was first granted
         * (official "Read data" guide); older reads fail.
         */
        const val HISTORY_LIMIT_MILLIS = 30L * 24 * 60 * 60 * 1000

        /**
         * Upper bound on Health Connect reads per run, steps and distance together, to stay well inside
         * its rate limits (the official guide states limits exist but gives no numbers).
         */
        const val MAX_PER_RUN = 100

        /**
         * What to save for a closed segment that currently has [previousSteps] (null if PENDING),
         * ended at [endUtc], given [read] at [nowUtc]. Null = save nothing, try again next run.
         * A clock set backwards (now before the end) counts as "not settled yet".
         */
        fun decide(previousSteps: Long?, endUtc: Long, read: StepsRead, nowUtc: Long): Outcome? {
            val answer = when (read) {
                is StepsRead.Count -> Answer.Value(read.steps)
                StepsRead.NoData -> Answer.None
                StepsRead.Failed -> Answer.Failed
            }
            return rule(previousSteps, endUtc, answer, nowUtc)?.let { (steps, status) -> Outcome(steps, status) }
        }

        /** The same rules as [decide], for distance in metres. */
        fun decideDistance(previousMeters: Double?, endUtc: Long, read: DistanceRead, nowUtc: Long): DistanceOutcome? {
            val answer = when (read) {
                is DistanceRead.Meters -> Answer.Value(read.meters)
                DistanceRead.NoData -> Answer.None
                DistanceRead.Failed -> Answer.Failed
            }
            return rule(previousMeters, endUtc, answer, nowUtc)?.let { (meters, status) -> DistanceOutcome(meters, status) }
        }

        /** What Health Connect said, the same way for steps and distance. */
        private sealed interface Answer<out T> {
            data class Value<T>(val value: T) : Answer<T>
            data object None : Answer<Nothing>
            data object Failed : Answer<Nothing>
        }

        /** The one set of rules behind [decide] and [decideDistance]. */
        private fun <T : Any> rule(previous: T?, endUtc: Long, answer: Answer<T>, nowUtc: Long): Pair<T?, StepsStatus>? {
            val age = nowUtc - endUtc
            val settled = age >= SETTLE_MILLIS
            return when (answer) {
                is Answer.Value -> answer.value to if (settled) StepsStatus.FINAL else StepsStatus.PROVISIONAL
                Answer.None -> if (settled) settle(previous) else null
                Answer.Failed -> if (age >= HISTORY_LIMIT_MILLIS) settle(previous) else null
            }
        }

        /** A value the user already saw becomes final; with none, the segment is left out (UNAVAILABLE). */
        private fun <T : Any> settle(previous: T?): Pair<T?, StepsStatus> =
            if (previous != null) previous to StepsStatus.FINAL else null to StepsStatus.UNAVAILABLE
    }
}
