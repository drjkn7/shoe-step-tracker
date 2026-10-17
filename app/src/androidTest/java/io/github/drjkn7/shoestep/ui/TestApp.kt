package io.github.drjkn7.shoestep.ui

import androidx.test.core.app.ApplicationProvider
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.steps.StepsRead
import io.github.drjkn7.shoestep.steps.StepsSource
import java.time.ZoneId

/** Fixed "now" for UI tests: 17 Oct 2026, 12:00 UTC. */
val TestClock = object : Clock {
    override fun nowUtcMillis() = 1_792_238_400_000L
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

/** Stand-in for Health Connect in UI tests: [access] and [steps] are set by each test. */
class FakeStepsSource(
    @Volatile var access: StepsAccess = StepsAccess.READY,
    /** Returned for every read; null = no data. */
    @Volatile var steps: Long? = null,
) : StepsSource {
    override suspend fun access() = access

    override suspend fun read(startUtc: Long, endUtc: Long): StepsRead =
        if (access != StepsAccess.READY) StepsRead.Failed else steps?.let { StepsRead.Count(it) } ?: StepsRead.NoData
}

/**
 * A container backed by an in-memory database and a fake step source, so UI tests never touch
 * the real app data or depend on Health Connect being installed.
 */
fun testContainer(stepsSource: StepsSource = FakeStepsSource(), clock: Clock = TestClock): AppContainer = AppContainer(
    context = ApplicationProvider.getApplicationContext(),
    databaseFactory = ShoeStepDatabase::buildInMemory,
    clock = clock,
    stepsSourceFactory = { stepsSource },
)
