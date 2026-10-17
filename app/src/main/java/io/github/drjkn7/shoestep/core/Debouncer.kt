package io.github.drjkn7.shoestep.core

/**
 * Ignores repeats of the same trigger that arrive within [windowMillis] (2 seconds by default).
 *
 * Phase 6 uses this for NFC: holding the phone against a tag can deliver the same tag several
 * times in a row, and without this guard one tap could start a session and immediately pause it.
 * Each key (for example a tag's UUID) has its own timer, so tapping pair A and then pair B straight
 * away is not blocked. Keys are never logged. Safe to call from several threads.
 */
class Debouncer(
    private val clock: Clock,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    /** When each key was last seen, accepted or not. */
    private val lastSeen = HashMap<String, Long>()

    /** True if this event for [key] should be acted on; false if it is a repeat to ignore. */
    @Synchronized
    fun accept(key: String): Boolean {
        val now = clock.nowUtcMillis()
        val previous = lastSeen[key]

        // Accept if this tag is new, if 2 seconds have passed, or if the clock jumped backwards
        // (then we can't tell how long it's been, and one extra action beats blocking the tag).
        val accepted = previous == null || now < previous || now - previous >= windowMillis
        // Every read restarts the timer, even ignored ones: a phone resting on a tag acts once,
        // and the tag only works again after the phone has been away from it for 2 seconds.
        lastSeen[key] = now
        return accepted
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 2_000L
    }
}
