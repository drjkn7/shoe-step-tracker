package io.github.drjkn7.shoestep.core

import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Source of the current time. Inject this instead of calling System.currentTimeMillis()
 * so that time-dependent logic (sessions, reconciliation) can be tested with a fake clock.
 */
interface Clock {
    /** Current instant as UTC epoch milliseconds. */
    fun nowUtcMillis(): Long

    /** Time zone the user is in right now; stored alongside timestamps for display. */
    fun zoneId(): ZoneId
}

/** The real device clock. */
object SystemWallClock : Clock {
    override fun nowUtcMillis(): Long = System.currentTimeMillis()

    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}

/** Milliseconds in a minute, for walking times. */
const val MILLIS_PER_MINUTE = 60_000L

/** The stored zone id as a zone; one that can't be read (a damaged row) falls back to UTC. */
fun zoneIdOrUtc(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (_: DateTimeException) {
    ZoneOffset.UTC
}
