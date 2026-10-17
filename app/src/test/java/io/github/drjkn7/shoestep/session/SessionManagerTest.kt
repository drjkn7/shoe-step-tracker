package io.github.drjkn7.shoestep.session

import io.github.drjkn7.shoestep.core.FakeClock
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.SessionState.ENDED
import io.github.drjkn7.shoestep.data.model.SessionState.PAUSED
import io.github.drjkn7.shoestep.data.model.SessionState.RUNNING
import io.github.drjkn7.shoestep.data.model.StepsStatus
import io.github.drjkn7.shoestep.session.SessionResult.Done
import io.github.drjkn7.shoestep.session.SessionResult.NeedsSwitch
import io.github.drjkn7.shoestep.session.SessionResult.NoChange
import io.github.drjkn7.shoestep.session.SessionResult.ShoeUnavailable
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private val clock = FakeClock()
    private lateinit var store: FakeSessionStore
    private lateinit var manager: SessionManager
    private var nextId = 0

    @Before fun setUp() {
        store = FakeSessionStore().apply {
            addShoe("A")
            addShoe("B")
            addShoe("C", archived = true)
        }
        manager = newManager()
    }

    /** A fresh manager over the same store = what the app sees after being killed and reopened. */
    private fun newManager() = SessionManager(store, clock, newId = { "id${++nextId}" })

    private fun activeSession(): SessionEntity? = store.activeId?.let { store.sessions[it] }
    private fun segmentsOf(sessionId: String) = store.segments.values.filter { it.sessionId == sessionId }

    // --- Every state × every event -----------------------------------------------------------

    private enum class Setup { IDLE, RUNNING_A, PAUSED_A }

    /** What the store looks like afterwards, in terms a person can check against the table. */
    private data class Snapshot(
        val activeShoe: String?,
        val activeState: SessionState?,
        val openSegments: Int,
        val ended: List<Pair<String, EndReason?>>,
    )

    private fun snapshot(): Snapshot {
        val active = activeSession()
        return Snapshot(
            activeShoe = active?.shoeId,
            activeState = active?.state,
            openSegments = store.segments.values.count { it.endUtc == null },
            ended = store.sessions.values.filter { it.state == ENDED }.map { it.shoeId to it.endReason },
        )
    }

    private val idle = Snapshot(null, null, 0, emptyList())
    private val runningA = Snapshot("A", RUNNING, 1, emptyList())
    private val pausedA = Snapshot("A", PAUSED, 0, emptyList())
    private val endedA = Snapshot(null, null, 0, listOf("A" to EndReason.USER))
    private val switchedToB = Snapshot("B", RUNNING, 1, listOf("A" to EndReason.SWITCH))

    /** `current` = the active session's id at the time of the event ("none" when idle). */
    private class Event(val name: String, val run: suspend SessionManager.(current: String) -> SessionResult)

    private val startA = Event("start A") { start("A") }
    private val startB = Event("start B") { start("B") }
    private val startArchived = Event("start archived C") { start("C") }
    private val startMissing = Event("start missing shoe") { start("missing") }
    private val switchA = Event("switch to A") { switchTo("A") }
    private val switchB = Event("switch to B") { switchTo("B") }
    private val switchArchived = Event("switch to archived C") { switchTo("C") }
    private val pause = Event("pause current") { pause(it) }
    private val pauseStale = Event("pause stale id") { pause("stale") }
    private val resume = Event("resume current") { resume(it) }
    private val resumeStale = Event("resume stale id") { resume("stale") }
    private val end = Event("end current") { end(it) }
    private val endStale = Event("end stale id") { end("stale") }
    private val endForA = Event("end for shoe A") { endForShoe("A") }
    private val endForB = Event("end for shoe B") { endForShoe("B") }

    private data class Case(val setup: Setup, val event: Event, val result: SessionResult, val after: Snapshot)

    private val table = listOf(
        Case(Setup.IDLE, startA, Done, runningA),
        Case(Setup.IDLE, startArchived, ShoeUnavailable, idle),
        Case(Setup.IDLE, startMissing, ShoeUnavailable, idle),
        Case(Setup.IDLE, switchA, Done, runningA),
        Case(Setup.IDLE, switchArchived, ShoeUnavailable, idle),
        Case(Setup.IDLE, pause, NoChange, idle),
        Case(Setup.IDLE, resume, NoChange, idle),
        Case(Setup.IDLE, end, NoChange, idle),
        Case(Setup.IDLE, endForA, NoChange, idle),

        Case(Setup.RUNNING_A, startA, NoChange, runningA),
        Case(Setup.RUNNING_A, startB, NeedsSwitch("A"), runningA),
        Case(Setup.RUNNING_A, startArchived, ShoeUnavailable, runningA),
        Case(Setup.RUNNING_A, switchA, NoChange, runningA),
        Case(Setup.RUNNING_A, switchB, Done, switchedToB),
        Case(Setup.RUNNING_A, switchArchived, ShoeUnavailable, runningA),
        Case(Setup.RUNNING_A, pause, Done, pausedA),
        Case(Setup.RUNNING_A, pauseStale, NoChange, runningA),
        Case(Setup.RUNNING_A, resume, NoChange, runningA),
        Case(Setup.RUNNING_A, resumeStale, NoChange, runningA),
        Case(Setup.RUNNING_A, end, Done, endedA),
        Case(Setup.RUNNING_A, endStale, NoChange, runningA),
        Case(Setup.RUNNING_A, endForA, Done, endedA),
        Case(Setup.RUNNING_A, endForB, NoChange, runningA),

        Case(Setup.PAUSED_A, startA, NoChange, pausedA),
        Case(Setup.PAUSED_A, startB, NeedsSwitch("A"), pausedA),
        Case(Setup.PAUSED_A, startArchived, ShoeUnavailable, pausedA),
        Case(Setup.PAUSED_A, switchA, NoChange, pausedA),
        Case(Setup.PAUSED_A, switchB, Done, switchedToB),
        Case(Setup.PAUSED_A, pause, NoChange, pausedA),
        Case(Setup.PAUSED_A, pauseStale, NoChange, pausedA),
        Case(Setup.PAUSED_A, resume, Done, runningA),
        Case(Setup.PAUSED_A, resumeStale, NoChange, pausedA),
        Case(Setup.PAUSED_A, end, Done, endedA),
        Case(Setup.PAUSED_A, endStale, NoChange, pausedA),
        Case(Setup.PAUSED_A, endForA, Done, endedA),
        Case(Setup.PAUSED_A, endForB, NoChange, pausedA),
    )

    @Test fun everyStateAndEventBehavesAsTheTableSays() = runTest {
        for (case in table) {
            setUp()
            when (case.setup) {
                Setup.IDLE -> Unit
                Setup.RUNNING_A -> manager.start("A")
                Setup.PAUSED_A -> {
                    manager.start("A")
                    manager.pause(store.activeId!!)
                }
            }
            val label = "${case.setup} + ${case.event.name}"
            val current = store.activeId ?: "none"

            assertEquals(label, case.result, case.event.run(manager, current))
            assertEquals(label, case.after, snapshot())

            // Idempotency: the same event again never changes anything a second time.
            val again = case.event.run(manager, current)
            assertTrue("$label (repeated) returned $again", again is NoChange || again == case.result && again !is Done)
            assertEquals("$label (repeated)", case.after, snapshot())
        }
    }

    // --- Timing -----------------------------------------------------------------------------

    @Test fun pauseAndResumeSplitTheSessionIntoSegments() = runTest {
        val start = clock.nowMillis
        manager.start("A")
        val id = store.activeId!!
        clock.advanceBy(10 * MINUTE); manager.pause(id)
        clock.advanceBy(5 * MINUTE); manager.resume(id)
        clock.advanceBy(20 * MINUTE); manager.end(id)

        val segments = segmentsOf(id).sortedBy { it.startUtc }
        assertEquals(listOf(10 * MINUTE, 20 * MINUTE), segments.map { it.endUtc!! - it.startUtc })
        assertTrue(segments.all { it.stepsStatus == StepsStatus.PENDING && it.steps == null })
        val session = store.sessions.getValue(id)
        assertEquals(start, session.startUtc)
        assertEquals(start + 35 * MINUTE, session.endUtc)
        assertEquals(EndReason.USER, session.endReason)
        assertNull(store.activeId)
    }

    @Test fun switchEndsTheOldSessionExactlyWhereTheNewOneStarts() = runTest {
        manager.start("A")
        val old = store.activeId!!
        clock.advanceBy(MINUTE)
        manager.switchTo("B")

        val oldSession = store.sessions.getValue(old)
        val newSession = activeSession()!!
        assertEquals(EndReason.SWITCH, oldSession.endReason)
        assertEquals(oldSession.endUtc, newSession.startUtc)
        assertEquals("B", newSession.shoeId)
    }

    @Test fun sessionRecordsTheTimeZoneItStartedIn() = runTest {
        clock.zone = ZoneId.of("Europe/Berlin")
        manager.start("A")
        assertEquals("Europe/Berlin", activeSession()!!.zoneId)
    }

    // --- Negative-duration guard --------------------------------------------------------------

    @Test fun clockGoingBackwardsNeverProducesNegativeDurations() = runTest {
        manager.start("A")
        val id = store.activeId!!
        val start = clock.nowMillis

        clock.advanceBy(-HOUR); manager.pause(id)
        clock.advanceBy(-HOUR); manager.resume(id)
        clock.advanceBy(-HOUR); manager.end(id)

        val segments = segmentsOf(id).sortedBy { it.id }
        assertTrue("segment ends before it starts", segments.all { it.endUtc!! >= it.startUtc })
        assertTrue("segments overlap", segments[1].startUtc >= segments[0].endUtc!!)
        val session = store.sessions.getValue(id)
        assertTrue(session.endUtc!! >= start)
        assertTrue(session.endUtc!! >= segments.maxOf { it.endUtc!! })
    }

    @Test fun switchAfterClockWentBackStillStartsNoEarlierThanTheOldEnd() = runTest {
        manager.start("A")
        val old = store.activeId!!
        clock.advanceBy(-HOUR)
        manager.switchTo("B")
        val oldEnd = store.sessions.getValue(old).endUtc!!
        assertTrue(oldEnd >= store.sessions.getValue(old).startUtc)
        assertEquals(oldEnd, activeSession()!!.startUtc)
    }

    // --- Process death / restore --------------------------------------------------------------

    @Test fun runningSessionSurvivesTheAppBeingKilled() = runTest {
        manager.start("A")
        val id = store.activeId!!

        val afterRestart = newManager()
        val active = afterRestart.activeSession.first()
        assertEquals(id, active?.session?.id)
        assertEquals(RUNNING, active?.session?.state)
        assertEquals("Shoe A", active?.shoeName)

        assertEquals(Done, afterRestart.pause(id))
        assertEquals(PAUSED, activeSession()!!.state)
    }

    @Test fun pausedSessionSurvivesTheAppBeingKilled() = runTest {
        manager.start("A")
        val id = store.activeId!!
        manager.pause(id)

        val afterRestart = newManager()
        assertEquals(PAUSED, afterRestart.activeSession.first()?.session?.state)
        assertEquals(Done, afterRestart.resume(id))
    }

    @Test fun restoreClearsAnActivePointerToAnEndedSession() = runTest {
        store.sessions["s"] = session("s", "A", ENDED, endUtc = 2_000)
        store.activeId = "s"

        newManager().restore()
        assertNull(store.activeId)
    }

    @Test fun restorePausesARunningSessionWithNoOpenSegment() = runTest {
        store.sessions["s"] = session("s", "A", RUNNING)
        store.segments["g"] = segment("g", "s", 1_000, 2_000)
        store.activeId = "s"

        newManager().restore()
        assertEquals(PAUSED, store.sessions.getValue("s").state)
    }

    @Test fun restoreResumesAPausedSessionThatStillHasAnOpenSegment() = runTest {
        store.sessions["s"] = session("s", "A", PAUSED)
        store.segments["g"] = segment("g", "s", 1_000, null)
        store.activeId = "s"

        newManager().restore()
        assertEquals(RUNNING, store.sessions.getValue("s").state)
    }

    @Test fun restoreEndsUnendedSessionsThatAreNotActiveWithoutInventingTime() = runTest {
        store.sessions["orphan"] = session("orphan", "B", RUNNING)
        store.segments["g"] = segment("g", "orphan", 1_000, null)
        clock.nowMillis = 999_999_999

        newManager().restore()
        val orphan = store.sessions.getValue("orphan")
        assertEquals(ENDED, orphan.state)
        assertEquals(1_000L, orphan.endUtc) // last known time, not "now"
        assertEquals(1_000L, store.segments.getValue("g").endUtc)
    }

    @Test fun restoreRunsOncePerLaunchAndLeavesConsistentDataAlone() = runTest {
        manager.start("A")
        val before = snapshot()
        val fresh = newManager()
        val count = store.transactionCount
        fresh.restore(); fresh.restore(); fresh.activeSession.first()
        assertEquals(count + 1, store.transactionCount)
        assertEquals(before, snapshot())
    }

    // --- Atomicity and concurrency ------------------------------------------------------------

    @Test fun aFailureHalfwayThroughLeavesNothingBehind() = runTest {
        store.failOn = "setActive" // fails after the session and segment were inserted
        try {
            manager.start("A")
            fail("expected the simulated failure")
        } catch (_: IllegalStateException) {
        }
        assertEquals(idle, snapshot())
        assertTrue(store.sessions.isEmpty() && store.segments.isEmpty())

        store.failOn = null
        assertEquals(Done, manager.start("A"))
    }

    @Test fun failedSwitchKeepsTheOriginalSessionRunning() = runTest {
        manager.start("A")
        store.failOn = "insertSession"
        runCatching { manager.switchTo("B") }
        assertEquals(runningA, snapshot())
    }

    @Test fun simultaneousStartsForTwoPairsGiveExactlyOneSession() = runTest {
        val a = async { manager.start("A") }
        val b = async { manager.start("B") }
        val results = listOf(a.await(), b.await())
        assertEquals(1, results.count { it == Done })
        assertEquals(1, results.count { it is NeedsSwitch })
        assertEquals(1, store.sessions.size)
    }

    // --- Helpers ------------------------------------------------------------------------------

    private fun session(id: String, shoeId: String, state: SessionState, endUtc: Long? = null) =
        SessionEntity(id, shoeId, 1_000, endUtc, "UTC", state, if (state == ENDED) EndReason.USER else null)

    private fun segment(id: String, sessionId: String, start: Long, end: Long?) =
        SegmentEntity(id, sessionId, start, end, null, StepsStatus.PENDING, null)

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
