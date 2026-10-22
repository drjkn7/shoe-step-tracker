package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.steps.RationaleScreen
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Step counting on screen: the Home permission banner, "so far" counts, labels and the rationale. */
@RunWith(AndroidJUnit4::class)
class StepsScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val source = FakeStepsSource()

    /** Starts at [TestClock]'s time; tests move it so sessions have a real length. */
    private val clock = object : Clock {
        @Volatile var now = TestClock.nowUtcMillis()
        override fun nowUtcMillis() = now
        override fun zoneId(): ZoneId = TestClock.zoneId()
    }
    private val container: AppContainer = testContainer(source, clock)
    private fun walkFor(minutes: Int) {
        clock.now += minutes * 60_000L
    }
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun count(n: Int) = Formatters.count(n.toLong(), context.resources.configuration.locales[0])
    private fun steps(n: Int) = context.resources.getQuantityString(R.plurals.steps_count, n, count(n))

    @After fun tearDown() = container.database.close()

    private fun launch() = rule.setContent { ShoeStepTheme { AppNavHost(container) } }

    private fun addShoe(name: String): String = runBlocking {
        val result = container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, "", "EUR", null, 2))
        (result as SaveResult.Saved).shoeId
    }

    private fun waitForText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun assertNoText(text: String) =
        assertTrue("unexpected: $text", rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())

    @Test fun bannerAsksForPermissionWithAnAllowButton() {
        source.access = StepsAccess.NEEDS_PERMISSION
        launch()
        waitForText(str(R.string.steps_access_title))
        rule.onNodeWithText(str(R.string.steps_access_permission_body)).assertIsDisplayed()
        rule.onNode(hasText(str(R.string.steps_access_allow)) and hasClickAction()).assertIsDisplayed()
    }

    @Test fun bannerOffersInstallWhenHealthConnectIsMissing() {
        source.access = StepsAccess.NEEDS_INSTALL
        launch()
        waitForText(str(R.string.steps_access_install_body))
        rule.onNode(hasText(str(R.string.steps_access_install)) and hasClickAction()).assertIsDisplayed()
        assertNoText(str(R.string.steps_access_allow))
    }

    @Test fun bannerExplainsWhenStepsAreUnavailable() {
        source.access = StepsAccess.UNAVAILABLE
        launch()
        waitForText(str(R.string.steps_access_unavailable_body))
        assertNoText(str(R.string.steps_access_allow))
        assertNoText(str(R.string.steps_access_install))
    }

    @Test fun noBannerWhenStepsCanBeRead() {
        addShoe("Daily trainers")
        launch()
        waitForText("Daily trainers")
        rule.waitForIdle()
        assertNoText(str(R.string.steps_access_title))
    }

    @Test fun runningSessionCardShowsStepsSoFar() {
        val id = addShoe("Daily trainers")
        runBlocking { container.sessionManager.start(id) }
        walkFor(30)
        source.steps = 1_500
        launch()
        waitForText(str(R.string.session_card_steps_so_far, steps(1_500)))
    }

    @Test fun sessionCardSaysCountedLaterWithoutPermission() {
        val id = addShoe("Daily trainers")
        runBlocking { container.sessionManager.start(id) }
        source.access = StepsAccess.NEEDS_PERMISSION
        launch()
        waitForText(str(R.string.session_card_steps_later))
    }

    @Test fun historyShowsCountedLaterThenAboutOnceCounted() {
        val id = addShoe("Daily trainers")
        runBlocking {
            container.sessionManager.start(id)
            walkFor(30)
            container.sessionManager.end(container.sessionManager.activeSession.first()!!.session.id)
        }
        launch()
        rule.clickWhenShown("Daily trainers")
        val pending = str(R.string.history_steps_pending, steps(0))
        waitForText(pending)

        source.steps = 1_234
        runBlocking { container.stepReconciler.reconcile() }
        val about = str(R.string.steps_about, steps(1_234))
        waitForText(about)
        rule.onNodeWithText(about, substring = true).performScrollTo().assertIsDisplayed()
        // The shoe's total also says "about" while the count may still change.
        rule.onNode(hasText(str(R.string.detail_total_steps)) and hasText(str(R.string.steps_about, count(1_234))))
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun rationaleExplainsStepUseAndCloses() {
        var closed = false
        rule.setContent { ShoeStepTheme { RationaleScreen(onClose = { closed = true }) } }
        rule.onNodeWithText(str(R.string.rationale_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.rationale_stays)).assertExists()
        rule.onNodeWithText(str(R.string.rationale_close)).performScrollTo().performClick()
        assertTrue(closed)
    }
}
