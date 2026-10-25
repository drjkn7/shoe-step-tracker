package io.github.drjkn7.shoestep.session

import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.SessionWithShoeName
import io.github.drjkn7.shoestep.data.db.SessionWithSteps
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.StepsStatus
import io.github.drjkn7.shoestep.steps.StepReconciler
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a session command did. Repeating a command never changes anything twice. */
sealed interface SessionResult {
    /** The session state changed. */
    data object Done : SessionResult

    /** Nothing to do: already in that state, or the request was for a session that is no longer active. */
    data object NoChange : SessionResult

    /** Another pair has an active session; the user must confirm a switch ([SessionManager.switchTo]). */
    data class NeedsSwitch(val activeShoeId: String) : SessionResult

    /** The shoe does not exist or is archived, so it can't start a session. */
    data object ShoeUnavailable : SessionResult
}

/** Why a past session's times can't be saved (checked before looking for overlaps). */
enum class PastTimeProblem { END_NOT_AFTER_START, IN_FUTURE, TOO_OLD }

/** What [SessionManager.addPast] did. */
sealed interface AddPastResult {
    data class Added(val sessionId: String) : AddPastResult

    data class Invalid(val problem: PastTimeProblem) : AddPastResult

    /** Shares time with [other] (any pair, including the session running now); nothing was saved. */
    data class Overlaps(val other: SessionWithShoeName) : AddPastResult

    /** The shoe does not exist or is archived. */
    data object ShoeUnavailable : AddPastResult
}

/**
 * The ONLY code that changes session state (Idle → Running ⇄ Paused → Ended, plus Switch).
 *
 * - Each command is one database transaction, and commands run one at a time.
 * - Commands are idempotent: pause/resume/end name the session they mean, so a repeated or stale
 *   request (double tap, old screen) does nothing instead of acting on a different session.
 * - Nothing is kept in memory: the database is the state, so a session survives the app being
 *   killed or the phone restarting. [restore] repairs anything inconsistent once per launch.
 * - Times never go backwards inside a session: if the phone's clock jumps back, a segment ends
 *   at its own start (zero length) rather than being saved with a negative duration.
 */
