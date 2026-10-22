package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.data.model.StepsStatus
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The pair's page: fuller stats (T22) and the "so far" count while its session runs. */
@RunWith(AndroidJUnit4::class)
class PairStatsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val clock = object : Clock {
        @Volatile var now = TestClock.nowUtcMillis()
        override fun nowUtcMillis() = now
        override fun zoneId(): ZoneId = TestClock.zoneId()
    }
    private val source = FakeStepsSource()
    private val container = testContainer(stepsSource = source, clock = clock)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val locale get() = context.resources.configuration.locales[0]
    private fun steps(n: Int) = context.resources.getQuantityString(R.plurals.steps_count, n, Formatters.count(n.toLong(), locale))

    @After fun tearDown() = container.database.close()

    private fun addShoe(name: String, price: String): String = runBlocking {
        (container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, price, "EUR", null, 2)) as SaveResult.Saved).shoeId
    }

    /** An ended session of [minutes] with a final count of [count] steps. */
    private fun addWalk(shoeId: String, minutes: Int, count: Long) = runBlocking {
        val start = clock.now - minutes * 60_000L
        val id = "s-$shoeId-$minutes"
        container.database.sessionDao().insertSession(SessionEntity(id, shoeId, start, clock.now, "UTC", SessionState.ENDED, EndReason.USER))
        container.database.sessionDao().insertSegment(SegmentEntity("g-$id", id, start, clock.now, count, StepsStatus.FINAL, clock.now))
    }

    private fun openPair(name: String) {
        rule.setContent { ShoeStepTheme { AppNavHost(container) } }
        rule.clickWhenShown(name)
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.detail_stats_title)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun assertRow(label: Int, value: String) =
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(str(label)).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test fun newPairShowsDashesUntilItHasData() {
        addShoe("Daily trainers", price = "")
        openPair("Daily trainers")
        val dash = str(R.string.dash)
        assertRow(R.string.detail_average_steps, dash)
        assertRow(R.string.detail_walking_time, str(R.string.duration_minutes, 0))
        assertRow(R.string.detail_last_worn, dash)
        assertRow(R.string.detail_cost_per_1000, dash) // T22: no price → dash
    }

    @Test fun statsSumUpEndedSessions() {
        val id = addShoe("Daily trainers", price = "100")
        addWalk(id, minutes = 90, count = 6_000)
        addWalk(id, minutes = 30, count = 2_000)
        openPair("Daily trainers")
        assertRow(R.string.detail_average_steps, Formatters.count(4_000, locale))
        assertRow(R.string.detail_walking_time, str(R.string.duration_hours_minutes, 2, 0))
        assertRow(R.string.detail_last_worn, Formatters.date(Formatters.localEpochDay(clock.now), locale))
    }

    @Test fun freePairCostsZeroOncePaidOff() {
        val id = addShoe("Gift pair", price = "0")
        addWalk(id, minutes = 10, count = 1_000)
        openPair("Gift pair")
        // T22: free shoes cost 0 (not a dash) once they have steps.
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("0.00", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun runningPairPageShowsStepsSoFar() {
        val id = addShoe("Daily trainers", price = "")
        runBlocking { container.sessionManager.start(id) }
        clock.now += 30 * 60_000L
        source.steps = 1_500
        openPair("Daily trainers")
        waitForText(str(R.string.session_card_steps_so_far, steps(1_500)))
        // This is the pair's own page (Home shows the count on its card too).
        rule.onNodeWithText(str(R.string.detail_stats_title)).assertExists()
    }
}
