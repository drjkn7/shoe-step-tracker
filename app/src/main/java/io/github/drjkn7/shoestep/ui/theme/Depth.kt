package io.github.drjkn7.shoestep.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/*
 * Depth recipes (docs/DESIGN.md section 4): soft drop shadows plus a bright top edge, or an inset
 * for pressed-in controls. No blur of what is behind (no glass). Each modifier also paints the
 * surface itself, because the order matters: drop shadows go under the fill and inner shadows on top
 * of it (an inner shadow drawn before the fill would be hidden).
 *
 * Colours are passed in (SilverTheme.colors) rather than read inside, so these stay plain modifiers.
 * Keep to one raised surface per list row; nested shadows in long lists cost scrolling smoothness.
 */

/** Cards and secondary buttons: lightly raised. */
fun Modifier.raised(colors: SilverColors, shape: Shape, fill: Brush = SolidColor(colors.card)): Modifier {
    val s = colors.shadows
    return this
        .dropShadow(shape, Shadow(radius = 16.dp, color = s.raisedKey, offset = DpOffset(0.dp, 6.dp)))
        .dropShadow(shape, Shadow(radius = 2.dp, color = s.raisedAmbient, offset = DpOffset(0.dp, 1.dp)))
        .background(fill, shape)
        .topHighlight(shape, s.raisedHighlight)
}

/** Pop-ups and the overflow menu: higher above the page. */
fun Modifier.floating(colors: SilverColors, shape: Shape, fill: Brush = colors.dialogBrush()): Modifier {
    val s = colors.shadows
    return this
        .dropShadow(shape, Shadow(radius = 36.dp, color = s.floatingKey, offset = DpOffset(0.dp, 16.dp)))
        .dropShadow(shape, Shadow(radius = 4.dp, color = s.floatingAmbient, offset = DpOffset(0.dp, 2.dp)))
        .background(fill, shape)
        .topHighlight(shape, s.floatingHighlight)
}

/** Inputs, unselected chips, the Paused badge and switch tracks: pressed into the surface. */
fun Modifier.pressedIn(colors: SilverColors, shape: Shape, fill: Brush = SolidColor(colors.inset)): Modifier {
    val s = colors.shadows
    return this
        .background(fill, shape)
        .innerShadow(shape, Shadow(radius = 5.dp, color = s.pressedDark, offset = DpOffset(0.dp, 2.dp)))
        .innerShadow(shape, Shadow(radius = 1.dp, color = s.pressedLight, offset = DpOffset(0.dp, (-1).dp)))
}

/** Primary buttons and Add shoe: the accent gradient with a purple glow underneath. */
fun Modifier.accentGlow(colors: SilverColors, shape: Shape): Modifier = this
    .dropShadow(shape, Shadow(radius = 20.dp, color = colors.shadows.accentGlow, offset = DpOffset(0.dp, 7.dp)))
    .background(colors.accentBrush(), shape)

/** The screen backdrop: pale sky at the top to steel blue (night: steel navy) at the bottom. */
fun Modifier.backdrop(colors: SilverColors): Modifier = background(colors.backdropBrush())

/** The 1 dp bright top edge of raised and floating surfaces. */
private fun Modifier.topHighlight(shape: Shape, color: Color): Modifier =
    innerShadow(shape, Shadow(radius = 0.5.dp, color = color, offset = DpOffset(0.dp, 1.dp)))
