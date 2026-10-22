package io.github.drjkn7.shoestep.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 AA contrast (4.5:1 for normal text) for the text/background pairs in docs/DESIGN.md
 * section 8, computed from the tokens, in both themes (T49).
 */
class ContrastTest {

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertReadable(name: String, text: Color, background: Color) {
        val r = ratio(text, background)
        assertTrue("$name: contrast %.2f is below 4.5".format(r), r >= 4.5)
    }

    private fun checkTheme(c: SilverColors) {
        val theme = if (c.isNight) "night" else "light"
        val surfaces = listOf("card" to c.card, "card-register" to c.cardRegister) +
            c.dialog.mapIndexed { i, it -> "dialog stop $i" to it }
        surfaces.forEach { (surface, bg) ->
            assertReadable("$theme text-primary on $surface", c.textPrimary, bg)
            assertReadable("$theme text-muted on $surface", c.textMuted, bg)
            assertReadable("$theme accent-ink on $surface", c.accentInk, bg)
            assertReadable("$theme danger on $surface", c.danger, bg)
        }
        // Secondary buttons. Danger text is not used on them: night danger on the lightest sheen stop
        // is only 4.3:1, so destructive actions are text buttons on a card or the filled Delete button.
        c.sheen.forEachIndexed { i, bg ->
            assertReadable("$theme text-primary on sheen stop $i", c.textPrimary, bg)
            assertReadable("$theme accent-ink on sheen stop $i", c.accentInk, bg)
        }
        assertReadable("$theme text-muted on inset", c.textMuted, c.inset)
        assertReadable("$theme text-primary on inset", c.textPrimary, c.inset)
        c.accent.forEachIndexed { i, stop -> assertReadable("$theme white on accent stop $i", c.textOnAccent, stop) }
        assertReadable("$theme white on danger-fill", c.textOnAccent, c.dangerFill)
        // Text that sits on the backdrop must stay in its top part (headings, the pair header).
        c.backdrop.filter { it.first <= 0.15f }.forEach { (at, bg) ->
            assertReadable("$theme text-primary on backdrop at $at", c.textPrimary, bg)
            assertReadable("$theme text-muted on backdrop at $at", c.textMuted, bg)
        }
    }

    @Test fun lightThemeIsReadable() = checkTheme(LightSilverColors)

    @Test fun nightThemeIsReadable() = checkTheme(NightSilverColors)

    @Test fun accentGradientIsTheSameInBothThemes() {
        assertTrue(LightSilverColors.accent == NightSilverColors.accent)
    }
}
