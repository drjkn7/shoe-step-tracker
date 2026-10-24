package io.github.drjkn7.shoestep.steps

import io.github.drjkn7.shoestep.core.FakeClock
import io.github.drjkn7.shoestep.data.model.StepsStatus.FINAL
import io.github.drjkn7.shoestep.data.model.StepsStatus.PENDING
import io.github.drjkn7.shoestep.data.model.StepsStatus.PROVISIONAL
import io.github.drjkn7.shoestep.data.model.StepsStatus.UNAVAILABLE
import io.github.drjkn7.shoestep.steps.StepReconciler.Companion.HISTORY_LIMIT_MILLIS
import io.github.drjkn7.shoestep.steps.StepReconciler.Companion.MAX_PER_RUN
import io.github.drjkn7.shoestep.steps.StepReconciler.Companion.SETTLE_MILLIS
import io.github.drjkn7.shoestep.steps.StepReconciler.Companion.decideDistance
import io.github.drjkn7.shoestep.steps.StepReconciler.DistanceOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2 Phase 4: distance follows the same rules as steps (T33–T36), separately from them. */
class DistanceReconcilerTest {

    private val clock = FakeClock()
    private val store = FakeStepStore()
    private val source = FakeStepsSource(distanceAllowed = true)
    private val reconciler = StepReconciler(store, source, clock)

    private val now get() = clock.nowMillis
    private val minute = 60_000L
    private val hour = 60 * minute

    // --- decideDistance(): the same table as steps (T35) -----------------------------------------

    @Test fun decisionTable() {
        val end = 1_000_000L
        data class Row(val previous: Double?, val read: DistanceRead, val age: Long, val expected: DistanceOutcome?)
        val rows = listOf(
            Row(null, DistanceRead.Meters(2_400.0), 0, DistanceOutcome(2_400.0, PROVISIONAL)),
            Row(null, DistanceRead.Meters(2_400.0), SETTLE_MILLIS - 1, DistanceOutcome(2_400.0, PROVISIONAL)),
            Row(null, DistanceRead.Meters(2_400.0), SETTLE_MILLIS, DistanceOutcome(2_400.0, FINAL)),
            Row(2_000.0, DistanceRead.Meters(2_600.0), hour, DistanceOutcome(2_600.0, PROVISIONAL)), // watch synced late
            Row(2_600.0, DistanceRead.Meters(2_300.0), hour, DistanceOutcome(2_300.0, PROVISIONAL)), // may go down
            Row(null, DistanceRead.NoData, hour, null),
            Row(null, DistanceRead.NoData, SETTLE_MILLIS, DistanceOutcome(null, UNAVAILABLE)), // T34: "–", not 0
            Row(1_500.0, DistanceRead.NoData, SETTLE_MILLIS, DistanceOutcome(1_500.0, FINAL)),
            Row(null, DistanceRead.Failed, SETTLE_MILLIS, null),
            Row(null, DistanceRead.Failed, HISTORY_LIMIT_MILLIS, DistanceOutcome(null, UNAVAILABLE)),
            Row(1_500.0, DistanceRead.Failed, HISTORY_LIMIT_MILLIS, DistanceOutcome(1_500.0, FINAL)),
            Row(null, DistanceRead.Meters(50.0), -hour, DistanceOutcome(50.0, PROVISIONAL)), // clock set back
        )
        rows.forEachIndexed { i, row ->
            assertEquals("row $i: $row", row.expected, decideDistance(row.previous, end, row.read, end + row.age))
        }
    }

    // --- reconcile() ------------------------------------------------------------------------------

    @Test fun distanceIsReadForTheExactTimeSpanAndSavedBesideTheSteps() = runTest {
        store.add("g1", now - hour, now - minute)
        source.answer = { _, _ -> StepsRead.Count(3_000) }
        source.distanceAnswer = { _, _ -> DistanceRead.Meters(2_250.5) }

        assertEquals(2, reconciler.reconcile())

        assertEquals(listOf((now - hour) to (now - minute)), source.distanceReads)
        assertEquals(3_000L, store["g1"].steps)
        assertEquals(2_250.5, store["g1"].distanceMeters!!, 0.0)
        assertEquals(PROVISIONAL, store["g1"].distanceStatus)
        assertEquals(now, store["g1"].distanceReadUtc)
    }

    @Test fun refusingDistanceLeavesStepsWorkingAndDistanceWaiting() = runTest { // T33
        source.distanceAllowed = false
        store.add("g1", now - hour, now - minute)
        source.answer = { _, _ -> StepsRead.Count(3_000) }

        assertEquals(1, reconciler.reconcile())

        assertEquals(3_000L, store["g1"].steps)
        assertTrue(source.distanceReads.isEmpty())
        assertEquals(PENDING, store["g1"].distanceStatus)
        assertNull(store["g1"].distanceMeters)

        // Allowed later: the waiting segment is filled in (backfill).
        source.distanceAllowed = true
        source.distanceAnswer = { _, _ -> DistanceRead.Meters(2_000.0) }
        reconciler.reconcile()
        assertEquals(2_000.0, store["g1"].distanceMeters!!, 0.0)
        assertEquals(3_000L, store["g1"].steps)
    }

