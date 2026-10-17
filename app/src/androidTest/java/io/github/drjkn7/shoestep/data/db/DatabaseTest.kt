package io.github.drjkn7.shoestep.data.db

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.data.model.StepsStatus
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: ShoeStepDatabase
    private lateinit var shoes: ShoeDao
    private lateinit var sessions: SessionDao
    private lateinit var active: ActiveSessionDao

    private val clock = object : Clock {
        override fun nowUtcMillis() = 1_760_000_000_000L
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }

    @Before fun setUp() {
        db = ShoeStepDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        shoes = db.shoeDao()
        sessions = db.sessionDao()
        active = db.activeSessionDao()
    }

    @After fun tearDown() = db.close()

    // --- helpers -------------------------------------------------------------------------------

    private fun shoe(id: String, tag: String? = null, price: Long? = 12_000, archived: Boolean = false) = ShoeEntity(
        id = id, tagUuid = tag, name = "Shoe $id", brand = "", model = "", type = ShoeType.WALKING,
        priceMinor = price, currencyCode = "EUR", purchaseEpochDay = null, accentColor = 0,
        archived = archived, createdAtUtc = 0,
    )

    private suspend fun addSession(
        id: String,
        shoeId: String,
        state: SessionState = SessionState.ENDED,
        start: Long = 1_000,
    ) = sessions.insertSession(
        SessionEntity(id, shoeId, start, if (state == SessionState.ENDED) start + 10_000 else null, "UTC", state,
            if (state == SessionState.ENDED) EndReason.USER else null),
    )

    private suspend fun addSegment(
        id: String,
        sessionId: String,
        start: Long,
        end: Long?,
        steps: Long?,
        status: StepsStatus = StepsStatus.FINAL,
    ) = sessions.insertSegment(SegmentEntity(id, sessionId, start, end, steps, status, null))

    private suspend fun stats(shoeId: String) = shoes.getWithStatsById(shoeId)!!

    // --- totals and stats ----------------------------------------------------------------------

    @Test fun totalsAreSummedAcrossSessionsAndSegments() = runTest {
        shoes.insert(shoe("a"))
        addSession("s1", "a", start = 1_000)
        addSegment("g1", "s1", 1_000, 61_000, 500)
        addSegment("g2", "s1", 100_000, 160_000, 700) // paused gap between g1 and g2 not counted
        addSession("s2", "a", start = 500_000)
        addSegment("g3", "s2", 500_000, 530_000, 300)

        val s = stats("a")
        assertEquals(1_500L, s.totalSteps)
        assertEquals(2, s.sessionCount)
        assertEquals(2, s.endedSessionCount)
        assertEquals(150_000L, s.activeMillis)
        assertEquals(500_000L, s.lastUsedUtc)
        assertEquals(750L, s.averageStepsPerSession)
        assertFalse(s.isActive)
        assertFalse(s.hasUnfinalSteps)
    }

    @Test fun shoeWithoutSessionsHasZeroTotals() = runTest {
        shoes.insert(shoe("a"))
        val s = stats("a")
        assertEquals(0L, s.totalSteps)
        assertEquals(0, s.sessionCount)
        assertEquals(0L, s.activeMillis)
        assertNull(s.lastUsedUtc)
        assertNull(s.averageStepsPerSession)
    }

    @Test fun pendingAndOpenSegmentsCountAsZeroButAreFlagged() = runTest {
        shoes.insert(shoe("a"))
        addSession("s1", "a", state = SessionState.RUNNING)
        addSegment("g1", "s1", 0, 1_000, null, StepsStatus.PENDING)
        addSegment("g2", "s1", 2_000, 3_000, 40, StepsStatus.PROVISIONAL)
        addSegment("g3", "s1", 4_000, null, null, StepsStatus.PENDING) // still open
        active.setActive(ActiveSessionEntity(sessionId = "s1"))

        val s = stats("a")
        assertEquals(40L, s.totalSteps)
        assertEquals(2_000L, s.activeMillis) // open segment not counted
        assertTrue(s.isActive)
        assertTrue(s.hasUnfinalSteps)
        assertNull(s.averageStepsPerSession) // no ended sessions yet
    }

    @Test fun negativeDurationFromClockChangeCountsAsZero() = runTest {
        shoes.insert(shoe("a"))
        addSession("s1", "a")
        addSegment("g1", "s1", 10_000, 4_000, 10) // end before start
        addSegment("g2", "s1", 20_000, 25_000, 10)
        assertEquals(5_000L, stats("a").activeMillis)
    }

    @Test fun totalsAreSeparatePerShoe() = runTest {
        shoes.insert(shoe("a"))
        shoes.insert(shoe("b"))
        addSession("s1", "a"); addSegment("g1", "s1", 0, 1, 100)
        addSession("s2", "b"); addSegment("g2", "s2", 0, 1, 7)
        assertEquals(100L, stats("a").totalSteps)
        assertEquals(7L, stats("b").totalSteps)
    }

    @Test fun largeStepTotalsDoNotOverflowInt() = runTest {
        shoes.insert(shoe("a"))
        addSession("s1", "a")
        addSegment("g1", "s1", 0, 1, 3_000_000_000)
        addSegment("g2", "s1", 2, 3, 3_000_000_000)
        assertEquals(6_000_000_000L, stats("a").totalSteps)
    }

    @Test fun historyListsSessionsNewestFirstWithTheirSteps() = runTest {
        shoes.insert(shoe("a"))
        addSession("old", "a", start = 1_000); addSegment("g1", "old", 1_000, 2_000, 10)
        addSession("new", "a", start = 9_000); addSegment("g2", "new", 9_000, 9_500, 20); addSegment("g3", "new", 9_600, 9_700, 5)

        val history = sessions.observeHistory("a").first()
        assertEquals(listOf("new", "old"), history.map { it.session.id })
        assertEquals(25L, history[0].totalSteps)
        assertEquals(2, history[0].segmentCount)
        assertEquals(600L, history[0].activeMillis)
    }

    // --- cascade delete, archive -------------------------------------------------------------

    @Test fun deletingShoeCascadesToSessionsSegmentsAndActive() = runTest {
        shoes.insert(shoe("a"))
        addSession("s1", "a", state = SessionState.RUNNING)
        addSegment("g1", "s1", 0, null, null, StepsStatus.PENDING)
        active.setActive(ActiveSessionEntity(sessionId = "s1"))

        assertEquals(1, shoes.delete("a"))
        assertNull(sessions.getSession("s1"))
        assertTrue(sessions.getSegments("s1").isEmpty())
        assertNull(active.getActiveSessionId())
    }

    @Test fun archivingKeepsHistory() = runTest {
        val repo = ShoeRepository(shoes, clock)
        shoes.insert(shoe("a"))
        addSession("s1", "a"); addSegment("g1", "s1", 0, 1, 42)

        assertTrue(repo.setArchived("a", true))
        assertTrue(repo.observeShoes(archived = false).first().isEmpty())
        assertEquals(42L, repo.observeShoes(archived = true).first().single().totalSteps)

        assertTrue(repo.setArchived("a", false))
        assertEquals(listOf("a"), repo.observeShoes().first().map { it.shoe.id })
    }

    // --- one active session invariant ----------------------------------------------------------

    @Test fun settingActiveTwiceReplacesNeverAdds() = runTest {
        shoes.insert(shoe("a"))
        addSession("s1", "a", state = SessionState.RUNNING)
        addSession("s2", "a", state = SessionState.RUNNING)

        active.setActive(ActiveSessionEntity(sessionId = "s1"))
        active.setActive(ActiveSessionEntity(sessionId = "s2"))

        assertEquals("s2", active.getActiveSessionId())
        assertEquals("s2", active.observeActiveSession().first()?.id)
    }

    @Test fun activeRowWithOtherIdIsRejectedByDatabase() = runTest {
        shoes.insert(shoe("a"))
        addSession("s1", "a", state = SessionState.RUNNING)
        try {
            active.setActive(ActiveSessionEntity(id = 2, sessionId = "s1"))
            fail("Database accepted a second active_session row")
        } catch (expected: Exception) {
            // The trigger aborted the insert.
        }
        assertNull(active.getActiveSessionId())
    }

    @Test fun activeSessionMustReferenceExistingSession() = runTest {
        try {
            active.setActive(ActiveSessionEntity(sessionId = "missing"))
            fail("Foreign key was not enforced")
        } catch (expected: Exception) {
            // Foreign key constraint failed.
        }
    }

    @Test fun clearRemovesActiveSession() = runTest {
        shoes.insert(shoe("a"))
        addSession("s1", "a", state = SessionState.RUNNING)
        active.setActive(ActiveSessionEntity(sessionId = "s1"))
        active.clear()
        assertNull(active.getActiveSessionId())
    }

    // --- tags (T29) --------------------------------------------------------------------------

    @Test fun sameTagUuidOnTwoShoesIsBlocked() = runTest {
        val tag = "3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e"
        shoes.insert(shoe("a", tag = tag))
        try {
            shoes.insert(shoe("b", tag = tag))
            fail("Duplicate tag UUID was accepted")
        } catch (expected: Exception) {
            // Unique index rejected it.
        }
        assertEquals("a", shoes.getByTagUuid(tag)?.id)
        assertNull(shoes.getById("b"))
    }

    @Test fun manyShoesMayHaveNoTag() = runTest {
        shoes.insert(shoe("a", tag = null))
        shoes.insert(shoe("b", tag = null))
        assertEquals(2, shoes.observeWithStats(false).first().size)
    }

    // --- repository ---------------------------------------------------------------------------

    @Test fun repositoryValidatesBeforeSaving() = runTest {
        val repo = ShoeRepository(shoes, clock, newId = { "fixed-id" })
        val bad = ShoeInput("", "", "", ShoeType.OTHER, "-5", "EUR", null, 0)
        assertTrue(repo.add(bad) is SaveResult.Invalid)
        assertTrue(shoes.observeWithStats(false).first().isEmpty())

        val good = bad.copy(name = "  Trail  pair ", priceText = "89.50")
        assertEquals(SaveResult.Saved("fixed-id"), repo.add(good))
        val saved = shoes.getById("fixed-id")!!
        assertEquals("Trail pair", saved.name)
        assertEquals(8_950L, saved.priceMinor)
        assertEquals(clock.nowUtcMillis(), saved.createdAtUtc)
    }

    @Test fun repositoryUpdateKeepsTagAndCreationTime() = runTest {
        val repo = ShoeRepository(shoes, clock)
        shoes.insert(shoe("a", tag = "3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e").copy(createdAtUtc = 5))
        val input = ShoeInput("Renamed", "B", "M", ShoeType.HIKING, "", "GBP", null, 3)
        assertEquals(SaveResult.Saved("a"), repo.update("a", input))
        val updated = shoes.getById("a")!!
        assertEquals("Renamed", updated.name)
        assertNull(updated.priceMinor)
        assertEquals("3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e", updated.tagUuid)
        assertEquals(5L, updated.createdAtUtc)
        assertEquals(SaveResult.NotFound, repo.update("missing", input))
    }

    @Test fun parameterisedQueriesTreatInputAsData() = runTest {
        shoes.insert(shoe("a"))
        // A classic injection string is just an unknown id, not SQL.
        assertNull(shoes.getById("a' OR '1'='1"))
        assertEquals(0, shoes.delete("a' OR '1'='1"))
        assertEquals(1, shoes.observeWithStats(false).first().size)
    }
}
