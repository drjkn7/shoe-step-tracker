package io.github.drjkn7.shoestep.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.drjkn7.shoestep.R

/**
 * Chakra Petch, bundled in res/font (SIL Open Font License, licenses/ChakraPetch-OFL.txt). Never a
 * downloadable font: the app has no INTERNET permission.
 */
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** Silver Matte type scale (docs/DESIGN.md section 2). All sizes in sp, so they follow the font-size setting. */
@Immutable
data class SilverType(
    /** Screen titles, big numbers. */
    val display: TextStyle,
    /** Shoe name, pop-up title. */
    val section: TextStyle,
    /** Button text, row values. */
    val button: TextStyle,
    /** Subtitles, inputs. */
    val body: TextStyle,
    /** Small capitals-style labels, chips. */
    val label: TextStyle,
    /** Helper text; the smallest text in the app. */
    val note: TextStyle,
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = ChakraPetch,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val DefaultSilverType = SilverType(
    // Tracking was tuned for Inter in the mockups; DESIGN.md asks to confirm it for Chakra Petch.
    display = style(42, 46, FontWeight.Bold, tracking = -1.7f),
    section = style(17, 24, FontWeight.Bold),
    button = style(15, 20, FontWeight.SemiBold),
    body = style(14, 21, FontWeight.Normal),
    label = style(12, 16, FontWeight.SemiBold, tracking = 0.2f),
    note = style(12, 16, FontWeight.Normal),
)

/**
 * The same scale mapped onto Material's names, so Material components used under the hood (and
 * screens not yet restyled) pick up Chakra Petch at sizes close to the design.
 */
fun SilverType.toMaterial() = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display.copy(fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
    headlineLarge = display.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineMedium = section.copy(fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = section.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = section.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = section,
    titleSmall = button,
    bodyLarge = body.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = body,
    bodySmall = note,
    labelLarge = button,
    labelMedium = label,
    labelSmall = label,
)
