package io.github.drjkn7.shoestep.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.ui.FakeNotificationAccess
import io.github.drjkn7.shoestep.ui.InMemorySettings
import io.github.drjkn7.shoestep.ui.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real notification as Android holds it (T41, T43): posted, updated and removed as the session
 * changes. Uses an in-memory database; the notification belongs to the test app and is removed afterwards.
 */
@RunWith(AndroidJUnit4::class)
class SessionNotifierTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val system = context.getSystemService(NotificationManager::class.java)
    private val settings = InMemorySettings()
    private lateinit var container: AppContainer
    private val notifier get() = container.sessionNotifier
    private val sessions get() = container.sessionManager

    @Before fun setUp() {
        // Android 13+: the test app needs the permission itself, or Android drops what it posts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        container = testContainer(settings = settings, notificationAccess = FakeNotificationAccess(allowed = true))
    }

    @After fun tearDown() {
        system.cancel(SessionNotifier.NOTIFICATION_ID)
        container.database.close()
    }

    private fun addShoe(name: String): String = runBlocking {
        (container.shoeRepository.add(ShoeInput(name, "", "", ShoeType.WALKING, "", "EUR", null, 2)) as SaveResult.Saved).shoeId
    }

    private fun activeId(): String = runBlocking { sessions.activeSession.first()!!.session.id }

    /**
     * Android drops updates from an app that posts more than about 5 notifications a second, which these
     * tests would do back to back, so each refresh waits a little first. (People tap far slower.)
     */
    private fun refresh() {
        SystemClock.sleep(POST_SPACING_MILLIS)
        runBlocking { notifier.refresh() }
    }

    /** The posted notification once it matches [expected] (posting is asynchronous), or null if there is none. */
    private fun posted(expected: (Notification?) -> Boolean = { true }): Notification? {
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (true) {
            val current = system.activeNotifications.firstOrNull { it.id == SessionNotifier.NOTIFICATION_ID }?.notification
            if (expected(current) || SystemClock.uptimeMillis() > deadline) return current
            SystemClock.sleep(50)
        }
    }

    private fun title(n: Notification?) = n?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun titled(text: String) = posted { title(it) == text }

    private fun gone() = posted { it == null }

    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private companion object {
        const val POST_SPACING_MILLIS = 250L
    }

    @Test fun followsTheSessionFromStartToEnd() {
        val shoe = addShoe("Trail Runner 2")
        runBlocking { sessions.start(shoe) }
        refresh()
        val running = titled(str(R.string.notification_running_title, "Trail Runner 2"))
        assertNotNull(running)
        assertEquals(listOf(str(R.string.session_pause), str(R.string.session_end)), running!!.actions.map { it.title.toString() })
        assertTrue(running.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(running.flags and Notification.FLAG_ONGOING_EVENT != 0)

        val id = activeId()
        runBlocking { sessions.pause(id) }
        refresh()
        val paused = titled(str(R.string.notification_paused_title, "Trail Runner 2"))
        assertNotNull(paused)
        assertEquals(listOf(str(R.string.session_resume), str(R.string.session_end)), paused!!.actions.map { it.title.toString() })

        runBlocking { sessions.resume(id) }
        refresh()
        assertNotNull(titled(str(R.string.notification_running_title, "Trail Runner 2")))

        runBlocking { sessions.end(id) }
        refresh()
        assertNull(gone())
    }

    /** Lock screen: only "Session running", no pair name or times. */
    @Test fun privateOnTheLockScreen() {
        runBlocking { sessions.start(addShoe("Trail Runner 2")) }
        refresh()
        val running = titled(str(R.string.notification_running_title, "Trail Runner 2"))!!
        assertEquals(Notification.VISIBILITY_PRIVATE, running.visibility)
        assertEquals(str(R.string.notification_public), title(running.publicVersion))
        assertEquals(0, running.publicVersion.actions?.size ?: 0)
    }

    @Test fun switchShowsTheNewPair() {
        val first = addShoe("Road A")
        val second = addShoe("Road B")
        runBlocking { sessions.start(first) }
        refresh()
        runBlocking { sessions.switchTo(second) }
        refresh()
        assertNotNull(titled(str(R.string.notification_running_title, "Road B")))
    }

    @Test fun archivingOrDeletingThePairRemovesIt() {
        val shoe = addShoe("Road A")
        runBlocking { sessions.start(shoe) }
        refresh()
        assertNotNull(titled(str(R.string.notification_running_title, "Road A")))
        runBlocking { sessions.setArchived(shoe, true) }
        refresh()
        assertNull(gone())

        val other = addShoe("Road B")
        runBlocking { sessions.start(other) }
        refresh()
        assertNotNull(titled(str(R.string.notification_running_title, "Road B")))
        runBlocking { sessions.deleteShoe(other) }
        refresh()
        assertNull(gone())
    }

    /** T43: off in Settings means no notification; turning it back on shows it for the running session. */
    @Test fun settingOffRemovesItAndOnBringsItBack() {
        runBlocking { sessions.start(addShoe("Road A")) }
        refresh()
        assertNotNull(titled(str(R.string.notification_running_title, "Road A")))
        runBlocking { settings.setSessionNotification(false) }
        refresh()
        assertNull(gone())
        runBlocking { settings.setSessionNotification(true) }
        refresh()
        assertNotNull(titled(str(R.string.notification_running_title, "Road A")))
    }

    @Test fun nothingIsPostedWhenAndroidDoesNotAllowIt() {
        container = testContainer(settings = settings, notificationAccess = FakeNotificationAccess(allowed = false))
        runBlocking { sessions.start(addShoe("Road A")) }
        refresh()
        assertNull(gone())
    }
}
