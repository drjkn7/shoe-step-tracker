package io.github.drjkn7.shoestep.settings

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * T48: the theme choice is still there after the app restarts. Uses its own settings file, never the
 * app's, and deletes it afterwards.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileName = "settings-test-${UUID.randomUUID()}"

    @After fun deleteFile() {
        context.preferencesDataStoreFile(fileName).delete()
    }

    @Test fun themeChoiceSurvivesARestart() = runBlocking {
        val firstRun = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val before = DataStoreSettings(context, fileName, firstRun)
        assertEquals(ThemeMode.SYSTEM, before.themeMode.first())
        before.setThemeMode(ThemeMode.DARK)
        firstRun.cancel() // the app stops: the file is closed

        val secondRun = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val after = DataStoreSettings(context, fileName, secondRun)
        assertEquals(ThemeMode.DARK, after.themeMode.first())
        secondRun.cancel()
    }

    @Test fun distanceUnitAndCardChoiceSurviveARestart() = runBlocking {
        val firstRun = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val before = DataStoreSettings(context, fileName, firstRun)
        assertNull("not chosen: the region decides", before.distanceUnit.first())
        assertFalse(before.distanceCardDismissed.first())
        before.setDistanceUnit(DistanceUnit.MILES)
        before.dismissDistanceCard()
        firstRun.cancel()

        val secondRun = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val after = DataStoreSettings(context, fileName, secondRun)
        assertEquals(DistanceUnit.MILES, after.distanceUnit.first())
        assertTrue(after.distanceCardDismissed.first())
        secondRun.cancel()
    }
}
