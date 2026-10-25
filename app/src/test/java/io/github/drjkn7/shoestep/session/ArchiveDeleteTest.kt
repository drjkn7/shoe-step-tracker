package io.github.drjkn7.shoestep.session

import io.github.drjkn7.shoestep.core.FakeClock
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Code review 25 Oct 2026: archiving or deleting a pair ends its session in the same transaction, so a
 * tag tap can't start one in between.
 */
class ArchiveDeleteTest {

    private val clock = FakeClock()
    private lateinit var store: FakeSessionStore
    private lateinit var manager: SessionManager
    private var nextId = 0

    @Before fun setUp() {
        store = FakeSessionStore().apply {
            addShoe("A")
            addShoe("B")
        }
        manager = SessionManager(store, clock, newId = { "id${++nextId}" })
    }

    @Test fun archivingEndsThatPairsSessionInOneTransaction() = runTest {
        manager.start("A")
        val session = store.activeId!!
        val before = store.transactionCount

        assertEquals(SessionResult.Done, manager.setArchived("A", true))

        assertEquals(before + 1, store.transactionCount)
        assertTrue(store.shoes.getValue("A").archived)
        assertNull(store.activeId)
        assertEquals(SessionState.ENDED, store.sessions.getValue(session).state)
        assertEquals(EndReason.USER, store.sessions.getValue(session).endReason)
        // An archived pair can't start a new session.
        assertEquals(SessionResult.ShoeUnavailable, manager.start("A"))
    }

    @Test fun archivingLeavesAnotherPairsSessionAlone() = runTest {
        manager.start("B")
        manager.setArchived("A", true)
        assertEquals(SessionState.RUNNING, store.sessions.getValue(store.activeId!!).state)
    }

    @Test fun unarchivingChangesNoSession() = runTest {
        manager.setArchived("A", true)
        manager.start("B")
        assertEquals(SessionResult.Done, manager.setArchived("A", false))
        assertTrue(!store.shoes.getValue("A").archived)
        assertEquals("B", store.sessions.getValue(store.activeId!!).shoeId)
    }

    @Test fun deletingEndsTheSessionAndRemovesThePair() = runTest {
        manager.start("A")
        assertEquals(SessionResult.Done, manager.deleteShoe("A"))
        assertNull(store.activeId)
        assertTrue("A" !in store.shoes)
        assertTrue(store.sessions.values.none { it.shoeId == "A" })
        assertEquals(SessionResult.ShoeUnavailable, manager.deleteShoe("A")) // already gone
    }

    @Test fun aFailedArchiveKeepsTheSessionRunning() = runTest {
        manager.start("A")
        store.failOn = "setShoeArchived"
        runCatching { manager.setArchived("A", true) }
        assertEquals(SessionState.RUNNING, store.sessions.getValue(store.activeId!!).state)
        assertTrue(!store.shoes.getValue("A").archived)
    }
}
