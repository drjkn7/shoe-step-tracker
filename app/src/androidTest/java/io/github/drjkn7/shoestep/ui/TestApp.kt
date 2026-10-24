package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.nfc.NfcState
import io.github.drjkn7.shoestep.settings.SettingsStore
import io.github.drjkn7.shoestep.settings.SystemNightMode
import io.github.drjkn7.shoestep.settings.ThemeMode
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.steps.StepsRead
import io.github.drjkn7.shoestep.steps.StepsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
fun testContainer(
    stepsSource: StepsSource = FakeStepsSource(),
    clock: Clock = TestClock,
    nfcState: NfcState = NfcState.ON,
    settings: SettingsStore = InMemorySettings(),
    systemNightMode: SystemNightMode = SystemNightMode {},
): AppContainer = AppContainer(
    context = ApplicationProvider.getApplicationContext(),
    databaseFactory = ShoeStepDatabase::buildInMemory,
    clock = clock,
    stepsSourceFactory = { stepsSource },
    nfcStateProvider = { nfcState },
    settingsFactory = { settings },
    systemNightModeFactory = { systemNightMode },
)

/** Settings that live only in memory, so UI tests never touch the real settings file. */
class InMemorySettings(initialCurrency: String? = null, initialTheme: ThemeMode = ThemeMode.SYSTEM) : SettingsStore {
    private val currency = MutableStateFlow(initialCurrency)
    override val defaultCurrency: Flow<String?> = currency

    override suspend fun setDefaultCurrency(code: String) {
        currency.value = code
    }

    private val theme = MutableStateFlow(initialTheme)
    override val themeMode: Flow<ThemeMode> = theme

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }
}

/**
 * Waits until a node with [text] is shown. Screens load their data from the database in the
 * background, which the test rule's idle wait does not cover, so a tap right after launch can come
 * before the row exists.
 */
fun ComposeTestRule.waitForNode(text: String, timeoutMillis: Long = 5_000) =
    waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

/** Taps the node with [text] once it is shown (see [waitForNode]). */
fun ComposeTestRule.clickWhenShown(text: String) {
    waitForNode(text)
    onNodeWithText(text).performClick()
}
