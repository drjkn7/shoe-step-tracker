package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The changes chosen in the design review of 25 Oct 2026 (quiet zeros, notes, order, wording). */
@RunWith(AndroidJUnit4::class)
class DesignPolishScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val source = FakeStepsSource(steps = 0)
    private val container = testContainer(stepsSource = source)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val locale get() = context.resources.configuration.locales[0]
    private val now = TestClock.nowUtcMillis()

    @After fun tearDown() = container.database.close()

    private fun addShoe(name: String): String = runBlocking {
        (container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, "129", "GBP", null, 1)) as SaveResult.Saved).shoeId
    }

    /** An ended session; [steps] null = still counted later. */
    private fun addWalk(shoeId: String, id: String, startAgoMin: Int, minutes: Int, steps: Long?) = runBlocking {
        val start = now - startAgoMin * 60_000L
        val end = start + minutes * 60_000L
        val dao = container.database.sessionDao()
        dao.insertSession(SessionEntity(id, shoeId, start, end, "UTC", SessionState.ENDED, EndReason.USER))
        dao.insertSegment(
            SegmentEntity(
                "g-$id", id, start, end, steps, if (steps == null) StepsStatus.PENDING else StepsStatus.FINAL, now,
                distanceStatus = StepsStatus.UNAVAILABLE,
            ),
        )
    }

    private fun launch() = rule.setContent { ShoeStepTheme { AppNavHost(container) } }

    private fun gone(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()

    @Test fun zerosAreQuietOnHomeAndInHistory() {
        val id = addShoe("Fresh pair")
        addWalk(id, "s1", startAgoMin = 120, minutes = 20, steps = 0)
        launch()
        rule.waitForNode(str(R.string.steps_none_yet)) // Home row, instead of "0 steps"
        assertTrue(gone(context.resources.getQuantityString(R.plurals.steps_count, 0, "0")))

        rule.clickWhenShown("Fresh pair")
        rule.waitForNode(str(R.string.detail_stats_title)) // sentence case, found by its own wording
        rule.onNodeWithText(str(R.string.history_title)).performScrollTo()
        rule.waitForNode(str(R.string.history_no_steps))
    }

    @Test fun countedLaterIsANoteUnderTheNumber() {
        val id = addShoe("Daily pair")
        addWalk(id, "s1", startAgoMin = 300, minutes = 30, steps = 677)
        addWalk(id, "s2", startAgoMin = 120, minutes = 30, steps = null)
        launch()
        rule.clickWhenShown("Daily pair")
        rule.waitForNode(str(R.string.stats_counted_later_note))
        rule.onNodeWithText(Formatters.count(677, locale)).assertExists() // the plain number
        assertTrue(gone(str(R.string.history_steps_pending, Formatters.count(677, locale))))
        // Costs of a pound or more keep only pence: £129 / 677 steps per 1,000 = £190.55.
        rule.onNodeWithText(Formatters.costPerDistance(java.math.BigDecimal("190.55"), "GBP", locale)!!).assertExists()
    }

    @Test fun aRunningSessionComesFirstAndSaysNoStepsYet() {
        val id = addShoe("Trail pair")
        runBlocking { container.sessionManager.start(id) }
        launch()
        rule.clickWhenShown("Trail pair")
        rule.waitForNode(str(R.string.session_card_no_steps_note)) // instead of "About 0"
        rule.onAllNodesWithText(str(R.string.steps_none_yet)).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)

        val pause = rule.onNodeWithText(str(R.string.session_pause)).getUnclippedBoundsInRoot()
        val stats = rule.onNodeWithText(str(R.string.detail_stats_title)).getUnclippedBoundsInRoot()
        assertTrue("the running card should be above Stats", pause.top < stats.top)
        rule.onNodeWithText(str(R.string.title_shoe_detail)).assertExists() // "Pair"
    }
}
