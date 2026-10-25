package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import io.github.drjkn7.shoestep.settings.DistanceUnit
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** M2 Phase 5: distance on Home, the pair's page and History; km ↔ miles; the "Show distance" card (T31–T34, T39). */
@RunWith(AndroidJUnit4::class)
class DistanceScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val clock = object : Clock {
        @Volatile var now = TestClock.nowUtcMillis()
        override fun nowUtcMillis() = now
        override fun zoneId(): ZoneId = TestClock.zoneId()
    }
    private val source = FakeStepsSource()
    private val settings = InMemorySettings()
    private val container = testContainer(stepsSource = source, clock = clock, settings = settings)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val locale get() = context.resources.configuration.locales[0]

    /** What TalkBack reads (and tests find): "6.0 kilometres", "About 1.2 kilometres". */
    private fun spoken(meters: Double, unit: DistanceUnit = DistanceUnit.KILOMETRES, about: Boolean = false): String {
        val text = str(
            if (unit == DistanceUnit.MILES) R.string.distance_mi_spoken else R.string.distance_km_spoken,
            Formatters.distance(meters, unit, locale),
        )
        return if (about) str(R.string.steps_about, text) else text
    }

    @After fun tearDown() = container.database.close()

    private fun addShoe(name: String, price: String): String = runBlocking {
        (container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, price, "EUR", null, 2)) as SaveResult.Saved).shoeId
    }

    /** An ended session of [minutes] with final steps and a distance in [meters] (null = none, settled). */
    private fun addWalk(shoeId: String, minutes: Int, steps: Long, meters: Double?) = runBlocking {
        val start = clock.now - minutes * 60_000L
        val id = "s-$shoeId-$minutes"
        container.database.sessionDao().insertSession(SessionEntity(id, shoeId, start, clock.now, "UTC", SessionState.ENDED, EndReason.USER))
        container.database.sessionDao().insertSegment(
            SegmentEntity(
                "g-$id", id, start, clock.now, steps, StepsStatus.FINAL, clock.now,
                distanceMeters = meters,
                distanceStatus = if (meters == null) StepsStatus.UNAVAILABLE else StepsStatus.FINAL,
                distanceReadUtc = clock.now,
            ),
        )
    }

    private fun launch() = rule.setContent { ShoeStepTheme { AppNavHost(container) } }

    private fun openPair(name: String) {
        rule.clickWhenShown(name)
        rule.waitForNode(str(R.string.detail_stats_title))
    }

    private fun waitFor(text: String) = rule.waitUntil(5_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun gone(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()

    @Test fun distanceShowsOnHomeThePairPageAndHistory() {
        val id = addShoe("Daily trainers", price = "120")
        addWalk(id, minutes = 90, steps = 6_000, meters = 4_000.0)
        addWalk(id, minutes = 30, steps = 2_000, meters = 2_000.0)
        launch()
        waitFor(spoken(6_000.0)) // Home row: total under the steps (T31)

        openPair("Daily trainers")
        waitFor(spoken(6_000.0)) // Total distance
        waitFor(spoken(3_000.0)) // under "Average per session"
        rule.onNodeWithText(str(R.string.detail_cost_per_km)).assertExists()
        waitFor(Formatters.costPerDistance(java.math.BigDecimal("20.0000"), "EUR", locale)!!) // €120 / 6 km (T39)
        waitFor(spoken(4_000.0)) // History rows
        waitFor(spoken(2_000.0))
    }

    @Test fun missingDistanceIsADashNeverZero() {
        val id = addShoe("Gift pair", price = "50")
        addWalk(id, minutes = 20, steps = 2_000, meters = null)
        launch()
        openPair("Gift pair")
        // T34: no distance → dash for the total, the average and cost per km; never "0.0 km".
        // History rows leave the distance line out instead (design review, 25 Oct 2026).
        val dash = str(R.string.dash)
        rule.waitUntil(5_000) { rule.onAllNodesWithText(dash).fetchSemanticsNodes().size == 3 }
        rule.onNodeWithText(str(R.string.history_title)).performScrollTo()
        assertEquals(3, rule.onAllNodesWithText(dash).fetchSemanticsNodes().size)
        assertTrue(gone(spoken(0.0)))
    }

    @Test fun switchingToMilesUpdatesEveryScreenAndKeepsTheData() {
        val id = addShoe("Daily trainers", price = "120")
        addWalk(id, minutes = 60, steps = 5_000, meters = 4_828.032) // exactly 3 miles
        launch()
        waitFor(spoken(4_828.032)) // 4.8 km

        rule.onNodeWithContentDescription(str(R.string.nav_settings)).performClick()
        rule.onNodeWithText(str(R.string.units_miles)).performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { settings.distanceUnit.first() } == DistanceUnit.MILES }
        rule.onAllNodesWithContentDescription(str(R.string.nav_back))[0].performClick()

        waitFor(spoken(4_828.032, DistanceUnit.MILES)) // Home: 3.0 miles
        openPair("Daily trainers")
        waitFor(spoken(4_828.032, DistanceUnit.MILES))
        rule.onNodeWithText(str(R.string.detail_cost_per_mile)).assertExists()
        waitFor(Formatters.costPerDistance(java.math.BigDecimal("40.0000"), "EUR", locale)!!) // €120 / 3 mi

        // T32: the stored metres never change.
        val stored = runBlocking { container.database.sessionDao().getSegments("s-$id-60").single().distanceMeters }
        assertEquals(4_828.032, stored!!, 0.0)
    }

    @Test fun runningSessionShowsDistanceSoFar() {
        val id = addShoe("Daily trainers", price = "")
        runBlocking { container.sessionManager.start(id) }
        clock.now += 30 * 60_000L
        source.steps = 1_500
        source.meters = 1_180.0
        launch()
        waitFor(spoken(1_180.0, about = true)) // Home card: "About 1.2 kilometres"
    }

    @Test fun showDistanceCardAppearsUntilNotNow() {
        source.distanceAllowed = false
        addShoe("Daily trainers", price = "")
        launch()
        rule.waitForNode(str(R.string.distance_card_title))
        rule.onNodeWithText(str(R.string.distance_card_body)).assertExists()

        rule.onNodeWithText(str(R.string.distance_card_not_now)).performClick()
        rule.waitUntil(5_000) { gone(str(R.string.distance_card_title)) }
        assertTrue(runBlocking { settings.distanceCardDismissed.first() })

        // Settings still offers it.
        rule.onNodeWithContentDescription(str(R.string.nav_settings)).performClick()
        rule.waitForNode(str(R.string.settings_allow_distance))
    }

    @Test fun noCardOnceDistanceIsAllowed() {
        addShoe("Daily trainers", price = "")
        launch()
        rule.waitForNode("Daily trainers")
        rule.waitForIdle()
        assertTrue(gone(str(R.string.distance_card_title)))
    }
}
