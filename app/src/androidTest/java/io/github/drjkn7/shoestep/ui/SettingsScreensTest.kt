package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings: default currency, step status, export buttons, privacy and licences pages. */
@RunWith(AndroidJUnit4::class)
class SettingsScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val settings = InMemorySettings()
    private val source = FakeStepsSource()
    private val container = testContainer(stepsSource = source, settings = settings)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @After fun tearDown() = container.database.close()

    private fun openSettings() {
        rule.setContent { ShoeStepTheme { AppNavHost(container) } }
        rule.onNodeWithContentDescription(str(R.string.nav_settings)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.settings_new_pairs)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun chosenDefaultCurrencyIsUsedForNewPairs() {
        openSettings()
        rule.onNode(hasText(" (", substring = true) and hasClickAction())
            .performClick()
        rule.onNode(hasSetTextAction()).performTextInput("JPY")
        rule.onNode(hasText("JPY", substring = true) and hasClickAction() and !hasSetTextAction()).performClick()
        rule.waitUntil(5_000) { runBlocking { settings.defaultCurrency.first() } == "JPY" }
        waitForText(str(R.string.settings_currency_value, "JPY", ""))

        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
        rule.onNodeWithText(str(R.string.nav_add_shoe)).performClick()
        waitForText("JPY")
    }

    @Test fun stepCountingShowsItsState() {
        openSettings()
        waitForText(str(R.string.settings_manage_health_connect))
    }

    @Test fun stepCountingOffersAllowWhenNotPermitted() {
        source.access = StepsAccess.NEEDS_PERMISSION
        openSettings()
        waitForText(str(R.string.steps_access_allow))
    }

    @Test fun exportButtonsAreThere() {
        openSettings()
        rule.onNodeWithText(str(R.string.settings_export_pairs)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_export_sessions)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_version, "0.1.0")).performScrollTo().assertIsDisplayed()
    }

    @Test fun privacyAndLicencesPagesOpen() {
        openSettings()
        rule.onNodeWithText(str(R.string.settings_privacy)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.privacy_short)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()

        rule.onNodeWithText(str(R.string.settings_licences)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.licences_apache_title)).assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.licences_show_text))[0].performClick()
        waitForText("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION")
        rule.onAllNodesWithText(str(R.string.licences_show_text))[0].performScrollTo().performClick()
        waitForText("Copyright 2004-present by the Checker Framework developers")
        assertEquals(2, rule.onAllNodesWithText(str(R.string.licences_hide_text)).fetchSemanticsNodes().size)
    }
}
