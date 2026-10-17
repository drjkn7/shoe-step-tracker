package io.github.drjkn7.shoestep.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebouncerTest {

    private val clock = FakeClock()
    private val debouncer = Debouncer(clock)

    /** Feeds reads of [key] at the given seconds (from the clock's start) and returns which were accepted. */
    private fun readsAt(vararg seconds: Double, key: String = "tag-A"): List<Boolean> {
        val start = clock.nowMillis
        return seconds.map { s ->
            clock.nowMillis = start + (s * 1_000).toLong()
            debouncer.accept(key)
        }
    }

    @Test fun firstReadIsAccepted() = assertTrue(debouncer.accept("tag-A"))

    @Test fun repeatWithinTwoSecondsIsIgnored() =
        assertEquals(listOf(true, false), readsAt(0.0, 1.9))

    @Test fun readExactlyTwoSecondsLaterIsAccepted() =
        assertEquals(listOf(true, true), readsAt(0.0, 2.0))

    @Test fun phoneRestingOnTheTagActsOnlyOnce() =
        // Reads every second while the phone stays on the tag: each ignored read restarts the timer.
        assertEquals(listOf(true, false, false, false, false), readsAt(0.0, 1.0, 2.0, 3.0, 4.0))

    @Test fun tagWorksAgainAfterTheTimerRunsOutWithoutReads() =
        assertEquals(listOf(true, false, true), readsAt(0.0, 1.5, 3.5))

    @Test fun differentPairsHaveSeparateTimers() {
        assertTrue(debouncer.accept("tag-A"))
        assertTrue(debouncer.accept("tag-B"))
        assertFalse(debouncer.accept("tag-A"))
    }

    @Test fun clockJumpingBackwardsDoesNotBlockTheTag() {
        assertTrue(debouncer.accept("tag-A"))
        clock.advanceBy(-60 * 60_000L)
        assertTrue(debouncer.accept("tag-A"))
        clock.advanceBy(500)
        assertFalse(debouncer.accept("tag-A")) // and the guard works normally from the new time
    }

    @Test fun windowCanBeConfigured() {
        val quick = Debouncer(clock, windowMillis = 500)
        assertTrue(quick.accept("tag-A"))
        clock.advanceBy(500)
        assertTrue(quick.accept("tag-A"))
        assertEquals(2_000L, Debouncer.DEFAULT_WINDOW_MILLIS)
    }
}
