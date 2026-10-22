package io.github.drjkn7.shoestep.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.settings.ThemeMode
import io.github.drjkn7.shoestep.ui.theme.GallerySection
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.ThemeGallery
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Theme setting applied live (T48), and the theme gallery rendered in light and night. */
@RunWith(AndroidJUnit4::class)
class ThemeScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun themeSettingAppliesWithoutRestart() {
        val settings = InMemorySettings()
        rule.setContent {
            ShoeStepTheme(settings) { Text(if (SilverTheme.colors.isNight) "night" else "light") }
        }
        val phoneIsDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        rule.onNodeWithText(if (phoneIsDark) "night" else "light").assertExists()

        runBlocking { settings.setThemeMode(ThemeMode.DARK) }
        rule.waitForIdle()
        rule.onNodeWithText("night").assertExists()

        runBlocking { settings.setThemeMode(ThemeMode.LIGHT) }
        rule.waitForIdle()
        rule.onNodeWithText("light").assertExists()

        runBlocking { settings.setThemeMode(ThemeMode.SYSTEM) }
        rule.waitForIdle()
        rule.onNodeWithText(if (phoneIsDark) "night" else "light").assertExists()
    }

    /**
     * Renders every gallery section in light and night at font scale 1.0 and 2.0. With the runner argument
     * `saveGallery=true` the pictures are saved under the app's external files dir (theme-gallery/) for
     * review; they show sample text only, never user data.
     */
    @Test fun galleryRendersInBothThemes() {
        val save = InstrumentationRegistry.getArguments().getString("saveGallery") == "true"
        val sections = GallerySection.entries
        var current by mutableStateOf(Triple(false, 1f, sections.first()))
        rule.setContent {
            val (night, fontScale, section) = current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ShoeStepTheme(darkTheme = night) {
                    Box(Modifier.testTag("gallery")) { ThemeGallery(section) }
                }
            }
        }
        for (night in listOf(false, true)) for (scale in listOf(1f, 2f)) for (section in sections) {
            current = Triple(night, scale, section)
            rule.waitForIdle()
            val image = rule.onNodeWithTag("gallery").captureToImage().asAndroidBitmap()
            if (save) {
                val dir = File(context.getExternalFilesDir(null), "theme-gallery").apply { mkdirs() }
                val name = "${section.name.lowercase()}-${if (night) "night" else "light"}-${(scale * 100).toInt()}.png"
                File(dir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
