package io.github.drjkn7.shoestep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The session notification's pop-ups and its Settings switch (T40, T42, T43). */
@RunWith(AndroidJUnit4::class)
class NotificationScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val settings = InMemorySettings()
    private val access = FakeNotificationAccess()
    private val container = testContainer(settings = settings, notificationAccess = access)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @After fun tearDown() = container.database.close()

    private fun launch() = rule.setContent { ShoeStepTheme { AppNavHost(container) } }

    private fun startSession(name: String = "Trail Runner 2"): String = runBlocking {
        val id = (container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, "", "EUR", null, 2)) as SaveResult.Saved).shoeId
        container.sessionManager.start(id)
        container.sessionManager.activeSession.first()!!.session.id
    }

    private fun shown(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun dialogButton(label: Int) = rule.onNode(hasText(str(label)) and hasClickAction() and hasAnyAncestor(isDialog()))

    /** T40: asked in the app's words first; Not now turns the notification off and never asks again. */
    @Test fun askBeforeAndroidsPromptAndNotNowTurnsItOff() {
        access.needsPermission = true
        startSession()
        launch()
        rule.waitUntil(5_000) { shown(str(R.string.notify_ask_title)) }
        rule.onNodeWithText(str(R.string.notify_ask_body)).assertIsDisplayed()

        dialogButton(R.string.notify_ask_not_now).performClick()
        rule.waitUntil(5_000) { !shown(str(R.string.notify_ask_title)) }
        assertTrue(settings.asked.value)
        assertFalse(settings.notification.value)
    }

    /** T40: nothing to ask below Android 13, when already allowed, or when there is no session. */
    @Test fun noQuestionWithoutASessionOrWhenNothingIsNeeded() {
        access.needsPermission = true
        launch()
        rule.waitForIdle()
        assertFalse(shown(str(R.string.notify_ask_title)))

        access.needsPermission = false
        startSession()
        rule.waitUntil(5_000) { shown(str(R.string.session_card_title)) }
        assertFalse(shown(str(R.string.notify_ask_title)))
    }

    /** T42: End session in the notification asks first, then ends that session. */
    @Test fun endFromTheNotificationAsksFirst() {
        val id = startSession("Trail Runner 2")
        launch()
        container.endRequests.value = id
        rule.waitUntil(5_000) { shown(str(R.string.session_end_title)) }
        rule.onNodeWithText(str(R.string.session_end_body, "Trail Runner 2")).assertIsDisplayed()

        dialogButton(R.string.session_end).performClick()
        // Wait on the screen, not the database: the end runs on the test's own UI dispatcher, which a
        // blocking read here would stall. Home redraws without the session card once it has ended.
        rule.waitUntil(5_000) { !shown(str(R.string.session_card_title)) }
        rule.waitForIdle()
        assertNull(runBlocking { container.sessionManager.activeSession.first() })
        assertNull(container.endRequests.value)
    }

    @Test fun cancellingTheEndQuestionKeepsTheSession() {
        val id = startSession()
        launch()
        container.endRequests.value = id
        rule.waitUntil(5_000) { shown(str(R.string.session_end_title)) }
        dialogButton(R.string.action_cancel).performClick()
        rule.waitUntil(5_000) { !shown(str(R.string.session_end_title)) }
        assertEquals(id, runBlocking { container.sessionManager.activeSession.first() }?.session?.id)
    }

    /** T42: an End session button from an old notification (that session is over) does nothing. */
    @Test fun staleEndRequestIsIgnored() {
        startSession()
        launch()
        container.endRequests.value = "an-old-session"
        rule.waitUntil(5_000) { container.endRequests.value == null }
        assertFalse(shown(str(R.string.session_end_title)))
        assertTrue(runBlocking { container.sessionManager.activeSession.first() } != null)
    }

    private fun openSettings() {
        launch()
        rule.onNodeWithContentDescription(str(R.string.nav_settings)).performClick()
        rule.waitUntil(5_000) { shown(str(R.string.settings_session_notification)) }
    }

    /** T43: the Settings switch turns the notification off and on. */
    @Test fun settingsSwitchTurnsTheNotificationOffAndOn() {
        access.allowed = true
        openSettings()
        val switch = rule.onNode(isToggleable() and hasText(str(R.string.settings_session_notification), substring = true))
        switch.performScrollTo().performClick()
        rule.waitUntil(5_000) { !settings.notification.value }
        switch.performClick()
        rule.waitUntil(5_000) { settings.notification.value }
        // Turning it on in Settings answers the question, so the pop-up never appears afterwards.
        assertTrue(settings.asked.value)
        assertFalse(shown(str(R.string.settings_notifications_blocked)))
    }

    @Test fun settingsSaysWhenAndroidBlocksNotifications() {
        access.allowed = false
        openSettings()
        rule.waitUntil(5_000) { shown(str(R.string.settings_notifications_blocked)) }
        rule.onNodeWithText(str(R.string.settings_open_android_settings)).performScrollTo().assertIsDisplayed()
    }
}
