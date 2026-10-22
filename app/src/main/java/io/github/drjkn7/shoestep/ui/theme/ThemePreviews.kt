package io.github.drjkn7.shoestep.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/*
 * Previews of the theme pieces, in light and night (Android Studio's Preview pane). The same gallery is
 * rendered on a device by ThemeGalleryTest, which saves pictures for review without Android Studio.
 * Labels here are developer-facing sample text, not app strings.
 */

@Preview(name = "Light", showBackground = true, widthDp = 390)
@Preview(name = "Night", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class LightNightPreviews

/** Every theme piece on one page; [section] limits it to one part (null = all). */
@Composable
internal fun ThemeGallery(section: GallerySection? = null) {
    val c = SilverTheme.colors
    Column(
        Modifier.backdrop(c).padding(SilverSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(SilverSpacing.l),
    ) {
        if (section == null || section == GallerySection.COLOURS) ColourSwatches()
        if (section == null || section == GallerySection.TYPE) TypeScale()
        if (section == null || section == GallerySection.DEPTH) DepthSamples()
        if (section == null || section == GallerySection.SHOE_COLOURS) ShoeColourRow()
    }
}

internal enum class GallerySection { COLOURS, TYPE, DEPTH, SHOE_COLOURS }

@Composable
private fun ColourSwatches() {
    val c = SilverTheme.colors
    val swatches: List<Pair<String, Brush>> = listOf(
        "card" to SolidColor(c.card),
        "card-register" to SolidColor(c.cardRegister),
        "sheen" to c.sheenBrush(),
        "inset" to SolidColor(c.inset),
        "dialog" to c.dialogBrush(),
        "accent" to c.accentBrush(),
        "text-primary" to SolidColor(c.textPrimary),
        "text-muted" to SolidColor(c.textMuted),
        "accent-ink" to SolidColor(c.accentInk),
        "danger" to SolidColor(c.danger),
        "danger-fill" to SolidColor(c.dangerFill),
        "divider" to SolidColor(c.divider),
    )
    Panel("Colours") {
        swatches.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s)) {
                row.forEach { (name, brush) ->
                    Column(Modifier.width(100.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(36.dp)
                                .background(brush, SilverShapes.input)
                                .border(1.dp, c.divider, SilverShapes.input),
                        )
                        Text(name, style = SilverTheme.type.note, color = c.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeScale() {
    val t = SilverTheme.type
    val c = SilverTheme.colors
    Panel("Type – Chakra Petch") {
        Text("My shoes", style = t.display, color = c.textPrimary)
        Text("Trail Runner 2", style = t.section, color = c.textPrimary)
        Text("612,940 steps", style = t.button, color = c.accentInk)
        Text("Trail running · Saucony Peregrine 15", style = t.body, color = c.textMuted)
        Text("CURRENT SESSION", style = t.label, color = c.textMuted)
        Text("Steps are counted when you open the app.", style = t.note, color = c.textMuted)
        Text("Delete", style = t.button, color = c.danger)
    }
}

@Composable
private fun DepthSamples() {
    val c = SilverTheme.colors
    Panel("Depth and shapes") {
        Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
            DepthTile("Raised", Modifier.raised(c, SilverShapes.panel))
            DepthTile("Floating", Modifier.floating(c, SilverShapes.dialog))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
            Box(
                Modifier.weight(1f).heightIn(min = SilverSpacing.touch).raised(c, SilverShapes.pill, c.sheenBrush()),
                contentAlignment = Alignment.Center,
            ) { Text("Pause", style = SilverTheme.type.button, color = c.textPrimary) }
            Box(
                Modifier.weight(1f).heightIn(min = SilverSpacing.touch).accentGlow(c, SilverShapes.pill),
                contentAlignment = Alignment.Center,
            ) { Text("End session", style = SilverTheme.type.button, color = c.textOnAccent) }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = SilverSpacing.touch).pressedIn(c, SilverShapes.input)
                .padding(horizontal = SilverSpacing.m),
            contentAlignment = Alignment.CenterStart,
        ) { Text("Pressed in (text field)", style = SilverTheme.type.body, color = c.textMuted) }
    }
}

@Composable
private fun RowScope.DepthTile(label: String, modifier: Modifier) {
    Box(Modifier.weight(1f).height(80.dp).then(modifier), contentAlignment = Alignment.Center) {
        Text(label, style = SilverTheme.type.button, color = SilverTheme.colors.textPrimary)
    }
}

@Composable
private fun ShoeColourRow() {
    Panel("Shoe colours (same in both themes)") {
        Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s)) {
            ShoeColors.forEach { Box(Modifier.size(SilverSpacing.swatch).background(it, SilverShapes.circle)) }
        }
    }
}

/** A raised card with a label, as the sections of the gallery. */
@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    val c = SilverTheme.colors
    Column(
        Modifier.fillMaxWidth().raised(c, SilverShapes.panel).padding(SilverSpacing.l),
        verticalArrangement = Arrangement.spacedBy(SilverSpacing.s),
    ) {
        Text(title.uppercase(), style = SilverTheme.type.label, color = c.textMuted)
        content()
    }
}

@LightNightPreviews
@Composable
private fun ColoursPreview() = ShoeStepTheme { ThemeGallery(GallerySection.COLOURS) }

@LightNightPreviews
@Composable
private fun TypePreview() = ShoeStepTheme { ThemeGallery(GallerySection.TYPE) }

@LightNightPreviews
@Composable
private fun DepthPreview() = ShoeStepTheme { ThemeGallery(GallerySection.DEPTH) }

@LightNightPreviews
@Composable
private fun ShoeColoursPreview() = ShoeStepTheme { ThemeGallery(GallerySection.SHOE_COLOURS) }

@Preview(name = "Backdrop light", widthDp = 390, heightDp = 844)
@Preview(name = "Backdrop night", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BackdropPreview() = ShoeStepTheme {
    Box(Modifier.fillMaxWidth().height(844.dp).backdrop(SilverTheme.colors))
}

@Preview(name = "Type at 200%", widthDp = 390, fontScale = 2f, showBackground = true)
@Composable
private fun TypeLargeFontPreview() = ShoeStepTheme { ThemeGallery(GallerySection.TYPE) }
