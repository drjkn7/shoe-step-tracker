package io.github.drjkn7.shoestep.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drjkn7.shoestep.ui.common.ConfirmDialog
import io.github.drjkn7.shoestep.ui.gallery.ComponentGallery
import io.github.drjkn7.shoestep.ui.gallery.ComponentSection
import io.github.drjkn7.shoestep.ui.theme.ShoeStepTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every Silver Matte component in light and night at font scale 1.0 and 2.0 (M2 Phase 2):
 * each tappable element is at least 48 dp in both directions and has something for TalkBack to read.
 * With the runner argument `saveGallery=true` pictures are saved under the app's external files dir
 * (component-gallery/); they show sample text only, never user data.
 */
@RunWith(AndroidJUnit4::class)
class ComponentGalleryTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val clickable = SemanticsMatcher("is tappable") {
        SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Disabled) == null
    }

    @Test fun realPopUpShowsItsTextAndButtonsInBothThemes() {
        val save = InstrumentationRegistry.getArguments().getString("saveGallery") == "true"
        var night by mutableStateOf(false)
        val body = "The session for Trail Runner 2 stops now. You can start a new one any time."
        rule.setContent {
            ShoeStepTheme(darkTheme = night) {
                ConfirmDialog("End this session?", body, "End session", onConfirm = {}, onDismiss = {}, destructive = false)
            }
        }
        for (isNight in listOf(false, true)) {
            night = isNight
            rule.waitForIdle()
            rule.onNodeWithText(body).assertIsDisplayed()
            rule.onNodeWithText("End session").assertIsDisplayed()
            if (save) {
                val image = rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
                val dir = File(context.getExternalFilesDir(null), "component-gallery").apply { mkdirs() }
                File(dir, "real-dialog-${if (isNight) "night" else "light"}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    @Test fun componentsAreLabelledAndLargeEnoughInBothThemes() {
        val save = InstrumentationRegistry.getArguments().getString("saveGallery") == "true"
        var current by mutableStateOf(Triple(false, 1f, ComponentSection.entries.first()))
        rule.setContent {
            val (night, fontScale, section) = current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ShoeStepTheme(darkTheme = night) {
                    // Lay the section out at its full height even when taller than the screen.
                    Box(Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true).testTag("gallery")) {
                        ComponentGallery(section)
                    }
                }
            }
        }
        val minPx = with(rule.density) { 48f * density - 1f } // 48 dp, allowing for rounding
        for (night in listOf(false, true)) for (scale in listOf(1f, 2f)) for (section in ComponentSection.entries) {
            current = Triple(night, scale, section)
            rule.waitForIdle()
            val where = "${section.name} ${if (night) "night" else "light"} ${scale}x"
            rule.onAllNodes(clickable, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val touch = node.touchBoundsInRoot
                assertTrue("$where: tappable element ${touch.width} x ${touch.height}px is under 48 dp", touch.width >= minPx && touch.height >= minPx)
                val merged = node.config
                val readable = merged.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true ||
                    merged.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true ||
                    node.children.any { child ->
                        child.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true ||
                            child.config.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true
                    }
                assertTrue("$where: a tappable element has nothing for TalkBack to read", readable)
            }
            if (save) {
                val image = rule.onNodeWithTag("gallery").captureToImage().asAndroidBitmap()
                val dir = File(context.getExternalFilesDir(null), "component-gallery").apply { mkdirs() }
                val name = "${section.name.lowercase()}-${if (night) "night" else "light"}-${(scale * 100).toInt()}.png"
                File(dir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
