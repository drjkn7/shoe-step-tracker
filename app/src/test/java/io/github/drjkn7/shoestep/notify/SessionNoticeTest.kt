package io.github.drjkn7.shoestep.notify

import io.github.drjkn7.shoestep.core.FakeClock
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.session.FakeSessionStore
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.session.SessionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** T41/T43: what the session notification shows, worked out from the real SessionManager's data. */
class SessionNoticeTest {

    private val clock = FakeClock()
    private lateinit var store: FakeSessionStore
    private lateinit var manager: SessionManager
    private var nextId = 0
    private val minute = 60_000L

    @Before fun setUp() {
        store = FakeSessionStore().apply {
            addShoe("A")
            addShoe("B")
        }
        manager = SessionManager(store, clock, newId = { "id${++nextId}" })
    }

    private suspend fun notice(enabled: Boolean = true): SessionNotice? {
        val session = store.activeId?.let { store.sessions.getValue(it) }
        val active = session?.let { ActiveSessionWithShoe(it, "Pair ${it.shoeId}", 0) }
        return SessionNotice.from(active, session?.let { manager.segments(it.id) }.orEmpty(), enabled)
    }

    @Test fun runningTimerStartsWithTheSession() = runTest {
        val start = clock.nowMillis
        manager.start("A")
        val running = notice() as SessionNotice.Running
        assertEquals("Pair A", running.shoeName)
        assertEquals(start, running.startUtc)
        assertEquals(start, running.timerBaseUtc)
    }

    @Test fun pausedShowsWhenItPausedAndTheWalkingTimeSoFar() = runTest {
        manager.start("A")
        val id = store.activeId!!
        clock.advanceBy(47 * minute)
        manager.pause(id)
        val paused = notice() as SessionNotice.Paused
        assertEquals(clock.nowMillis, paused.pausedAtUtc)
        assertEquals(47 * minute, paused.activeMillis)
    }

    @Test fun timerLeavesOutPausedTime() = runTest {
        val start = clock.nowMillis
        manager.start("A")
        val id = store.activeId!!
        clock.advanceBy(10 * minute)
        manager.pause(id)
        clock.advanceBy(30 * minute) // paused: not counted
        manager.resume(id)
        val running = notice() as SessionNotice.Running
        // Started 40 minutes ago with 10 of them walking: the timer reads 10 min, as if it began 30 min late.
        assertEquals(start + 30 * minute, running.timerBaseUtc)
        assertEquals(clock.nowMillis - 10 * minute, running.timerBaseUtc)
        assertEquals(start, running.startUtc)
    }

    @Test fun noNoticeWhenIdleEndedOrTurnedOff() = runTest {
        assertNull(notice())
        manager.start("A")
        assertNull(notice(enabled = false))
        manager.end(store.activeId!!)
        assertNull(notice())
    }

    @Test fun switchShowsTheNewPair() = runTest {
        manager.start("A")
        manager.switchTo("B")
        assertEquals("Pair B", notice()?.shoeName)
    }

    /** T42: the buttons go through SessionManager, so an old notification changes nothing. */
    @Test fun buttonsActOnlyOnTheActiveSession() = runTest {
        manager.start("A")
        val id = store.activeId!!
        assertEquals(SessionResult.Done, SessionActions.handle(SessionActions.PAUSE, id, manager))
        assertEquals(SessionResult.NoChange, SessionActions.handle(SessionActions.PAUSE, id, manager)) // repeated tap
        assertEquals(SessionResult.Done, SessionActions.handle(SessionActions.RESUME, id, manager))

        assertEquals(SessionResult.NoChange, SessionActions.handle(SessionActions.PAUSE, "not-a-session", manager))
        assertEquals(SessionResult.NoChange, SessionActions.handle(SessionActions.PAUSE, null, manager))
        assertEquals(SessionResult.NoChange, SessionActions.handle("something.else", id, manager))

        manager.switchTo("B")
        // A Pause button still showing the old pair's session leaves the new one running.
        assertEquals(SessionResult.NoChange, SessionActions.handle(SessionActions.PAUSE, id, manager))
        assert(notice() is SessionNotice.Running)
    }
}
