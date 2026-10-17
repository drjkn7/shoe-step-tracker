package io.github.drjkn7.shoestep.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ClockTest {

    @Test fun fakeClockOnlyMovesWhenAdvanced() {
        val clock = FakeClock(nowMillis = 1_000)
        assertEquals(1_000, clock.nowUtcMillis())
        clock.advanceBy(2_500)
        assertEquals(3_500, clock.nowUtcMillis())
    }

    @Test fun fakeClockZoneCanChange() {
        val clock = FakeClock()
        clock.zone = ZoneId.of("Europe/London")
        assertEquals(ZoneId.of("Europe/London"), clock.zoneId())
    }

    @Test fun systemClockIsCloseToRealTime() {
        val before = System.currentTimeMillis()
        val now = SystemWallClock.nowUtcMillis()
        val after = System.currentTimeMillis()
        assertTrue(now in before..after)
    }
}
