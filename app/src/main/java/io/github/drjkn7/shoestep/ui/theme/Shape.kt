package io.github.drjkn7.shoestep.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner shapes (docs/DESIGN.md section 5). */
object SilverShapes {
    /** Cards and panels. */
    val panel = RoundedCornerShape(26.dp)
    /** Pop-ups. */
    val dialog = RoundedCornerShape(28.dp)
    /** Bottom sheets. */
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    /** Buttons, badges, chips, segmented controls. */
    val pill = RoundedCornerShape(percent = 50)
    /** Text fields. */
    val input = RoundedCornerShape(16.dp)
    /** Icon buttons, colour markers and swatches. */
    val circle = CircleShape
}

/** Material's shape slots filled from the tokens above. */
val SilverMaterialShapes = Shapes(
    extraSmall = SilverShapes.input,
    small = SilverShapes.input,
    medium = SilverShapes.panel,
    large = SilverShapes.panel,
    extraLarge = SilverShapes.dialog,
)

/** Spacing and sizes (docs/DESIGN.md section 5). */
object SilverSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    /** Left and right padding of every screen. */
    val screen = 20.dp
    /** Smallest touch target. */
    val touch = 48.dp
    /** Colour circle of a pair. */
    val marker = 24.dp
    /** Colour swatch on the shoe form. */
    val swatch = 30.dp
}
