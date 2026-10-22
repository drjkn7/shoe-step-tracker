package io.github.drjkn7.shoestep.ui

import android.os.SystemClock
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.LayoutDirection
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.room3.withWriteTransaction
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
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 8: lots of data (T26) and right-to-left layout (T25). */
@RunWith(AndroidJUnit4::class)
class HardeningScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val container = testContainer()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun older(n: Int) = context.resources.getQuantityString(R.plurals.history_show_older, n, n)

    @After fun tearDown() = container.database.close()

    private fun addShoe(name: String): String = runBlocking {
        (container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, "", "EUR", null, 2)) as SaveResult.Saved).shoeId
    }

    /** [count] ended sessions of 10 minutes each with final step counts, written in one transaction. */
    private fun addSessions(shoeId: String, count: Int) = runBlocking {
        val db = container.database
        db.withWriteTransaction {
            val dao = db.sessionDao()
            repeat(count) { i ->
                val start = TestClock.nowUtcMillis() - (i + 1) * 3_600_000L
                val end = start + 600_000L
                dao.insertSession(SessionEntity("s$i", shoeId, start, end, "UTC", SessionState.ENDED, EndReason.USER))
                dao.insertSegment(SegmentEntity("g$i", "s$i", start, end, 1_000L + i, StepsStatus.FINAL, end))
            }
        }
    }

    @Test fun fortyPairsAndTwoThousandSessionsStayResponsive() {
        val names = (1..40).map { "Pair %02d".format(it) }
        val ids = names.map(::addShoe)
        addSessions(ids.first(), 2_000)

        rule.setContent { ShoeStepTheme { AppNavHost(container) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Pair 01").fetchSemanticsNodes().isNotEmpty() }

        // Home: the last pair can be reached by scrolling the list.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Pair 40"))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Pair 01"))

        // The busy pair opens quickly and builds only its newest 50 sessions.
        val started = SystemClock.elapsedRealtime()
        rule.onNodeWithText("Pair 01").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(older(50)).fetchSemanticsNodes().isNotEmpty() }
        val openMillis = SystemClock.elapsedRealtime() - started
        assertTrue("pair page with 2,000 sessions took $openMillis ms", openMillis < 3_000)

        rule.onNodeWithText(older(50)).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(older(50)).fetchSemanticsNodes().isNotEmpty() } // 1,900 left
    }

    @Test fun shortHistoryHasNoShowOlderButton() {
        val id = addShoe("Daily trainers")
        addSessions(id, 50)
        rule.setContent { ShoeStepTheme { AppNavHost(container) } }
        rule.clickWhenShown("Daily trainers")
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.history_title)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("older session", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun rightToLeftMirrorsTheLayout() {
        addShoe("Daily trainers")
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.LayoutDirection(LayoutDirection.Rtl)) {
                ShoeStepTheme { AppNavHost(container) }
            }
        }
        rule.clickWhenShown("Daily trainers")
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.detail_stats_title)).fetchSemanticsNodes().isNotEmpty() }
        val back = rule.onNodeWithContentDescription(str(R.string.nav_back)).fetchSemanticsNode().boundsInRoot
        val width = rule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot.right
        assertTrue("back button is on the right in RTL (left=${back.left}, width=$width)", back.left > width / 2)
    }
}
