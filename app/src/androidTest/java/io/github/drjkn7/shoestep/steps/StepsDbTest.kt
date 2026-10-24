package io.github.drjkn7.shoestep.steps

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionDao
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.data.model.StepsStatus
import io.github.drjkn7.shoestep.data.model.StepsStatus.FINAL
import io.github.drjkn7.shoestep.data.model.StepsStatus.PENDING
import io.github.drjkn7.shoestep.data.model.StepsStatus.PROVISIONAL
import io.github.drjkn7.shoestep.data.model.StepsStatus.UNAVAILABLE
import io.github.drjkn7.shoestep.session.RoomSessionStore
import io.github.drjkn7.shoestep.session.SessionManager
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** RoomStepStore's SQL guards, the "not final yet" flags and distance totals, on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class StepsDbTest {

    private lateinit var db: ShoeStepDatabase
    private lateinit var store: RoomStepStore
    private lateinit var sessions: SessionDao

    private val clock = object : Clock {
        var now = 1_760_000_000_000L
        override fun nowUtcMillis() = now
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }

    @Before fun setUp() = runTest {
        db = ShoeStepDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = RoomStepStore(db)
        sessions = db.sessionDao()
        db.shoeDao().insert(
            ShoeEntity(
                id = "a", tagUuid = null, name = "Shoe a", brand = "", model = "", type = ShoeType.WALKING,
                priceMinor = null, currencyCode = "EUR", purchaseEpochDay = null, accentColor = 0,
                archived = false, createdAtUtc = 0,
            ),
        )
        sessions.insertSession(SessionEntity("s1", "a", 0, 100_000, "UTC", SessionState.ENDED, EndReason.USER))
    }

    @After fun tearDown() = db.close()

    private suspend fun segment(id: String, start: Long, end: Long?, steps: Long? = null, status: StepsStatus = PENDING) =
        sessions.insertSegment(SegmentEntity(id, "s1", start, end, steps, status, null))

    private suspend fun get(id: String) = store.segments("s1").single { it.id == id }

    private suspend fun history() = sessions.observeHistory("a").first().single()

    @Test fun unresolvedListsClosedPendingFirstThenProvisionalOldestFirst() = runTest {
        segment("prov-old", 0, 1_000, 5, PROVISIONAL)
        segment("pend-new", 5_000, 6_000)
        segment("pend-old", 2_000, 3_000)
        segment("final", 7_000, 8_000, 1, FINAL)
        segment("gone", 9_000, 10_000, null, UNAVAILABLE)
        segment("open", 11_000, null)

        assertEquals(listOf("pend-old", "pend-new", "prov-old"), store.unresolvedClosedSegments(10).map { it.id })
        assertEquals(listOf("pend-old"), store.unresolvedClosedSegments(1).map { it.id })
    }

    @Test fun saveStepsOnlyWritesWhileStillUnresolvedAndUnchanged() = runTest {
        segment("g1", 0, 1_000)
        assertTrue(store.saveSteps("g1", 1_000, 40, PROVISIONAL, 2_000))
        assertEquals(SegmentEntity("g1", "s1", 0, 1_000, 40, PROVISIONAL, 2_000), get("g1"))

        assertFalse("end moved: stale read", store.saveSteps("g1", 999, 99, FINAL, 3_000))
        assertTrue(store.saveSteps("g1", 1_000, 41, FINAL, 3_000))
        assertFalse("final is never overwritten", store.saveSteps("g1", 1_000, 99, PROVISIONAL, 4_000))
        assertEquals(41L, get("g1").steps)

        segment("g2", 2_000, 3_000, null, UNAVAILABLE)
        assertFalse("unavailable is settled", store.saveSteps("g2", 3_000, 7, FINAL, 4_000))
        segment("open", 4_000, null)
        assertFalse("open segments are never written", store.saveSteps("open", 5_000, 7, FINAL, 6_000))
        assertNull(get("open").steps)
        assertFalse("unknown id", store.saveSteps("nope", 1, 1, FINAL, 1))
    }

    @Test fun historyAndStatsFlagsFollowTheStatuses() = runTest {
        segment("g1", 0, 1_000)
        segment("g2", 2_000, 3_000, 40, PROVISIONAL)
        history().let {
            assertTrue(it.hasPendingSteps)
            assertTrue(it.hasUnfinalSteps)
        }
        assertTrue(db.shoeDao().getWithStatsById("a")!!.hasPendingSteps)

        store.saveSteps("g1", 1_000, 60, PROVISIONAL, 5_000)
        assertFalse(db.shoeDao().getWithStatsById("a")!!.hasPendingSteps)
        history().let {
            assertFalse(it.hasPendingSteps)
            assertTrue(it.hasUnfinalSteps)
            assertEquals(100L, it.totalSteps)
        }
        assertTrue(db.shoeDao().getWithStatsById("a")!!.hasUnfinalSteps)

        store.saveSteps("g1", 1_000, null, UNAVAILABLE, 6_000)
        store.saveSteps("g2", 3_000, 40, FINAL, 6_000)
        history().let {
            assertFalse(it.hasPendingSteps)
            assertFalse("unavailable counts as settled", it.hasUnfinalSteps)
            assertEquals("unavailable is left out of totals", 40L, it.totalSteps)
        }
        assertFalse(db.shoeDao().getWithStatsById("a")!!.hasUnfinalSteps)
    }

    @Test fun pausedSegmentIsFilledInEndToEnd() = runTest {
        val manager = SessionManager(RoomSessionStore(db), clock)
        val source = object : StepsSource {
            override suspend fun access() = StepsAccess.READY
            override suspend fun read(startUtc: Long, endUtc: Long) = StepsRead.Count(1_500)
            override suspend fun distanceAllowed() = true
            override suspend fun readDistance(startUtc: Long, endUtc: Long) = DistanceRead.Meters(1_125.5)
        }
        val reconciler = StepReconciler(store, source, clock)

        manager.start("a")
        val sessionId = manager.activeSession.first()!!.session.id
        clock.now += 30 * 60_000
        assertEquals(1_500L, reconciler.stepsSoFar(sessionId))
        manager.pause(sessionId)

        assertEquals(2, reconciler.reconcile())
        val saved = store.segments(sessionId).single()
        assertEquals(1_500L, saved.steps)
        assertEquals(PROVISIONAL, saved.stepsStatus)
        assertEquals(1_125.5, saved.distanceMeters!!, 0.0)
        assertEquals(PROVISIONAL, saved.distanceStatus)

        clock.now += StepReconciler.SETTLE_MILLIS
        reconciler.reconcile()
        store.segments(sessionId).single().let {
            assertEquals(FINAL, it.stepsStatus)
            assertEquals(FINAL, it.distanceStatus)
        }
        assertEquals(1_125.5, db.shoeDao().getWithStatsById("a")!!.totalDistanceMeters!!, 0.0)
    }

    // --- Distance (M2 Phase 4) ------------------------------------------------------------------

    @Test fun newSegmentsStartWithDistancePending() = runTest {
        SessionManager(RoomSessionStore(db), clock).start("a")
        val open = db.sessionDao().getUnendedSessions().single().let { store.segments(it.id).single() }
        assertNull(open.distanceMeters)
        assertEquals(PENDING, open.distanceStatus)
    }

    @Test fun unresolvedDistanceListFollowsItsOwnStatus() = runTest {
        segment("steps-final", 0, 1_000, 5, FINAL) // distance still PENDING
        segment("open", 2_000, null)
        sessions.insertSegment(SegmentEntity("dist-prov", "s1", 3_000, 4_000, 7, FINAL, 1, 300.0, PROVISIONAL, 1))
        sessions.insertSegment(SegmentEntity("dist-final", "s1", 5_000, 6_000, null, PENDING, null, 1.0, FINAL, 1))

        assertEquals(listOf("steps-final", "dist-prov"), store.unresolvedClosedDistanceSegments(10).map { it.id })
        assertEquals(listOf("dist-final"), store.unresolvedClosedSegments(10).map { it.id })
    }

    @Test fun saveDistanceOnlyWritesWhileStillUnresolvedAndUnchanged() = runTest {
        segment("g1", 0, 1_000, 40, FINAL)
        assertTrue(store.saveDistance("g1", 1_000, 512.25, PROVISIONAL, 2_000))
        get("g1").let {
            assertEquals(512.25, it.distanceMeters!!, 0.0)
            assertEquals(PROVISIONAL, it.distanceStatus)
            assertEquals(2_000L, it.distanceReadUtc)
            assertEquals("steps untouched", 40L, it.steps)
            assertEquals(FINAL, it.stepsStatus)
            assertNull(it.lastReadUtc)
        }
        assertFalse("end moved: stale read", store.saveDistance("g1", 999, 9.0, FINAL, 3_000))
        assertTrue(store.saveDistance("g1", 1_000, 520.0, FINAL, 3_000))
        assertFalse("final is never overwritten", store.saveDistance("g1", 1_000, 1.0, PROVISIONAL, 4_000))
        assertEquals(520.0, get("g1").distanceMeters!!, 0.0)

        segment("open", 4_000, null)
        assertFalse("open segments are never written", store.saveDistance("open", 5_000, 7.0, FINAL, 6_000))
    }

    @Test fun distanceTotalsAreNullUntilKnownAndLeaveOutUnavailable() = runTest {
        segment("g1", 0, 1_000)
        segment("g2", 2_000, 3_000)
        assertNull("no distance yet: shown as a dash, not 0", history().totalDistanceMeters)
        assertNull(db.shoeDao().getWithStatsById("a")!!.totalDistanceMeters)

        store.saveDistance("g1", 1_000, 1_000.5, PROVISIONAL, 5_000)
        store.saveDistance("g2", 3_000, null, UNAVAILABLE, 5_000)
        assertEquals(1_000.5, history().totalDistanceMeters!!, 0.0)
        assertEquals(1_000.5, db.shoeDao().getWithStatsById("a")!!.totalDistanceMeters!!, 0.0)

        store.saveDistance("g1", 1_000, 0.0, FINAL, 6_000)
        assertEquals("a real zero is kept as zero", 0.0, history().totalDistanceMeters!!, 0.0)
    }
}
