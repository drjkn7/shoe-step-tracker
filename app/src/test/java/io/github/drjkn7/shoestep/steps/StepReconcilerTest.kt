package io.github.drjkn7.shoestep.steps

import io.github.drjkn7.shoestep.core.FakeClock
import io.github.drjkn7.shoestep.data.model.StepsStatus.FINAL
import io.github.drjkn7.shoestep.data.model.StepsStatus.PENDING
import io.github.drjkn7.shoestep.data.model.StepsStatus.PROVISIONAL
import io.github.drjkn7.shoestep.data.model.StepsStatus.UNAVAILABLE
import io.github.drjkn7.shoestep.steps.StepReconciler.Companion.HISTORY_LIMIT_MILLIS
import io.github.drjkn7.shoestep.steps.StepReconciler.Companion.MAX_PER_RUN
import io.github.drjkn7.shoestep.steps.StepReconciler.Companion.SETTLE_MILLIS
import io.github.drjkn7.shoestep.steps.StepReconciler.Companion.decide
import io.github.drjkn7.shoestep.steps.StepReconciler.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepReconcilerTest {

    private val clock = FakeClock()
    private val store = FakeStepStore()
    private val source = FakeStepsSource()
    private val reconciler = StepReconciler(store, source, clock)

    private val now get() = clock.nowMillis
    private val minute = 60_000L
    private val hour = 60 * minute

    // --- decide(): one row per rule -------------------------------------------------------------

    @Test fun decisionTable() {
        val end = 1_000_000L
        data class Row(val previous: Long?, val read: StepsRead, val age: Long, val expected: Outcome?)
        val rows = listOf(
            // A count is provisional for a day, then final.
            Row(null, StepsRead.Count(500), 0, Outcome(500, PROVISIONAL)),
            Row(null, StepsRead.Count(500), SETTLE_MILLIS - 1, Outcome(500, PROVISIONAL)),
            Row(null, StepsRead.Count(500), SETTLE_MILLIS, Outcome(500, FINAL)),
            Row(400, StepsRead.Count(650), hour, Outcome(650, PROVISIONAL)), // watch synced late
            Row(400, StepsRead.Count(650), SETTLE_MILLIS, Outcome(650, FINAL)),
            // No data: keep waiting for a day, then unavailable (or keep the number already shown).
            Row(null, StepsRead.NoData, hour, null),
            Row(400, StepsRead.NoData, hour, null),
            Row(null, StepsRead.NoData, SETTLE_MILLIS, Outcome(null, UNAVAILABLE)),
            Row(400, StepsRead.NoData, SETTLE_MILLIS, Outcome(400, FINAL)),
            // Errors: try again next time, unless older than Health Connect will ever let us read.
            Row(null, StepsRead.Failed, hour, null),
            Row(null, StepsRead.Failed, SETTLE_MILLIS * 2, null),
            Row(400, StepsRead.Failed, SETTLE_MILLIS * 2, null),
            Row(null, StepsRead.Failed, HISTORY_LIMIT_MILLIS, Outcome(null, UNAVAILABLE)),
            Row(400, StepsRead.Failed, HISTORY_LIMIT_MILLIS, Outcome(400, FINAL)),
            // Clock set backwards (now before the end): treated as recent.
            Row(null, StepsRead.Count(70), -hour, Outcome(70, PROVISIONAL)),
            Row(null, StepsRead.NoData, -SETTLE_MILLIS * 3, null),
            Row(null, StepsRead.Failed, -HISTORY_LIMIT_MILLIS * 2, null),
        )
        rows.forEachIndexed { i, row ->
            assertEquals("row $i: $row", row.expected, decide(row.previous, end, row.read, end + row.age))
        }
    }

    // --- reconcile() ---------------------------------------------------------------------------

    @Test fun withoutAccessNothingIsReadOrChanged() = runTest {
        store.add("g1", now - hour, now - minute)
        for (access in listOf(StepsAccess.NEEDS_PERMISSION, StepsAccess.NEEDS_INSTALL, StepsAccess.UNAVAILABLE)) {
            source.access = access
            assertEquals(0, reconciler.reconcile())
        }
        assertTrue(source.reads.isEmpty())
        assertEquals(PENDING, store["g1"].stepsStatus)
        assertNull(store["g1"].steps)
    }

    @Test fun pendingSegmentGetsProvisionalCountForItsExactTimeSpan() = runTest {
        store.add("g1", now - hour, now - minute)
        source.answer = { _, _ -> StepsRead.Count(1_500) }

        assertEquals(1, reconciler.reconcile())

        assertEquals(listOf((now - hour) to (now - minute)), source.reads)
        assertEquals(1_500L, store["g1"].steps)
        assertEquals(PROVISIONAL, store["g1"].stepsStatus)
        assertEquals(now, store["g1"].lastReadUtc)
    }

    @Test fun provisionalCountIsUpdatedThenBecomesFinalAfterOneDay() = runTest {
        store.add("g1", now - hour, now)
        source.answer = { _, _ -> StepsRead.Count(1_000) }
        reconciler.reconcile()
        assertEquals(PROVISIONAL, store["g1"].stepsStatus)

        clock.advanceBy(2 * hour)
        source.answer = { _, _ -> StepsRead.Count(1_200) } // watch synced later
        reconciler.reconcile()
        assertEquals(1_200L, store["g1"].steps)
        assertEquals(PROVISIONAL, store["g1"].stepsStatus)

        clock.advanceBy(SETTLE_MILLIS)
        reconciler.reconcile()
        assertEquals(1_200L, store["g1"].steps)
        assertEquals(FINAL, store["g1"].stepsStatus)

        // Final counts are never read or changed again.
        source.reads.clear()
        source.answer = { _, _ -> StepsRead.Count(9_999) }
        assertEquals(0, reconciler.reconcile())
        assertTrue(source.reads.isEmpty())
        assertEquals(1_200L, store["g1"].steps)
    }

    @Test fun segmentWithNoDataWaitsThenBecomesUnavailable() = runTest {
        store.add("g1", now - hour, now)
        reconciler.reconcile()
        assertEquals(PENDING, store["g1"].stepsStatus)

        clock.advanceBy(SETTLE_MILLIS)
        reconciler.reconcile()
        assertEquals(UNAVAILABLE, store["g1"].stepsStatus)
        assertNull(store["g1"].steps)

        source.reads.clear()
        reconciler.reconcile()
        assertTrue("unavailable segments are not read again", source.reads.isEmpty())
    }

    @Test fun openSegmentsAndSettledSegmentsAreNotTouched() = runTest {
        store.add("open", now - hour, null)
        store.add("final", now - 3 * hour, now - 2 * hour, steps = 10, status = FINAL)
        store.add("gone", now - 5 * hour, now - 4 * hour, status = UNAVAILABLE)
        source.answer = { _, _ -> StepsRead.Count(777) }

        assertEquals(0, reconciler.reconcile())

        assertTrue(source.reads.isEmpty())
        assertEquals(PENDING, store["open"].stepsStatus)
        assertEquals(10L, store["final"].steps)
        assertEquals(UNAVAILABLE, store["gone"].stepsStatus)
    }

    @Test fun zeroLengthSegmentIsZeroWithoutAskingHealthConnect() = runTest {
        store.add("g1", now - hour, now - hour)
        reconciler.reconcile()
        assertTrue(source.reads.isEmpty())
        assertEquals(0L, store["g1"].steps)
        assertEquals(PROVISIONAL, store["g1"].stepsStatus)
    }

    @Test fun oneFailedReadDoesNotStopTheOthers() = runTest {
        store.add("g1", 0, 10 * minute)
        store.add("g2", 20 * minute, 30 * minute)
        clock.nowMillis = 31 * minute
        source.answer = { start, _ -> if (start == 0L) StepsRead.Failed else StepsRead.Count(42) }

        assertEquals(1, reconciler.reconcile())

        assertEquals(PENDING, store["g1"].stepsStatus)
        assertEquals(42L, store["g2"].steps)
    }

    @Test fun staleResultIsNotSavedIfTheSegmentChangedDuringTheRead() = runTest {
        store.add("g1", now - hour, now - minute)
        source.answer = { _, _ -> StepsRead.Count(300) }
        store.beforeSave = { store.add("g1", now - hour, now - minute, steps = 5, status = FINAL) }

        assertEquals(0, reconciler.reconcile())
        assertEquals(5L, store["g1"].steps)
        assertEquals(FINAL, store["g1"].stepsStatus)
    }

    @Test fun eachRunReadsAtMostTheLimitOldestFirst() = runTest {
        val total = MAX_PER_RUN + 20
        repeat(total) { i -> store.add("g%03d".format(i), i * minute, i * minute + 30_000) }
        clock.nowMillis = total * minute
        source.answer = { _, _ -> StepsRead.Count(1) }

        assertEquals(MAX_PER_RUN, reconciler.reconcile())
        assertEquals(0L, source.reads.first().first)
        assertEquals(PENDING, store["g%03d".format(total - 1)].stepsStatus)

        reconciler.reconcile()
        assertTrue(store.segments.values.none { it.stepsStatus == PENDING })
    }

    @Test fun overlappingRunsDoNotCorruptCounts() = runTest {
        repeat(10) { i -> store.add("g$i", i * minute, i * minute + 30_000) }
        clock.nowMillis = 20 * minute
        source.answer = { start, _ -> StepsRead.Count(start / minute + 1) }

        val a = async { reconciler.reconcile() }
        val b = async { reconciler.reconcile() }
        a.await()
        b.await()

        repeat(10) { i -> assertEquals(i + 1L, store["g$i"].steps) }
    }

    // --- stepsSoFar() --------------------------------------------------------------------------

    @Test fun soFarIsNullWithoutAccess() = runTest {
        store.add("g1", now - hour, null)
        source.access = StepsAccess.NEEDS_PERMISSION
        assertNull(reconciler.stepsSoFar("s1"))
        assertTrue(source.reads.isEmpty())
    }

    @Test fun soFarReadsTheOpenSegmentUpToNow() = runTest {
        store.add("g1", now - hour, null)
        source.answer = { _, _ -> StepsRead.Count(1_500) }

        assertEquals(1_500L, reconciler.stepsSoFar("s1"))
        assertEquals(listOf((now - hour) to now), source.reads)
        assertEquals("nothing is saved", PENDING, store["g1"].stepsStatus)
    }

    @Test fun soFarUsesSavedCountsAndOnlyReadsTheRest() = runTest {
        store.add("done", now - 3 * hour, now - 2 * hour, steps = 300, status = PROVISIONAL)
        store.add("waiting", now - 2 * hour + minute, now - hour)
        store.add("gone", now - 5 * hour, now - 4 * hour, status = UNAVAILABLE)
        store.add("open", now - 30 * minute, null)
        store.add("other", now - 30 * minute, null, sessionId = "s2")
        source.answer = { start, _ -> StepsRead.Count(if (start == now - 30 * minute) 200 else 50) }

        assertEquals(550L, reconciler.stepsSoFar("s1"))
        assertEquals(2, source.reads.size)
    }

    @Test fun soFarCountsNoDataAsZeroButAFailedReadAsUnknown() = runTest {
        store.add("g1", now - hour, null)
        assertEquals(0L, reconciler.stepsSoFar("s1"))

        source.answer = { _, _ -> StepsRead.Failed }
        assertNull(reconciler.stepsSoFar("s1"))
    }

    @Test fun soFarIsZeroWhenTheClockIsBeforeTheStart() = runTest {
        store.add("g1", now + hour, null)
        assertEquals(0L, reconciler.stepsSoFar("s1"))
        assertTrue(source.reads.isEmpty())
    }
}
