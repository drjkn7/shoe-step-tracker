package io.github.drjkn7.shoestep.core

import java.time.ZoneId

/** Test clock whose time only moves when a test moves it. */
class FakeClock(
    var nowMillis: Long = 1_760_000_000_000L,
    var zone: ZoneId = ZoneId.of("UTC"),
) : Clock {
    override fun nowUtcMillis(): Long = nowMillis

    override fun zoneId(): ZoneId = zone

    fun advanceBy(millis: Long) {
        nowMillis += millis
    }
}
