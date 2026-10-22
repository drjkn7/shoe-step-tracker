package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Manual session controls on Shoe detail and Home (T08, T20). */
@RunWith(AndroidJUnit4::class)
class SessionScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @Before fun setUp() {
        container = testContainer()
    }

    @After fun tearDown() = container.database.close()

    private fun launch(fontScale: Float = 1f) {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                ShoeStepTheme { AppNavHost(container) }
            }
        }
    }

    private fun addShoe(name: String): String = runBlocking {
        val result = container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, "", "EUR", null, 2))
        (result as SaveResult.Saved).shoeId
    }

    private fun active(): ActiveSessionWithShoe? = runBlocking { container.sessionManager.activeSession.first() }

    private fun button(label: Int): SemanticsNodeInteraction =
        rule.onNode(hasText(str(label)) and hasClickAction() and !hasAnyAncestor(isDialog()))

    private fun dialogButton(label: Int): SemanticsNodeInteraction =
        rule.onNode(hasText(str(label)) and hasClickAction() and hasAnyAncestor(isDialog()))

    /** Home has no scrolling parent around its card; Shoe detail does. */
    private fun SemanticsNodeInteraction.scrolledTo() = apply { runCatching { performScrollTo() } }

    private fun tap(label: Int) = button(label).scrolledTo().performClick()

    private fun waitForText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun startTime(): String {
        val session = active()!!.session
        return Formatters.time(session.startUtc, session.zoneId, Locale.getDefault())
    }

    @Test fun startPauseResumeAndEndFromShoeDetail() {
        addShoe("Daily trainers")
        launch()
        rule.clickWhenShown("Daily trainers")
        rule.onNodeWithText(str(R.string.history_empty)).performScrollTo().assertIsDisplayed()

        tap(R.string.session_start)
        waitForText(str(R.string.session_pause))
        rule.onNodeWithText(str(R.string.detail_session_running, startTime())).assertIsDisplayed()
        assertEquals(SessionState.RUNNING, active()?.session?.state)

        tap(R.string.session_pause)
        waitForText(str(R.string.session_resume))
        rule.onNodeWithText(str(R.string.detail_session_paused)).assertIsDisplayed()
        assertEquals(SessionState.PAUSED, active()?.session?.state)

        tap(R.string.session_resume)
        waitForText(str(R.string.session_pause))

        tap(R.string.session_end)
        rule.onNodeWithText(str(R.string.session_end_title)).assertIsDisplayed()
        dialogButton(R.string.session_end).performClick()
        waitForText(str(R.string.session_start))
        assertNull(active())
        // The history row ("…, 12:00 · 0 min", PNG 08), not the Walking time stat ("0 min").
        rule.onNodeWithText(str(R.string.history_row, "", str(R.string.duration_minutes, 0)), substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun cancellingEndKeepsTheSessionRunning() {
        val id = addShoe("Trail shoes")
        runBlocking { container.sessionManager.start(id) }
        launch()
        rule.clickWhenShown("Trail shoes")
        tap(R.string.session_end)
        dialogButton(R.string.action_cancel).performClick()
        assertEquals(SessionState.RUNNING, active()?.session?.state)
        button(R.string.session_pause).performScrollTo().assertIsDisplayed()
    }

    @Test fun homeCardShowsTheActiveSessionAndControlsIt() {
        val id = addShoe("Office shoes")
        runBlocking { container.sessionManager.start(id) }
        launch()
        rule.onNodeWithText(str(R.string.session_card_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.session_card_running, "Office shoes", startTime())).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_running_badge)).assertIsDisplayed()

        tap(R.string.session_pause)
        waitForText(str(R.string.session_card_paused, "Office shoes"))
        rule.onNodeWithText(str(R.string.home_paused_badge)).assertIsDisplayed()

        tap(R.string.session_resume)
        waitForText(str(R.string.session_card_running, "Office shoes", startTime()))

        tap(R.string.session_end)
        dialogButton(R.string.session_end).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(str(R.string.session_card_title)).fetchSemanticsNodes().isEmpty()
        }
        assertNull(active())
    }

    @Test fun homeCardOpensThePairItBelongsTo() {
        val id = addShoe("Card target")
        runBlocking { container.sessionManager.start(id) }
        launch()
        rule.onNodeWithText(str(R.string.session_card_title)).performClick()
        waitForText(str(R.string.history_title))
        rule.onNodeWithText(str(R.string.history_in_progress), substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun startingAnotherPairAsksToSwitch() {
        val first = addShoe("First pair")
        addShoe("Second pair")
        runBlocking { container.sessionManager.start(first) }
        launch()
        rule.clickWhenShown("Second pair")
        rule.onNodeWithText(str(R.string.detail_other_active, "First pair")).performScrollTo().assertIsDisplayed()

        tap(R.string.session_start)
        rule.onNodeWithText(str(R.string.session_switch_title)).assertIsDisplayed()
        dialogButton(R.string.action_cancel).performClick()
        assertEquals("First pair", active()?.shoeName)

        tap(R.string.session_start)
        dialogButton(R.string.session_switch_confirm).performClick()
        waitForText(str(R.string.session_pause))
        assertEquals("Second pair", active()?.shoeName)
        val firstHistory = runBlocking { container.sessionManager.observeHistory(first).first() }
        assertEquals(EndReason.SWITCH, firstHistory.single().session.endReason)
    }

    @Test fun archivedPairHasNoStartButton() {
        val id = addShoe("Retired")
        runBlocking { container.shoeRepository.setArchived(id, true) }
        launch()
        rule.onNodeWithText(str(R.string.home_show_archived)).performClick()
        rule.clickWhenShown("Retired")
        waitForText(str(R.string.detail_archived_note))
        assertTrue(rule.onAllNodesWithText(str(R.string.session_start)).fetchSemanticsNodes().isEmpty())
    }

    @Test fun archivingAPairWithASessionAsksAndEndsIt() {
        val id = addShoe("Busy pair")
        runBlocking { container.sessionManager.start(id) }
        launch()
        rule.clickWhenShown("Busy pair")
        rule.onNodeWithContentDescription(str(R.string.action_more)).performClick()
        rule.onNodeWithText(str(R.string.action_archive)).performClick()
        rule.onNodeWithText(str(R.string.archive_running_body)).assertIsDisplayed()
        dialogButton(R.string.action_archive).performClick()

        waitForText(str(R.string.detail_archived_note))
        assertNull(active())
    }

    @Test fun sessionButtonsAreLargeEnoughAt200PercentFont() {
        val id = addShoe("Big text")
        runBlocking { container.sessionManager.start(id) }
        launch(fontScale = 2f)
        val minPx = with(rule.density) { 48.dp.toPx() } - 1f
        for (label in listOf(R.string.session_pause, R.string.session_end)) {
            val node = button(label).scrolledTo().fetchSemanticsNode()
            val touch = node.touchBoundsInRoot
            assertTrue("${str(label)} too small: ${touch.width} x ${touch.height}px", touch.width >= minPx && touch.height >= minPx)
        }
        rule.clickWhenShown("Big text")
        tap(R.string.session_pause)
        waitForText(str(R.string.session_resume))
    }
}
