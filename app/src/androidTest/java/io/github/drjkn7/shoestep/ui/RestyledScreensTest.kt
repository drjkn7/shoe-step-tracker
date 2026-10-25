package io.github.drjkn7.shoestep.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.settings.SystemNightMode
import io.github.drjkn7.shoestep.settings.ThemeMode
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
 * M2 Phase 3: "Discard changes?" (T45), the Appearance card (T48), and every restyled screen in light
 * and night at normal and 200% text (T46, T49; Add past session since Phase 6): each tappable element
 * is at least 48 dp and has something for TalkBack to read.
 */
@RunWith(AndroidJUnit4::class)
class RestyledScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private val settings = InMemorySettings()
    private val toldAndroid = mutableListOf<ThemeMode>()
    private val container: AppContainer = testContainer(settings = settings, systemNightMode = SystemNightMode { toldAndroid += it })

    @After fun tearDown() = container.database.close()

    private fun launch() = rule.setContent { ShoeStepTheme { AppNavHost(container) } }

    private fun addPair(name: String): String = runBlocking {
        val result = container.shoeRepository.add(ShoeInput(name, "Saucony", "Peregrine 15", ShoeType.TRAIL_RUNNING, "129", "GBP", null, 4))
        (result as SaveResult.Saved).shoeId
    }

    private fun field(label: Int): SemanticsNodeInteraction = rule.onNode(hasSetTextAction() and hasText(str(label), substring = true))

    private fun openEdit() {
        rule.onNodeWithContentDescription(str(R.string.action_more)).performClick()
        rule.onNodeWithText(str(R.string.action_edit)).performClick()
    }

    private fun waitForText(text: String) = rule.waitForNode(text)

    private fun assertGone(text: String) =
        assertTrue("'$text' should not be shown", rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())

    // --- T45 Discard changes ------------------------------------------------------------------

    @Test fun editFormAsksOnlyAfterAChange() {
        addPair("Trail Runner 2")
        launch()
        rule.clickWhenShown("Trail Runner 2")
        openEdit()
        waitForText(str(R.string.title_edit_shoe))
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction() and hasText("Trail Runner 2")).fetchSemanticsNodes().isNotEmpty() }

        // Nothing changed: Close just closes.
        rule.onNodeWithContentDescription(str(R.string.nav_close)).performClick()
        waitForText(str(R.string.detail_stats_title))
        assertGone(str(R.string.discard_title))

        openEdit()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction() and hasText("Trail Runner 2")).fetchSemanticsNodes().isNotEmpty() }
        field(R.string.field_name).performTextReplacement("Trail Runner 3")

        // Close asks, naming the pair as saved; Keep editing keeps what was typed.
        rule.onNodeWithContentDescription(str(R.string.nav_close)).performClick()
        rule.onNodeWithText(str(R.string.discard_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.discard_body_edit, "Trail Runner 2")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.discard_keep)).performClick()
        assertGone(str(R.string.discard_title))
        field(R.string.field_name).assertExists()
        rule.onNode(hasSetTextAction() and hasText("Trail Runner 3")).assertExists()

        // The phone's Back asks too (the first Back only closes the keyboard); Discard leaves without saving.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        rule.onNodeWithText(str(R.string.discard_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.discard_confirm)).performClick()
        waitForText(str(R.string.detail_stats_title))
        val names = runBlocking { container.shoeRepository.observeShoes().first().map { it.shoe.name } }
        assertEquals(listOf("Trail Runner 2"), names)
    }

    @Test fun newPairAsksOnlyAfterTyping() {
        launch()
        rule.onNodeWithText(str(R.string.nav_add_shoe)).performClick()
        waitForText(str(R.string.title_add_shoe))

        rule.onNodeWithContentDescription(str(R.string.nav_close)).performClick()
        waitForText(str(R.string.home_empty_title))
        assertGone(str(R.string.discard_title))

        rule.onNodeWithText(str(R.string.nav_add_shoe)).performClick()
        waitForText(str(R.string.title_add_shoe))
        rule.onNodeWithText(str(R.string.type_hiking)).performClick()
        Espresso.pressBack()
        rule.onNodeWithText(str(R.string.discard_body_add)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.discard_confirm)).performClick()
        waitForText(str(R.string.home_empty_title))
        assertTrue(runBlocking { container.shoeRepository.observeShoes().first().isEmpty() })
    }

    // --- T48 Appearance card ------------------------------------------------------------------

    @Test fun themeControlSavesTheChoiceAndTellsAndroid() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.nav_settings)).performClick()
        waitForText(str(R.string.settings_appearance))

        rule.onNodeWithText(str(R.string.theme_dark)).performClick()
        rule.waitUntil(5_000) { runBlocking { settings.themeMode.first() } == ThemeMode.DARK }
        rule.waitUntil(5_000) { toldAndroid.lastOrNull() == ThemeMode.DARK }

        rule.onNodeWithText(str(R.string.theme_system)).performClick()
        rule.waitUntil(5_000) { runBlocking { settings.themeMode.first() } == ThemeMode.SYSTEM }
        rule.waitUntil(5_000) { toldAndroid.lastOrNull() == ThemeMode.SYSTEM }

        // Choosing the current option again changes nothing.
        val calls = toldAndroid.size
        rule.onNodeWithText(str(R.string.theme_system)).performClick()
        rule.waitForIdle()
        assertEquals(calls, toldAndroid.size)
    }

    // --- T46 / T49 Every screen, light and night, 100% and 200% text ----------------------------

    @Test fun everyScreenIsLabelledAndLargeEnoughInBothThemes() {
        val id = addPair("Trail Runner 2")
        addPair("A very long pair name to check how the screens wrap it")
        runBlocking { container.sessionManager.start(id) }

        var config by mutableStateOf(false to 1f)
        rule.setContent {
            val (night, scale) = config
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                ShoeStepTheme(darkTheme = night) { AppNavHost(container) }
            }
        }
        for (night in listOf(false, true)) for (scale in listOf(1f, 2f)) {
            config = night to scale
            val where = "${if (night) "night" else "light"} ${scale}x"
            // Home keeps last round's scroll position, which can leave "My shoes" above the screen (and unbuilt).
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription(str(R.string.nav_settings)).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasScrollToKeyAction()).performScrollToIndex(0)
            waitForText(str(R.string.title_home))
            assertTappablesOk("$where Home")

            // Home builds only the rows on screen; on a short screen (CI's Pixel 2) this one starts below it.
            rule.onNode(hasScrollToKeyAction()).performScrollToKey(id)
            // Tap the left side: at 200% text the middle of the row can be under the floating "Add pair".
            rule.onAllNodesWithText("Trail Runner 2").onFirst().performTouchInput { click(Offset(width * 0.2f, height / 2f)) }
            waitForText(str(R.string.detail_stats_title))
            assertTappablesOk("$where pair page")

            openEdit()
            rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction() and hasText("Trail Runner 2")).fetchSemanticsNodes().isNotEmpty() }
            assertTappablesOk("$where edit form")
            rule.onNodeWithContentDescription(str(R.string.nav_close)).performClick()
            waitForText(str(R.string.detail_stats_title))

            rule.onNodeWithText(str(R.string.past_add)).performScrollTo().performClick()
            waitForText(str(R.string.title_add_past_session))
            assertTappablesOk("$where add past session")
            rule.onNodeWithContentDescription(str(R.string.nav_close)).performClick()
            waitForText(str(R.string.detail_stats_title))
            rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()

            rule.onNodeWithContentDescription(str(R.string.nav_settings)).performClick()
            waitForText(str(R.string.settings_appearance))
            assertTappablesOk("$where Settings")

            rule.onNodeWithText(str(R.string.settings_privacy)).performScrollTo().performClick()
            waitForText(str(R.string.privacy_short))
            assertTappablesOk("$where privacy")
            rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()

            rule.onNodeWithText(str(R.string.settings_licences)).performScrollTo().performClick()
            waitForText(str(R.string.licences_intro))
            assertTappablesOk("$where licences")
            rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
            rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
        }
    }

    private val tappable = SemanticsMatcher("is tappable") {
        SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Disabled) == null
    }

    /** Every tappable element (on or off screen) is at least 48 dp each way and has words for TalkBack. */
    private fun assertTappablesOk(where: String) {
        rule.waitForIdle()
        val minPx = with(rule.density) { 48f * density - 1f } // 48 dp, allowing for rounding
        rule.onAllNodes(tappable, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            // Layout size, not on-screen bounds, so elements scrolled out of view are checked too.
            val label = readable(node)
            assertTrue("$where: '$label' is ${node.size.width} x ${node.size.height}px, under 48 dp", node.size.width >= minPx && node.size.height >= minPx)
            assertTrue("$where: a tappable element has nothing for TalkBack to read", label.isNotEmpty())
        }
    }

    private fun readable(node: SemanticsNode): String {
        fun own(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")
        return listOf(own(node)).plus(node.children.map(::own)).filter { it.isNotBlank() }.joinToString(" ")
    }
}
