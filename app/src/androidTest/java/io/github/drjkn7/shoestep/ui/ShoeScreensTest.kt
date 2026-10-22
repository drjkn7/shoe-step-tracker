package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
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

@RunWith(AndroidJUnit4::class)
class ShoeScreensTest {

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

    private fun addShoeDirectly(name: String): String = runBlocking {
        val result = container.shoeRepository.add(ShoeInput(name, "Brand", "Model", ShoeType.WALKING, "100", "EUR", null, 1))
        (result as SaveResult.Saved).shoeId
    }

    private fun field(label: Int): SemanticsNodeInteraction =
        rule.onNode(hasSetTextAction() and hasText(str(label), substring = true))

    private fun clickSave() = rule.onNodeWithText(str(R.string.action_save)).performScrollTo().performClick()

    // --- Home -------------------------------------------------------------------------------

    @Test fun emptyStateExplainsHowToStart() {
        launch()
        rule.waitForNode(str(R.string.home_empty_title))
        rule.onNodeWithText(str(R.string.home_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_empty_body)).assertIsDisplayed()
    }

    // --- Add + validation (T03) -------------------------------------------------------------

    @Test fun addShoeShowsValidationErrorsThenSaves() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.nav_add_shoe)).performClick()

        // Empty name: tapping Save shows a popup describing the problem, visible wherever the form
        // is scrolled (product owner found the inline error was off-screen on a Pixel 9 Pro).
        clickSave()
        rule.onNodeWithText(str(R.string.save_problems_title)).assertIsDisplayed()
        rule.onNodeWithText(
            str(R.string.save_problems_item, str(R.string.field_label_name), str(R.string.error_name_required)),
        ).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.action_ok)).performClick()
        assertTrue(rule.onAllNodesWithText(str(R.string.save_problems_title)).fetchSemanticsNodes().isEmpty())
        // The field itself stays marked so the user can find it after closing the popup.
        rule.onNodeWithText(str(R.string.error_name_required)).assertExists()

        // 200 characters
        field(R.string.field_name).performTextReplacement("a".repeat(200))
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.error_too_long, 60, 60)).assertExists()

        // Bad prices
        field(R.string.field_name).performTextReplacement("Trail pair")
        field(R.string.field_price).performTextReplacement("-5")
        rule.onNodeWithText(str(R.string.error_price_negative)).assertExists()
        field(R.string.field_price).performTextReplacement("abc")
        rule.onNodeWithText(str(R.string.error_price_invalid)).assertExists()
        field(R.string.field_price).performTextReplacement("1.999")
        rule.onNodeWithText(str(R.string.error_price_decimals, currencyShown())).assertExists()

        // Valid
        field(R.string.field_price).performTextReplacement("129.99")
        clickSave()

        // Lands on the new shoe (product decision), and Back goes to the list.
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.detail_stats_title)).fetchSemanticsNodes().isNotEmpty() }
        // The name appears in both the title bar and the card.
        rule.onAllNodesWithText("Trail pair").onFirst().assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
        rule.onNodeWithText(str(R.string.title_home)).assertIsDisplayed()
        rule.onNodeWithText("Trail pair").assertIsDisplayed()

        val saved = runBlocking { container.shoeRepository.observeShoes().first().single().shoe }
        assertEquals(12_999L, saved.priceMinor)
    }

    /** The currency button's visible text is the currency code chosen from the device locale. */
    private fun currencyShown(): String =
        io.github.drjkn7.shoestep.core.Money.defaultCurrencyCode(context.resources.configuration.locales[0])

    // --- Edit -------------------------------------------------------------------------------

    @Test fun editingNameUpdatesDetailAndList() {
        addShoeDirectly("Old name")
        launch()
        rule.clickWhenShown("Old name")
        rule.onNodeWithContentDescription(str(R.string.action_edit)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Old name").fetchSemanticsNodes().isNotEmpty() }

        field(R.string.field_name).performTextReplacement("New name")
        clickSave()

        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.detail_stats_title)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("New name").onFirst().assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
        rule.onNodeWithText("New name").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Old name").fetchSemanticsNodes().isEmpty())
    }

    // --- Archive / unarchive (T28) ----------------------------------------------------------

    @Test fun archiveHidesShoeAndUnarchiveBringsItBack() {
        addShoeDirectly("Winter boots")
        launch()
        rule.clickWhenShown("Winter boots")
        rule.onNodeWithContentDescription(str(R.string.action_more)).performClick()
        rule.onNodeWithText(str(R.string.action_archive)).performClick()
        rule.onNodeWithText(str(R.string.detail_archived_note)).assertIsDisplayed()

        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
        rule.onNodeWithText(str(R.string.home_empty_title)).assertIsDisplayed()

        rule.onNodeWithText(str(R.string.home_show_archived)).performClick()
        rule.onNodeWithText("Winter boots").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_archived_badge)).assertIsDisplayed()

        rule.onNodeWithText("Winter boots").performClick()
        rule.onNodeWithContentDescription(str(R.string.action_more)).performClick()
        rule.onNodeWithText(str(R.string.action_unarchive)).performClick()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
        rule.onNodeWithText(str(R.string.home_show_archived)).performClick()
        rule.onNodeWithText("Winter boots").assertIsDisplayed()
    }

    // --- Delete (T28, SEC-11) -----------------------------------------------------------------

    @Test fun deleteNeedsConfirmationAndCancelKeepsShoe() {
        val id = addShoeDirectly("Old runners")
        launch()
        rule.clickWhenShown("Old runners")

        rule.onNodeWithContentDescription(str(R.string.action_more)).performClick()
        rule.onNodeWithText(str(R.string.action_delete)).performClick()
        rule.onNodeWithText(str(R.string.delete_title, "Old runners")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.action_cancel)).performClick()
        assertTrue(runBlocking { container.shoeRepository.findShoe(id) } != null)

        rule.onNodeWithContentDescription(str(R.string.action_more)).performClick()
        rule.onNodeWithText(str(R.string.action_delete)).performClick()
        rule.onNodeWithText(str(R.string.action_delete)).performClick() // the dialog's confirm button

        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.home_empty_title)).fetchSemanticsNodes().isNotEmpty() }
        assertNull(runBlocking { container.shoeRepository.findShoe(id) })
    }

    // --- Accessibility (part of T25) -----------------------------------------------------------

    @Test fun iconButtonsAreLabelledAndAtLeast48dp() {
        addShoeDirectly("Labelled")
        launch()
        assertIconButtons()
        rule.clickWhenShown("Labelled")
        assertIconButtons()
        rule.onNodeWithContentDescription(str(R.string.action_edit)).performClick()
        assertIconButtons()
    }

    private fun assertIconButtons() {
        rule.waitForIdle()
        val iconButtons = rule.onAllNodes(hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        val count = iconButtons.fetchSemanticsNodes().size
        assertTrue("Expected icon buttons on screen", count > 0)
        // Material draws icon buttons 40dp wide but extends the touch area to 48dp; measure the touch area.
        val minPx = with(rule.density) { 48.dp.toPx() } - 1f
        for (i in 0 until count) {
            // Off-screen elements (small emulator screens) measure 0x0 until scrolled into view.
            runCatching { iconButtons[i].performScrollTo() }
            val node = iconButtons[i].fetchSemanticsNode()
            val touch = node.touchBoundsInRoot
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)
            assertTrue("Touch target too small for $label: ${touch.width} x ${touch.height}px", touch.width >= minPx && touch.height >= minPx)
        }
        // Every clickable element exposes some label (text or content description) to TalkBack.
        val unlabelled = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            val hasDescription = config.contains(SemanticsProperties.ContentDescription)
            val hasText = config.contains(SemanticsProperties.Text) ||
                config.contains(SemanticsProperties.EditableText)
            !hasDescription && !hasText && node.children.none { child ->
                child.config.contains(SemanticsProperties.Text)
            }
        }
        assertTrue("Unlabelled clickable elements: ${unlabelled.size}", unlabelled.isEmpty())
    }

    @Test fun addFormIsUsableAt200PercentFont() {
        launch(fontScale = 2f)
        rule.onNodeWithContentDescription(str(R.string.nav_add_shoe)).performClick()
        field(R.string.field_name).performTextReplacement("Big text")
        clickSave()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.detail_stats_title)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Big text").onFirst().assertExists()
    }
}

