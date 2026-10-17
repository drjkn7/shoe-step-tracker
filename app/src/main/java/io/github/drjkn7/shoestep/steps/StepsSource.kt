package io.github.drjkn7.shoestep.steps

/** Whether the app can read steps right now. */
enum class StepsAccess {
    /** Health Connect is ready and the user allowed reading steps. */
    READY,

    /** Health Connect is ready but reading steps has not been allowed. */
    NEEDS_PERMISSION,

    /** Health Connect must be installed or updated first (Android 13 and lower). */
    NEEDS_INSTALL,

    /** This phone can't provide steps right now (no Health Connect support, or it failed to answer). */
    UNAVAILABLE,
}

/** Result of asking for the steps taken between two instants. */
sealed interface StepsRead {
    data class Count(val steps: Long) : StepsRead

    /** Health Connect answered but has no step records for that time. */
    data object NoData : StepsRead

    /** The read did not work (no access, error, or the time is too far back); try again later. */
    data object Failed : StepsRead
}

/**
 * Where step counts come from. The real one is [HealthConnectStepsSource]; tests use fakes.
 * Implementations never throw (except for coroutine cancellation) and never log step data.
 */
interface StepsSource {
    suspend fun access(): StepsAccess

    /** Steps recorded from [startUtc] (inclusive) to [endUtc] (exclusive), UTC epoch millis. */
    suspend fun read(startUtc: Long, endUtc: Long): StepsRead
}
