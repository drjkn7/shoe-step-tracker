package io.github.drjkn7.shoestep

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.ui.navigation.AppNavHost
import io.github.drjkn7.shoestep.ui.testContainer
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    @Test
    fun navigatesToSettingsAndAddShoeAndBack() {
        val container = testContainer()
        rule.setContent { ShoeStepTheme { AppNavHost(container) } }

        rule.onNodeWithText(str(R.string.title_home)).assertIsDisplayed()

        rule.onNodeWithContentDescription(str(R.string.nav_settings)).performClick()
        rule.onNodeWithText(str(R.string.placeholder_settings)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()

        rule.onNodeWithContentDescription(str(R.string.nav_add_shoe)).performClick()
        rule.onNodeWithText(str(R.string.title_add_shoe)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()

        rule.onNodeWithText(str(R.string.title_home)).assertIsDisplayed()
        container.database.close()
    }
}
