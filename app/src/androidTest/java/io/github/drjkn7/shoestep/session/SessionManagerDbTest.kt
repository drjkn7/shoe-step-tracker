package io.github.drjkn7.shoestep.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.ui.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SessionManager against the real Room database (the JVM tests use a fake store). Uses its own
 * database file, never the app's, and deletes it afterwards.
 */
@RunWith(AndroidJUnit4::class)
class SessionManagerDbTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ShoeStepDatabase

    @Before fun setUp() {
        context.deleteDatabase(FILE)
        db = ShoeStepDatabase.build(context, FILE)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(FILE)
    }

    private fun manager(database: ShoeStepDatabase = db) = SessionManager(RoomSessionStore(database), TestClock)

    private suspend fun addShoe(id: String, archived: Boolean = false) = db.shoeDao().insert(
        ShoeEntity(id, null, "Shoe $id", "", "", ShoeType.WALKING, null, "EUR", null, 0, archived, 0),
    )

    /** T16/T17: the session is in the database file, so it survives the process being killed. */
    @Test fun runningSessionSurvivesClosingAndReopeningTheDatabase() = runTest {
        addShoe("A")
        manager().start("A")
        val id = db.activeSessionDao().getActiveSessionId()!!
        db.close()

        db = ShoeStepDatabase.build(context, FILE) // what a fresh process does on launch
        val afterRestart = manager()
        val active = afterRestart.activeSession.first()
        assertEquals(id, active?.session?.id)
        assertEquals(SessionState.RUNNING, active?.session?.state)
        assertEquals("Shoe A", active?.shoeName)
        assertEquals(SessionResult.Done, afterRestart.pause(id))
    }

    @Test fun fullSessionIsStoredWithItsSegments() = runTest {
        addShoe("A")
        val sessions = manager()
        sessions.start("A")
        val id = db.activeSessionDao().getActiveSessionId()!!
        sessions.pause(id)
        sessions.resume(id)
        sessions.end(id)

        val history = db.sessionDao().observeHistory("A").first()
        assertEquals(1, history.size)
        assertEquals(SessionState.ENDED, history[0].session.state)
        assertEquals(EndReason.USER, history[0].session.endReason)
        assertEquals(2, history[0].segmentCount)
        assertEquals(0L, history[0].totalSteps) // steps arrive in Phase 5
        assertNull(db.activeSessionDao().getActiveSessionId())
        assertNull(db.sessionDao().getOpenSegment(id))
    }

    @Test fun switchLeavesExactlyOneActiveSession() = runTest {
        addShoe("A"); addShoe("B")
        val sessions = manager()
        sessions.start("A")
        assertTrue(sessions.start("B") is SessionResult.NeedsSwitch)
        assertEquals(SessionResult.Done, sessions.switchTo("B"))

        assertEquals("B", sessions.activeSession.first()?.session?.shoeId)
        assertEquals(1, db.sessionDao().getUnendedSessions().size)
        assertEquals(EndReason.SWITCH, db.sessionDao().observeHistory("A").first().single().session.endReason)
    }

    @Test fun roomTransactionRollsBackEverythingOnFailure() = runTest {
        addShoe("A")
        val store = RoomSessionStore(db)
        runCatching {
            store.transaction {
                store.insertSession(SessionEntity("s", "A", 0, null, "UTC", SessionState.RUNNING, null))
                store.setActive("s")
                error("simulated crash halfway")
            }
        }
        assertNull(store.session("s"))
        assertNull(store.activeSessionId())
    }

    @Test fun deletingAPairEndsItsSessionAndRemovesItInOneGo() = runTest {
        addShoe("A")
        val sessions = manager()
        sessions.start("A")
        assertEquals(SessionResult.Done, sessions.deleteShoe("A"))
        assertNull(sessions.activeSession.first())
        assertNull(db.shoeDao().getById("A"))
        assertTrue(db.sessionDao().getUnendedSessions().isEmpty())
    }

    @Test fun archivingAPairEndsItsSession() = runTest {
        addShoe("A")
        val sessions = manager()
        sessions.start("A")
        assertEquals(SessionResult.Done, sessions.setArchived("A", true))
        assertNull(sessions.activeSession.first())
        assertEquals(true, db.shoeDao().getById("A")!!.archived)
        assertEquals(SessionState.ENDED, db.sessionDao().observeHistory("A").first().single().session.state)
    }

    /** T50, T51, T53, T54 against real SQL: overlap rules, the history row, totals and deleting. */
    @Test fun pastSessionsOverlapRulesAndDeletingUseTheRealQueries() = runTest {
        addShoe("A")
        addShoe("B")
        val sessions = manager()
        val now = TestClock.nowUtcMillis()
        val hour = 3_600_000L
        db.sessionDao().insertSession(SessionEntity("live", "B", now - 3 * hour, now - 2 * hour, "UTC", SessionState.ENDED, EndReason.USER))

        // Overlaps "live" by a minute: refused, naming pair B.
        val overlap = sessions.addPast("A", now - 2 * hour - 60_000, now - hour) as AddPastResult.Overlaps
        assertEquals("live", overlap.other.session.id)
        assertEquals("Shoe B", overlap.other.shoeName)
        // Touching ends are fine.
        val added = (sessions.addPast("A", now - 2 * hour, now - hour) as AddPastResult.Added).sessionId

        val row = db.sessionDao().observeHistory("A").first().single()
        assertEquals(added, row.session.id)
        assertTrue(row.session.addedLater)
        assertEquals(hour, row.activeMillis)
        assertTrue(row.hasPendingSteps) // waiting for StepReconciler, like any other session
        assertEquals(1, db.shoeDao().observeWithStatsById("A").first()!!.sessionCount)

        // A running session (no end yet) reaches into the future: any time after its start overlaps it.
        sessions.start("B")
        val running = db.activeSessionDao().getActiveSessionId()!!
        assertEquals(running, db.sessionDao().getFirstOverlapping(now + hour, now + 2 * hour)?.session?.id)

        // Only the added session can be deleted; its segment goes with it.
        assertEquals(SessionResult.NoChange, sessions.deleteAddedLater("live"))
        assertEquals(SessionResult.Done, sessions.deleteAddedLater(added))
        assertTrue(db.sessionDao().observeHistory("A").first().isEmpty())
        assertTrue(db.sessionDao().getSegments(added).isEmpty())
        assertEquals(0, db.shoeDao().observeWithStatsById("A").first()!!.sessionCount)
    }

    private companion object {
        const val FILE = "session-manager-test.db"
    }
}
