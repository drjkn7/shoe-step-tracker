package io.github.drjkn7.shoestep.steps

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** What a running session shows for steps. */
sealed interface SoFarSteps {
    /** Approximate steps since the session started ("About 1,500 steps so far"). */
    data class Count(val steps: Long) : SoFarSteps

    /** Steps can't be read right now ("Steps counted later"). */
    data object Later : SoFarSteps
}

/** How often a visible "so far" count is re-read from Health Connect. */
const val SO_FAR_REFRESH_MILLIS = 60_000L

/**
 * Live "so far" steps for whichever session [sessionKey] names (null = none; the key also changes on
 * pause/resume so the count is re-read). Re-read on each [refresh] emission and every
 * [periodMillis] while collected. Display only: nothing is saved (Home card and the pair's page).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun soFarSteps(
    sessionKey: Flow<Pair<String, Any?>?>,
    refresh: Flow<Int>,
    reconciler: StepReconciler,
    periodMillis: Long = SO_FAR_REFRESH_MILLIS,
): Flow<SoFarSteps?> = combine(sessionKey, refresh) { key, _ -> key?.first }
    .flatMapLatest { sessionId ->
        if (sessionId == null) {
            flowOf<SoFarSteps?>(null)
        } else {
            flow<SoFarSteps?> {
                while (true) {
                    emit(reconciler.stepsSoFar(sessionId)?.let { SoFarSteps.Count(it) } ?: SoFarSteps.Later)
                    delay(periodMillis)
                }
            }
        }
    }
