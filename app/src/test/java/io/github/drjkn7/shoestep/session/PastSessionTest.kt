package io.github.drjkn7.shoestep.session

import io.github.drjkn7.shoestep.core.FakeClock
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.SessionWithShoeName
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.StepsStatus
import io.github.drjkn7.shoestep.session.PastTimeProblem.END_NOT_AFTER_START
import io.github.drjkn7.shoestep.session.PastTimeProblem.IN_FUTURE
import io.github.drjkn7.shoestep.session.PastTimeProblem.TOO_OLD
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M2 Phase 6: adding a past session for a forgotten tap, and deleting it again (T50–T54). */
class PastSessionTest {

    private val clock = FakeClock(zone = ZoneId.of("Europe/London"))
    private val now get() = clock.nowMillis
    private lateinit var store: FakeSessionStore
    private lateinit var manager: SessionManager
    private var nextId = 0

    @Before fun setUp() {
        store = FakeSessionStore().apply {
            addShoe("A")
            addShoe("B")
            addShoe("C", archived = true)
        }
        manager = SessionManager(store, clock, newId = { "id${++nextId}" })
    }

    private suspend fun addPast(shoeId: String, start: Long, end: Long) = manager.addPast(shoeId, start, end)

    /** An ended session of pair B from [startAgo] to [endAgo] before now, recorded live. */
    private fun walkB(startAgo: Long, endAgo: Long) {
        store.sessions["b1"] = SessionEntity("b1", "B", now - startAgo, now - endAgo, "UTC", SessionState.ENDED, EndReason.USER)
        store.segments["g1"] = SegmentEntity("g1", "b1", now - startAgo, now - endAgo, 500, StepsStatus.FINAL, now)
    }

    @Test fun savesAnEndedAddedLaterSessionWithOnePendingSegment() = runTest {
        val result = addPast("A", now - 2 * HOUR, now - HOUR)

        val id = (result as AddPastResult.Added).sessionId
        val session = store.sessions.getValue(id)
        assertEquals("A", session.shoeId)
        assertEquals(now - 2 * HOUR, session.startUtc)
        assertEquals(now - HOUR, session.endUtc)
        assertEquals(SessionState.ENDED, session.state)
        assertEquals(EndReason.USER, session.endReason)
        assertEquals("Europe/London", session.zoneId)
        assertTrue(session.addedLater)
        // StepReconciler fills the steps in like any other closed segment.
        val segment = store.segments.values.single { it.sessionId == id }
        assertEquals(now - 2 * HOUR, segment.startUtc)
        assertEquals(now - HOUR, segment.endUtc)
        assertNull(segment.steps)
        assertEquals(StepsStatus.PENDING, segment.stepsStatus)
        assertEquals(StepsStatus.PENDING, segment.distanceStatus)
        // Nothing becomes active.
        assertNull(store.activeId)
    }

    @Test fun timesAreCheckedBeforeAnythingIsSaved() = runTest {
        assertEquals(AddPastResult.Invalid(END_NOT_AFTER_START), addPast("A", now - HOUR, now - HOUR))
        assertEquals(AddPastResult.Invalid(END_NOT_AFTER_START), addPast("A", now - HOUR, now - 2 * HOUR))
        assertEquals(AddPastResult.Invalid(IN_FUTURE), addPast("A", now - HOUR, now + MINUTE))
        assertEquals(AddPastResult.Invalid(TOO_OLD), addPast("A", now - 30 * DAY - MINUTE, now - 30 * DAY + HOUR))
        assertTrue(store.sessions.isEmpty() && store.segments.isEmpty())
    }

    @Test fun theLimitsThemselvesAreAllowed() = runTest {
        // Ending exactly now, and starting exactly 30 days ago, are both fine.
        assertTrue(addPast("A", now - HOUR, now) is AddPastResult.Added)
        assertTrue(addPast("B", now - 30 * DAY, now - 30 * DAY + HOUR) is AddPastResult.Added)
    }

    @Test fun anOverlapWithAnyPairIsRefusedAndNamesThatSession() = runTest {
        walkB(startAgo = 3 * HOUR, endAgo = 2 * HOUR)
        val other = store.sessions.values.single()

        val result = addPast("A", now - 2 * HOUR - MINUTE, now - 90 * MINUTE)

        assertEquals(AddPastResult.Overlaps(SessionWithShoeName(other, "Shoe B")), result)
        assertEquals(1, store.sessions.size)
    }

    @Test fun touchingAnotherSessionIsNotAnOverlap() = runTest {
        walkB(startAgo = 3 * HOUR, endAgo = 2 * HOUR)
        assertTrue(addPast("A", now - 4 * HOUR, now - 3 * HOUR) is AddPastResult.Added) // ends where B starts
        assertTrue(addPast("A", now - 2 * HOUR, now - HOUR) is AddPastResult.Added) // starts where B ends
    }

    @Test fun theRunningSessionCountsUpToNowAndBeyond() = runTest {
        clock.advanceBy(-2 * HOUR)
        manager.start("B") // started two hours ago, still running
        clock.advanceBy(2 * HOUR)

        val result = addPast("A", now - 30 * MINUTE, now - 10 * MINUTE)

        assertTrue(result is AddPastResult.Overlaps)
        assertNull((result as AddPastResult.Overlaps).other.session.endUtc)
        // The running session is untouched.
        assertEquals(SessionState.RUNNING, store.sessions.getValue(store.activeId!!).state)
        assertEquals(1, store.sessions.size)
    }

    @Test fun pairsThatCantStartSessionsCantGetPastOnesEither() = runTest {
        assertEquals(AddPastResult.ShoeUnavailable, addPast("C", now - 2 * HOUR, now - HOUR)) // archived
        assertEquals(AddPastResult.ShoeUnavailable, addPast("missing", now - 2 * HOUR, now - HOUR))
        assertTrue(store.sessions.isEmpty())
    }

    @Test fun sendingTheSameSessionTwiceSavesItOnce() = runTest {
        val first = addPast("A", now - 2 * HOUR, now - HOUR)
        val second = addPast("A", now - 2 * HOUR, now - HOUR)

        assertTrue(first is AddPastResult.Added)
        assertTrue(second is AddPastResult.Overlaps)
        assertEquals(1, store.sessions.size)
    }

    @Test fun onlySessionsAddedLaterCanBeDeleted() = runTest {
        walkB(startAgo = 3 * HOUR, endAgo = 2 * HOUR)
        val live = store.sessions.values.single().id
        val added = (addPast("A", now - 2 * HOUR, now - HOUR) as AddPastResult.Added).sessionId

        assertEquals(SessionResult.NoChange, manager.deleteAddedLater(live))
        assertEquals(SessionResult.Done, manager.deleteAddedLater(added))
        assertEquals(SessionResult.NoChange, manager.deleteAddedLater(added)) // already gone

        assertEquals(setOf(live), store.sessions.keys)
        assertTrue(store.segments.values.none { it.sessionId == added })
    }

    @Test fun aFailedSaveLeavesNothingBehind() = runTest {
        store.failOn = "insertSegment"
        runCatching { addPast("A", now - 2 * HOUR, now - HOUR) }
        assertTrue(store.sessions.isEmpty() && store.segments.isEmpty())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
