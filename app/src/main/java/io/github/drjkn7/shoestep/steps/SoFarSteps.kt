package io.github.drjkn7.shoestep.steps

import io.github.drjkn7.shoestep.data.model.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.TimeSource

/** What a running session shows for steps. */
sealed interface SoFarSteps {
    /**
     * Approximate steps since the session started ("About 1,500 steps so far"), and the distance in
     * metres when it can be read ("About 1.1 km"; null = no distance line).
     */
    data class Count(val steps: Long, val distanceMeters: Double? = null) : SoFarSteps

    /** Steps can't be read right now ("Steps counted later"). */
    data object Later : SoFarSteps
}

/** How often a visible "so far" count is re-read from Health Connect. */
const val SO_FAR_REFRESH_MILLIS = 60_000L

/**
 * Showing a screen asks twice in a row (collection starts, then the screen's resume refresh): a request
 * for the same session and state this soon after the last one is skipped, so it is read once.
 */
private const val SO_FAR_REPEAT_MILLIS = 1_000L

/**
 * Live "so far" steps for whichever session [sessionKey] names (id and state; null = none). Re-read on
 * each [refresh] emission, on pause/resume, and every [periodMillis] while collected and running; a
 * paused session can't gain steps, so it is read once. Display only: nothing is saved (Home card and
 * the pair's page).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun soFarSteps(
    sessionKey: Flow<Pair<String, SessionState>?>,
    refresh: Flow<Int>,
    reconciler: StepReconciler,
    periodMillis: Long = SO_FAR_REFRESH_MILLIS,
): Flow<SoFarSteps?> = combine(sessionKey, refresh) { key, _ -> key }
    .skipQuickRepeats(SO_FAR_REPEAT_MILLIS)
    .flatMapLatest { key ->
        if (key == null) {
            flowOf<SoFarSteps?>(null)
        } else {
            val (sessionId, state) = key
            flow<SoFarSteps?> {
                do {
                    val steps = reconciler.stepsSoFar(sessionId)
                    emit(if (steps == null) SoFarSteps.Later else SoFarSteps.Count(steps, reconciler.distanceSoFar(sessionId)))
                    if (state == SessionState.PAUSED) break
                    delay(periodMillis)
                } while (true)
            }
        }
    }

/** Drops a value equal to the previous one that arrives within [millis] of it; anything else passes at once. */
private fun <T> Flow<T>.skipQuickRepeats(millis: Long): Flow<T> = flow {
    var last: Any? = NONE
    var lastAt: TimeSource.Monotonic.ValueTimeMark? = null
    collect { value ->
        val repeat = value == last && lastAt?.let { it.elapsedNow().inWholeMilliseconds < millis } == true
        last = value
        lastAt = TimeSource.Monotonic.markNow()
        if (!repeat) emit(value)
    }
}

private object NONE
