package io.github.drjkn7.shoestep.ui

import androidx.test.core.app.ApplicationProvider
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import java.time.ZoneId

/** Fixed "now" for UI tests: 17 Oct 2026, 12:00 UTC. */
val TestClock = object : Clock {
    override fun nowUtcMillis() = 1_792_238_400_000L
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

/** A container backed by an in-memory database, so UI tests never touch the real app data. */
fun testContainer(): AppContainer = AppContainer(
    context = ApplicationProvider.getApplicationContext(),
    databaseFactory = ShoeStepDatabase::buildInMemory,
    clock = TestClock,
)
