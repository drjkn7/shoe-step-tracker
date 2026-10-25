package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.db.SegmentEntity
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.data.model.StepsStatus
import io.github.drjkn7.shoestep.session.AddPastResult
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M2 Phase 6: Add past session (T50–T54). The test clock says 17 Oct 2026, 12:00 UTC, so the form
 * starts at 11:30–12:00 that day.
 */
@RunWith(AndroidJUnit4::class)
class PastSessionScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val source = FakeStepsSource(steps = 1_234, meters = 800.0)
    private val container = testContainer(stepsSource = source)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val locale get() = context.resources.configuration.locales[0]
    private val now = TestClock.nowUtcMillis()

    @After fun tearDown() = container.database.close()

    private fun addShoe(name: String, archived: Boolean = false): String = runBlocking {
        val id = (container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, "100", "EUR", null, 1)) as SaveResult.Saved).shoeId
        if (archived) container.shoeRepository.setArchived(id, true)
        id
    }

    private fun launch() = rule.setContent { ShoeStepTheme { AppNavHost(container) } }

    private fun openPair(name: String) {
        rule.clickWhenShown(name)
        rule.waitForNode(str(R.string.detail_stats_title))
    }

    private fun openForm() {
        rule.onNodeWithText(str(R.string.past_add)).performScrollTo().performClick()
        rule.waitForNode(str(R.string.title_add_past_session))
    }

    private fun waitFor(text: String) = rule.waitUntil(5_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    /** The Save button in the top bar (the form repeats it at the bottom). */
    private fun saveButton() = rule.onAllNodesWithText(str(R.string.action_save)).onFirst()

    private fun previewText(): String {
        val steps = context.resources.getQuantityString(R.plurals.steps_count, 1_234, Formatters.count(1_234, locale))
        val km = str(R.string.distance_km, Formatters.distance(800.0, io.github.drjkn7.shoestep.settings.DistanceUnit.KILOMETRES, locale))
        return str(R.string.past_preview_steps_distance, steps, km)
    }

    @Test fun addsAPastSessionThatCountsLikeAnyOther() {
        val id = addShoe("Trail Runner")
        launch()
        openPair("Trail Runner")
        openForm()
        rule.onNodeWithText("Trail Runner").assertExists() // the pair is fixed
        waitFor(previewText()) // T50: what Health Connect has for 11:30–12:00
        rule.onNodeWithText(str(R.string.past_preview_note)).assertExists()

        saveButton().assertIsEnabled().performClick()
        rule.waitForNode(str(R.string.history_added_later))
        rule.onNodeWithText(str(R.string.detail_stats_title)).assertExists() // back on the pair's page

        val session = runBlocking { container.database.sessionDao().observeHistory(id).first().single() }
        assertTrue(session.session.addedLater)
        assertEquals(30 * 60_000L, session.activeMillis)
        // T54: counted in the pair's totals straight away (provisional for a day, like any session).
        rule.waitUntil(5_000) { runBlocking { container.shoeRepository.observeShoe(id).first()!!.totalSteps } == 1_234L }
    }

    @Test fun anOverlapNamesTheOtherPairAndBlocksSave() {
        val other = addShoe("Daily Court")
        addShoe("Trail Runner")
        runBlocking {
            val dao = container.database.sessionDao()
            dao.insertSession(SessionEntity("s1", other, now - 55 * 60_000, now - 17 * 60_000, "UTC", SessionState.ENDED, EndReason.USER))
            dao.insertSegment(SegmentEntity("g1", "s1", now - 55 * 60_000, now - 17 * 60_000, 900, StepsStatus.FINAL, now))
        }
        launch()
        openPair("Trail Runner")
        openForm()

        val start = Formatters.time(now - 55 * 60_000, "UTC", locale)
        val end = Formatters.time(now - 17 * 60_000, "UTC", locale)
        waitFor(str(R.string.past_overlap, "Daily Court", start, end)) // T51
        saveButton().assertIsNotEnabled()
        assertTrue(rule.onAllNodesWithText(previewText()).fetchSemanticsNodes().isEmpty())
    }

    @Test fun anEndInTheFutureIsRefused() {
        addShoe("Trail Runner")
        launch()
        openPair("Trail Runner")
        openForm()
        waitFor(previewText())

        // End 12:00 → 12:05, five minutes after "now" (only the minutes change, so 12- and 24-hour phones agree).
        rule.onNodeWithText(Formatters.time(now, "UTC", locale)).performClick()
        rule.waitForNode(str(R.string.past_pick_end))
        rule.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[1].performTextReplacement("05")
        rule.onNode(hasText(str(R.string.action_ok)) and hasAnyAncestor(isDialog())).performClick()

        waitFor(str(R.string.past_in_future)) // T52
        saveButton().assertIsNotEnabled()
    }

    @Test fun onlyAddedLaterSessionsCanBeDeletedAfterAsking() {
        val id = addShoe("Trail Runner")
        runBlocking {
            val dao = container.database.sessionDao()
            dao.insertSession(SessionEntity("live", id, now - 5 * 3_600_000, now - 4 * 3_600_000, "UTC", SessionState.ENDED, EndReason.USER))
            assertTrue(container.sessionManager.addPast(id, now - 3 * 3_600_000, now - 2 * 3_600_000) is AddPastResult.Added)
        }
        launch()
        openPair("Trail Runner")
        rule.waitForNode(str(R.string.history_added_later))

        val addedTitle = str(
            R.string.history_row,
            Formatters.dateTime(now - 3 * 3_600_000, "UTC", locale),
            str(R.string.duration_hours_minutes, 1L, 0L),
        )
        // One Delete button: on the added session's row only (the pair's own Delete is in the closed menu).
        assertEquals(1, rule.onAllNodesWithText(str(R.string.action_delete)).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription(str(R.string.history_delete_description, addedTitle)).performScrollTo().performClick()

        rule.waitForNode(str(R.string.past_delete_title))
        rule.onNode(hasText(str(R.string.action_delete)) and hasAnyAncestor(isDialog())).performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.history_added_later)).fetchSemanticsNodes().isEmpty() }
        val left = runBlocking { container.database.sessionDao().observeHistory(id).first() }
        assertEquals(listOf("live"), left.map { it.session.id })
    }

    @Test fun archivedPairsHaveNoAddButton() {
        addShoe("Old pair", archived = true)
        launch()
        rule.onNodeWithText(str(R.string.home_show_archived)).performClick()
        openPair("Old pair")
        rule.onNodeWithText(str(R.string.history_title)).performScrollTo()
        assertTrue(rule.onAllNodesWithText(str(R.string.past_add)).fetchSemanticsNodes().isEmpty())
    }
}