class SessionManager(
    private val store: SessionStore,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    private var restored = false

    /** The running or paused session, or null when idle. */
    val activeSession: Flow<ActiveSessionWithShoe?> = store.observeActive().onStart { restore() }

    fun observeHistory(shoeId: String): Flow<List<SessionWithSteps>> = store.observeHistory(shoeId)

    /** For the Add past session form, to warn while the times are chosen. [addPast] checks again. */
    suspend fun firstOverlapping(startUtc: Long, endUtc: Long): SessionWithShoeName? = store.firstOverlapping(startUtc, endUtc)

    /** Starts a session for [shoeId] if nothing is active. Never ends another pair's session. */
    suspend fun start(shoeId: String): SessionResult = command { now ->
        if (!canStart(shoeId)) return@command SessionResult.ShoeUnavailable
        val current = activeSession()
        when {
            current == null -> {
                begin(shoeId, now)
                SessionResult.Done
            }
            current.shoeId == shoeId -> SessionResult.NoChange
            else -> SessionResult.NeedsSwitch(current.shoeId)
        }
    }

    /** Ends any other pair's session (reason SWITCH) and starts one for [shoeId], atomically. */
    suspend fun switchTo(shoeId: String): SessionResult = command { now ->
        if (!canStart(shoeId)) return@command SessionResult.ShoeUnavailable
        val current = activeSession()
        when {
            current == null -> begin(shoeId, now)
            current.shoeId == shoeId -> return@command SessionResult.NoChange
            // The new session starts exactly where the old one ended, so the two never overlap.
            else -> begin(shoeId, finish(current, EndReason.SWITCH, now))
        }
        SessionResult.Done
    }

    suspend fun pause(sessionId: String): SessionResult = command { now ->
        val session = activeSession()?.takeIf { it.id == sessionId && it.state == SessionState.RUNNING }
            ?: return@command SessionResult.NoChange
        closeOpenSegments(session.id, now)
        store.updateSession(session.copy(state = SessionState.PAUSED))
        SessionResult.Done
    }

    suspend fun resume(sessionId: String): SessionResult = command { now ->
        val session = activeSession()?.takeIf { it.id == sessionId && it.state == SessionState.PAUSED }
            ?: return@command SessionResult.NoChange
        openSegment(session.id, notBefore(session, now))
        store.updateSession(session.copy(state = SessionState.RUNNING))
        SessionResult.Done
    }

    suspend fun end(sessionId: String): SessionResult = command { now ->
        val session = activeSession()?.takeIf { it.id == sessionId } ?: return@command SessionResult.NoChange
        finish(session, EndReason.USER, now)
        SessionResult.Done
    }

    /** Ends [shoeId]'s session if it has the active one. Called before archiving or deleting a shoe. */
    suspend fun endForShoe(shoeId: String): SessionResult = command { now ->
        val session = activeSession()?.takeIf { it.shoeId == shoeId } ?: return@command SessionResult.NoChange
        finish(session, EndReason.USER, now)
        SessionResult.Done
    }

    /**
     * Saves an ended session from [startUtc] to [endUtc] for a forgotten tap, marked "added later", with
     * one segment left PENDING for StepReconciler to fill in like any other. Refused if the times break
     * [pastTimeProblem] or share any time with another session of any pair, the active one included.
     * Repeating the same request finds the first one as an overlap, so it is never saved twice.
     */
    suspend fun addPast(shoeId: String, startUtc: Long, endUtc: Long): AddPastResult = command { now ->
        if (!canStart(shoeId)) return@command AddPastResult.ShoeUnavailable
        pastTimeProblem(startUtc, endUtc, now)?.let { return@command AddPastResult.Invalid(it) }
        store.firstOverlapping(startUtc, endUtc)?.let { return@command AddPastResult.Overlaps(it) }
        AddPastResult.Added(insertPast(shoeId, startUtc, endUtc))
    }

    /**
     * Archives or unarchives [shoeId]. Archiving ends its session first, in the same transaction, so a
     * tag tap can never start a session in between (an archived pair can't have one).
     */
    suspend fun setArchived(shoeId: String, archived: Boolean): SessionResult = command { now ->
        if (archived) activeSession()?.takeIf { it.shoeId == shoeId }?.let { finish(it, EndReason.USER, now) }
        if (store.setShoeArchived(shoeId, archived)) SessionResult.Done else SessionResult.ShoeUnavailable
    }

    /** Ends [shoeId]'s session and deletes the pair (its sessions go with it), in one transaction. */
    suspend fun deleteShoe(shoeId: String): SessionResult = command { now ->
        activeSession()?.takeIf { it.shoeId == shoeId }?.let { finish(it, EndReason.USER, now) }
        if (store.deleteShoe(shoeId)) SessionResult.Done else SessionResult.ShoeUnavailable
    }

    /** Deletes [sessionId] only if it was added later; sessions recorded live can't be deleted. */
    suspend fun deleteAddedLater(sessionId: String): SessionResult = command {
        if (store.deleteAddedLater(sessionId)) SessionResult.Done else SessionResult.NoChange
    }

    /**
     * Once per launch, makes the stored state consistent. With transactions this should never find
     * anything, but if it does it prefers keeping data over inventing walking time:
     * - an active pointer to an ended session is cleared;
     * - a running session with no open segment becomes paused; a paused one with an open segment
     *   becomes running (the open segment is real walking time already recorded);
     * - any other unended session is ended at the last time it was known to be in use.
     */
    suspend fun restore() {
        mutex.withLock { restoreLocked() }
    }

    private suspend fun restoreLocked() {
        if (restored) return
        store.transaction {
            var active = store.activeSessionId()?.let { store.session(it) }
            if (active?.state == SessionState.ENDED) {
                store.clearActive()
                active = null
            }
            if (active == null && store.activeSessionId() != null) store.clearActive()

            for (orphan in store.unendedSessions()) {
                if (orphan.id == active?.id) continue
                val segments = store.segments(orphan.id)
                val lastKnown = maxOf(orphan.startUtc, segments.maxOfOrNull { maxOf(it.startUtc, it.endUtc ?: 0) } ?: 0)
                finish(orphan, EndReason.USER, lastKnown, clearActive = false)
            }

            if (active != null) {
                val hasOpen = store.segments(active.id).any { it.endUtc == null }
                when {
                    active.state == SessionState.RUNNING && !hasOpen ->
                        store.updateSession(active.copy(state = SessionState.PAUSED))
                    active.state == SessionState.PAUSED && hasOpen ->
                        store.updateSession(active.copy(state = SessionState.RUNNING))
                    else -> Unit
                }
            }
        }
        restored = true
    }

    private suspend fun <R> command(block: suspend (now: Long) -> R): R =
        mutex.withLock {
            restoreLocked()
            store.transaction { block(clock.nowUtcMillis()) }
        }

    private suspend fun canStart(shoeId: String): Boolean = store.findShoe(shoeId)?.archived == false

    private suspend fun activeSession(): SessionEntity? =
        store.activeSessionId()?.let { store.session(it) }?.takeIf { it.state != SessionState.ENDED }

    private suspend fun begin(shoeId: String, at: Long) {
        val session = SessionEntity(
            id = newId(),
            shoeId = shoeId,
            startUtc = at,
            endUtc = null,
            zoneId = clock.zoneId().id,
            state = SessionState.RUNNING,
            endReason = null,
        )
        store.insertSession(session)
        openSegment(session.id, at)
        store.setActive(session.id)
    }

    private suspend fun insertPast(shoeId: String, startUtc: Long, endUtc: Long): String {
        val session = SessionEntity(
            id = newId(),
            shoeId = shoeId,
            startUtc = startUtc,
            endUtc = endUtc,
            zoneId = clock.zoneId().id,
            state = SessionState.ENDED,
            endReason = EndReason.USER,
            addedLater = true,
        )
        store.insertSession(session)
        openSegment(session.id, startUtc)
        closeOpenSegments(session.id, endUtc)
        return session.id
    }

    private suspend fun openSegment(sessionId: String, at: Long) {
        store.insertSegment(
            SegmentEntity(
                id = newId(),
                sessionId = sessionId,
                startUtc = at,
                endUtc = null,
                steps = null,
                stepsStatus = StepsStatus.PENDING,
                lastReadUtc = null,
            ),
        )
    }

    /** Closes every open segment at [now], or at its own start if the clock went backwards. */
    private suspend fun closeOpenSegments(sessionId: String, now: Long) {
        store.segments(sessionId).filter { it.endUtc == null }.forEach {
            store.updateSegment(it.copy(endUtc = maxOf(now, it.startUtc)))
        }
    }

    /** Ends [session] and returns its end time, which is never before anything already recorded. */
    private suspend fun finish(
        session: SessionEntity,
        reason: EndReason,
        now: Long,
        clearActive: Boolean = true,
    ): Long {
        closeOpenSegments(session.id, now)
        val endUtc = notBefore(session, now)
        store.updateSession(session.copy(state = SessionState.ENDED, endUtc = endUtc, endReason = reason))
        if (clearActive) store.clearActive()
        return endUtc
    }

    /** [now], moved forward if needed so it is not before the session start or any segment boundary. */
    private suspend fun notBefore(session: SessionEntity, now: Long): Long {
        val latest = store.segments(session.id).maxOfOrNull { maxOf(it.startUtc, it.endUtc ?: it.startUtc) }
        return maxOf(now, session.startUtc, latest ?: session.startUtc)
    }

    companion object {
        /**
         * How far back a past session may start: Health Connect lets the app read at least this far
         * back, so its steps can always be filled in.
         */
        const val PAST_LIMIT_MILLIS = StepReconciler.HISTORY_LIMIT_MILLIS

        /** What is wrong with a past session from [startUtc] to [endUtc] at [nowUtc], or null if nothing. */
        fun pastTimeProblem(startUtc: Long, endUtc: Long, nowUtc: Long): PastTimeProblem? = when {
            endUtc <= startUtc -> PastTimeProblem.END_NOT_AFTER_START
            endUtc > nowUtc -> PastTimeProblem.IN_FUTURE
            startUtc < nowUtc - PAST_LIMIT_MILLIS -> PastTimeProblem.TOO_OLD
            else -> null
        }
    }
}
