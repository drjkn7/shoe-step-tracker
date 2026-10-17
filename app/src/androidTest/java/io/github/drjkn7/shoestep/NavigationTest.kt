package io.github.drjkn7.shoestep

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    @Test
    fun navigatesBetweenPlaceholderScreensAndBack() {
        rule.onNodeWithText(str(R.string.title_home)).assertIsDisplayed()

        rule.onNodeWithContentDescription(str(R.string.nav_settings)).performClick()
        rule.onNodeWithText(str(R.string.placeholder_settings)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()

        rule.onNodeWithContentDescription(str(R.string.nav_add_shoe)).performClick()
        rule.onNodeWithText(str(R.string.placeholder_add_edit)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()

        rule.onNodeWithText(str(R.string.placeholder_open_example)).performClick()
        rule.onNodeWithText(str(R.string.placeholder_detail)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.placeholder_edit)).performClick()
        rule.onNodeWithText(str(R.string.title_edit_shoe)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()

        rule.onNodeWithText(str(R.string.title_home)).assertIsDisplayed()
    }
}