    @Test fun distanceDoesNotNeedTheStepsPermission() = runTest {
        source.access = StepsAccess.NEEDS_PERMISSION
        store.add("g1", now - hour, now - minute)
        source.distanceAnswer = { _, _ -> DistanceRead.Meters(800.0) }

        assertEquals(1, reconciler.reconcile())

        assertTrue(source.reads.isEmpty())
        assertEquals(PENDING, store["g1"].stepsStatus)
        assertEquals(800.0, store["g1"].distanceMeters!!, 0.0)
    }

    @Test fun existingSessionsAreBackfilledAndTooOldOnesSettle() = runTest { // T37 backfill
        store.add("recent", now - 3 * 24 * hour, now - 3 * 24 * hour + hour, steps = 4_000, status = FINAL)
        store.add("old", now - 40 * 24 * hour, now - 40 * 24 * hour + hour, steps = 5_000, status = FINAL)
        source.distanceAnswer = { start, _ -> if (start == now - 3 * 24 * hour) DistanceRead.Meters(3_100.0) else DistanceRead.Failed }

        reconciler.reconcile()

        assertTrue("final steps are never read again", source.reads.isEmpty())
        assertEquals(3_100.0, store["recent"].distanceMeters!!, 0.0)
        assertEquals("settled at once: ended over a day ago", FINAL, store["recent"].distanceStatus)
        assertEquals(UNAVAILABLE, store["old"].distanceStatus)
        assertEquals(5_000L, store["old"].steps)
    }

    @Test fun zeroLengthSegmentIsZeroMetresWithoutAskingHealthConnect() = runTest {
        store.add("g1", now - hour, now - hour)
        reconciler.reconcile()
        assertTrue(source.distanceReads.isEmpty())
        assertEquals(0.0, store["g1"].distanceMeters!!, 0.0)
    }

    @Test fun finalAndUnavailableDistancesAreNotReadAgain() = runTest {
        store.add("final", now - 3 * hour, now - 2 * hour, meters = 1_000.0, distanceStatus = FINAL)
        store.add("gone", now - 5 * hour, now - 4 * hour, distanceStatus = UNAVAILABLE)
        store.add("open", now - hour, null)
        source.distanceAnswer = { _, _ -> DistanceRead.Meters(9_999.0) }

        reconciler.reconcile()

        assertTrue(source.distanceReads.isEmpty())
        assertEquals(1_000.0, store["final"].distanceMeters!!, 0.0)
        assertNull(store["gone"].distanceMeters)
        assertEquals(PENDING, store["open"].distanceStatus)
    }

    @Test fun staleDistanceIsNotSavedIfTheSegmentChangedDuringTheRead() = runTest {
        source.access = StepsAccess.NEEDS_PERMISSION
        store.add("g1", now - hour, now - minute)
        source.distanceAnswer = { _, _ -> DistanceRead.Meters(300.0) }
        store.beforeSave = { store.add("g1", now - hour, now - minute, meters = 5.0, distanceStatus = FINAL) }

        assertEquals(0, reconciler.reconcile())
        assertEquals(5.0, store["g1"].distanceMeters!!, 0.0)
    }

    @Test fun stepsAndDistanceShareOneReadLimitPerRun() = runTest {
        val total = MAX_PER_RUN + 20
        repeat(total) { i -> store.add("g%03d".format(i), i * minute, i * minute + 30_000) }
        clock.nowMillis = total * minute
        source.answer = { _, _ -> StepsRead.Count(1) }
        source.distanceAnswer = { _, _ -> DistanceRead.Meters(1.0) }

        reconciler.reconcile()
        assertEquals(MAX_PER_RUN, source.reads.size + source.distanceReads.size)
        assertEquals("half each when both are allowed", MAX_PER_RUN / 2, source.distanceReads.size)
        assertEquals("oldest first", 0L, source.distanceReads.first().first)

        // Counts made today are re-checked every run, yet distance still gets its share each time.
        repeat(4) { reconciler.reconcile() }
        assertTrue(
            "every segment has steps and distance after a few runs",
            store.segments.values.none { it.stepsStatus == PENDING || it.distanceStatus == PENDING },
        )
    }

    @Test fun withoutDistanceStepsMayUseEveryRead() = runTest {
        source.distanceAllowed = false
        repeat(MAX_PER_RUN + 5) { i -> store.add("g%03d".format(i), i * minute, i * minute + 30_000) }
        clock.nowMillis = (MAX_PER_RUN + 5) * minute
        source.answer = { _, _ -> StepsRead.Count(1) }

        reconciler.reconcile()
        assertEquals(MAX_PER_RUN, source.reads.size)
    }

    @Test fun aFailedDistanceReadIsTriedAgainNextRun() = runTest {
        store.add("g1", now - hour, now - minute)
        source.distanceAnswer = { _, _ -> DistanceRead.Failed }
        reconciler.reconcile()
        assertEquals(PENDING, store["g1"].distanceStatus)

        source.distanceAnswer = { _, _ -> DistanceRead.Meters(640.0) }
        reconciler.reconcile()
        assertEquals(640.0, store["g1"].distanceMeters!!, 0.0)
    }
}
