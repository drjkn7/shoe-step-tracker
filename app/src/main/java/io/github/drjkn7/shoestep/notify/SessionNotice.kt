package io.github.drjkn7.shoestep.notify

import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.model.SessionState

/**
 * What the session notification shows (PNGs 13 and 14), worked out without Android so it is unit-tested
 * on the JVM. No steps: the app never reads Health Connect in the background.
 */
sealed interface SessionNotice {
    val sessionId: String
    val shoeName: String
    val zoneId: String

    /**
     * "Pair · running", "Started 07:42 …" and a timer of the walking time so far. [timerBaseUtc] is when
     * the timer would have started had there been no pauses, so it counts only time spent running.
     */
    data class Running(
        override val sessionId: String,
        override val shoeName: String,
        override val zoneId: String,
        val startUtc: Long,
        val timerBaseUtc: Long,
    ) : SessionNotice

    /** "Pair · paused", "Paused at 08:29 after 47 min." */
    data class Paused(
        override val sessionId: String,
        override val shoeName: String,
        override val zoneId: String,
        val pausedAtUtc: Long,
        val activeMillis: Long,
    ) : SessionNotice

    companion object {
        /**
         * The notice for [active] and its [segments], or null when there should be none (no session, it
         * has ended, or the Session notification setting is off).
         */
        fun from(active: ActiveSessionWithShoe?, segments: List<SegmentEntity>, enabled: Boolean): SessionNotice? {
            if (!enabled || active == null) return null
            val session = active.session
            val closed = segments.filter { it.endUtc != null }
            val closedMillis = closed.sumOf { (it.endUtc!! - it.startUtc).coerceAtLeast(0) }
            return when (session.state) {
                SessionState.RUNNING -> {
                    val openStart = segments.lastOrNull { it.endUtc == null }?.startUtc ?: session.startUtc
                    Running(session.id, active.shoeName, session.zoneId, session.startUtc, openStart - closedMillis)
                }
                SessionState.PAUSED -> {
                    val pausedAt = closed.maxOfOrNull { it.endUtc!! } ?: session.startUtc
                    Paused(session.id, active.shoeName, session.zoneId, pausedAt, closedMillis)
                }
                SessionState.ENDED -> null
            }
        }
    }
}
