package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.nfc.NfcState
import io.github.drjkn7.shoestep.nfc.TagContent
import io.github.drjkn7.shoestep.nfc.TagHandle
import io.github.drjkn7.shoestep.nfc.TappedTag
import io.github.drjkn7.shoestep.nfc.WriteResult
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tag taps on screen (T04–T07, T11, T12, T14, T29). Taps are simulated by handing the coordinator
 * the same TappedTag a real tap produces; writing uses a fake tag. A real tag is checked by hand.
 */
@RunWith(AndroidJUnit4::class)
class TagScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val clock = object : Clock {
        @Volatile var now = TestClock.nowUtcMillis()
        override fun nowUtcMillis() = now
        override fun zoneId(): ZoneId = TestClock.zoneId()
    }
    private var container: AppContainer = testContainer(clock = clock)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @After fun tearDown() = container.database.close()

    /** Call before adding pairs: it swaps in a fresh (empty) container. */
    private fun useNfcState(nfc: NfcState) {
        container = testContainer(clock = clock, nfcState = nfc)
    }

    private fun launch() {
        rule.setContent { ShoeStepTheme { AppNavHost(container) } }
    }

    private fun addShoe(name: String, tag: String? = null): String = runBlocking {
        val id = (container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, "", "EUR", null, 2)) as SaveResult.Saved).shoeId
        if (tag != null) container.database.shoeDao().setTagUuid(id, tag)
        id
    }

    private val written = mutableListOf<String>()
    private val okTag = TagHandle { uuid, _ -> written += uuid; WriteResult.OK }

    /** One tap, after the phone has been away from the tag long enough for the repeat guard. */
    private fun tap(content: TagContent, key: String = "hw-1") {
        clock.now += 3_000
        runBlocking { container.tagCoordinator.onTag(TappedTag(key, content, okTag)) }
        rule.waitForIdle()
    }

    private fun waitForText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun assertGone(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun dialogButton(label: String) = rule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

    /**
     * Reads the active session straight from the database. Never call SessionManager here: its lock
     * can be held by a pop-up action that only runs when this test thread lets the UI work, which
     * would make the two wait for each other forever (the hang seen on 2026-10-18).
     */
    private fun active() = runBlocking { container.database.activeSessionDao().observeActiveSessionWithShoe().first() }

    private val uuidA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val uuidB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    @Test fun tapOnOurTagOpensThePairWithASessionRunning() {
        val id = addShoe("Daily trainers", uuidA)
        launch()
        tap(TagContent.Ours(uuidA), key = uuidA)
        waitForText(str(R.string.detail_stats_title)) // the pair's own page is open
        waitForText(str(R.string.session_pause))
        assertEquals(id, active()?.session?.shoeId)
    }

    @Test fun secondTapAsksPauseEndOrCancel() {
        addShoe("Daily trainers", uuidA)
        launch()
        tap(TagContent.Ours(uuidA), key = uuidA)
        tap(TagContent.Ours(uuidA), key = uuidA)
        waitForText(str(R.string.tag_running_title, "Daily trainers"))

        dialogButton(str(R.string.action_cancel)).performClick()
        assertGone(str(R.string.tag_running_title, "Daily trainers"))
        assertEquals(SessionState.RUNNING, active()?.session?.state)

        tap(TagContent.Ours(uuidA), key = uuidA)
        dialogButton(str(R.string.session_pause)).performClick()
        waitForText(str(R.string.session_resume))
        assertEquals(SessionState.PAUSED, active()?.session?.state)
    }

    @Test fun tapOnAnotherPairAsksToSwitch() {
        addShoe("Daily trainers", uuidA)
        addShoe("Trail shoes", uuidB)
        launch()
        tap(TagContent.Ours(uuidA), key = uuidA)
        tap(TagContent.Ours(uuidB), key = uuidB)
        waitForText(str(R.string.session_switch_title))
        dialogButton(str(R.string.session_switch_confirm)).performClick()
        assertGone(str(R.string.session_switch_title))
        rule.waitUntil(5_000) { active()?.shoeName == "Trail shoes" }
        assertEquals(1, runBlocking { container.database.sessionDao().getUnendedSessions() }.size)
    }

    @Test fun newTagShowsBothOptionsAndBackCancels() {
        addShoe("Daily trainers")
        launch()
        tap(TagContent.Blank)
        waitForText(str(R.string.tag_new_title))
        dialogButton(str(R.string.tag_link_existing)).assertIsEnabled()
        dialogButton(str(R.string.tag_add_new_pair)).assertIsDisplayed()

        // Back and a tap outside both go to the dialog's dismiss request: nothing is linked.
        Espresso.pressBack()
        assertGone(str(R.string.tag_new_title))
        assertNull(container.tagCoordinator.linking.value)
        assertTrue(written.isEmpty())
    }

    @Test fun newTagLinkedToAnExistingPair() {
        val id = addShoe("Daily trainers")
        launch()
        tap(TagContent.Foreign)
        dialogButton(str(R.string.tag_link_existing)).performClick()
        waitForText(str(R.string.tag_pick_title))
        rule.onNode(hasText("Daily trainers") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        waitForText(str(R.string.tag_linking_title, "Daily trainers"))
        rule.onNodeWithText(str(R.string.tag_lock_warning)).assertIsDisplayed() // T05: warning first
        tap(TagContent.Foreign)

        waitForText(str(R.string.tag_linked_title))
        dialogButton(str(R.string.action_ok)).performClick()
        val saved = runBlocking { container.shoeRepository.findShoe(id) }?.tagUuid
        assertEquals(written.single(), saved)
        rule.onNodeWithText(str(R.string.tag_linked)).performScrollTo().assertIsDisplayed()
        assertNull("linking never starts a session", active())
    }

    @Test fun newTagForANewPairLinksAfterSaving() {
        launch()
        tap(TagContent.Blank)
        dialogButton(str(R.string.tag_add_new_pair)).performClick()
        rule.onNode(hasSetTextAction() and hasText(str(R.string.field_name), substring = true))
            .performTextReplacement("Gym shoes")
        rule.onNodeWithText(str(R.string.action_save)).performScrollTo().performClick()

        waitForText(str(R.string.tag_linking_title, "Gym shoes"))
        tap(TagContent.Blank)
        waitForText(str(R.string.tag_linked_title))
        val shoe = runBlocking { container.shoeRepository.observeShoes().first() }.single().shoe
        assertEquals("Gym shoes", shoe.name)
        assertEquals(written.single(), shoe.tagUuid)
    }

    @Test fun tagOfAnotherPairIsRefusedWhileLinking() {
        addShoe("Daily trainers", uuidA)
        val trail = addShoe("Trail shoes")
        launch()
        runBlocking { container.tagCoordinator.startLinking(trail) }
        tap(TagContent.Ours(uuidA), key = uuidA)
        waitForText(str(R.string.tag_problem_in_use, "Daily trainers"))
        assertNull(runBlocking { container.shoeRepository.findShoe(trail) }?.tagUuid)

        dialogButton(str(R.string.action_cancel)).performClick()
        assertGone(str(R.string.tag_linking_title, "Trail shoes"))
    }

    @Test fun linkAndUnlinkFromThePairsPage() {
        addShoe("Daily trainers")
        launch()
        rule.onNodeWithText("Daily trainers").performClick()
        rule.onNodeWithText(str(R.string.tag_link)).performScrollTo().performClick()
        waitForText(str(R.string.tag_linking_title, "Daily trainers"))
        tap(TagContent.Blank)
        dialogButton(str(R.string.action_ok)).performClick()

        rule.onNodeWithText(str(R.string.tag_unlink)).performScrollTo().performClick()
        dialogButton(str(R.string.tag_unlink)).performClick()
        rule.onNodeWithText(str(R.string.tag_not_linked)).performScrollTo().assertIsDisplayed()
    }

    @Test fun nfcOffExplainsAndOffersSettings() {
        useNfcState(NfcState.OFF)
        addShoe("Daily trainers")
        launch()
        rule.onNodeWithText("Daily trainers").performClick()
        rule.onNodeWithText(str(R.string.tag_link)).performScrollTo().performClick()
        waitForText(str(R.string.tag_nfc_off_body))
        dialogButton(str(R.string.tag_open_settings)).assertIsDisplayed()
        assertNull(container.tagCoordinator.linking.value)
    }

    @Test fun phoneWithoutNfcHidesTagButtons() {
        useNfcState(NfcState.UNSUPPORTED)
        addShoe("Daily trainers")
        launch()
        rule.onNodeWithText("Daily trainers").performClick()
        rule.onNodeWithText(str(R.string.tag_no_nfc)).performScrollTo().assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(str(R.string.tag_link)).fetchSemanticsNodes().isEmpty())
    }
}
