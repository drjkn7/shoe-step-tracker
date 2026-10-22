package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ui.theme.SilverColors
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.accentGlow
import io.github.drjkn7.shoestep.ui.theme.raised
import io.github.drjkn7.shoestep.ui.theme.sheenBrush

/*
 * Silver Matte buttons (docs/DESIGN.md section 6). Every button is at least 48 dp tall and its label
 * wraps instead of being cut off at large font sizes. A disabled button is faded and does nothing.
 */

/** Main action: accent gradient, white label, purple glow. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = SilverTheme.colors
    ButtonBase(
        text = text,
        textColor = c.textOnAccent,
        onClick = onClick,
        enabled = enabled,
        surface = if (enabled) Modifier.accentGlow(c, SilverShapes.pill) else Modifier.background(c.accentBrushFaded(), SilverShapes.pill),
        modifier = modifier,
    )
}

/** Second choice next to a primary button (Pause, Replace tag): raised silver sheen. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = SilverTheme.colors
    ButtonBase(
        text = text,
        textColor = c.textPrimary,
        onClick = onClick,
        enabled = enabled,
        surface = Modifier.raised(c, SilverShapes.pill, c.sheenBrush()),
        modifier = modifier,
    )
}

/** Quiet action with no fill (Cancel, Not now, Show older sessions). [danger] for "Unlink tag" and similar. */
@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    accent: Boolean = false,
) {
    val c = SilverTheme.colors
    ButtonBase(
        text = text,
        textColor = when {
            danger -> c.danger
            accent -> c.accentInk
            else -> c.textPrimary
        },
        onClick = onClick,
        enabled = enabled,
        surface = Modifier,
        modifier = modifier,
    )
}

/** Permanent deletes only: filled danger colour with a white label. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = SilverTheme.colors
    ButtonBase(
        text = text,
        textColor = c.textOnAccent,
        onClick = onClick,
        enabled = enabled,
        surface = Modifier
            .dropShadow(SilverShapes.pill, Shadow(radius = 16.dp, color = c.shadows.raisedKey, offset = DpOffset(0.dp, 6.dp)))
            .background(SolidColor(c.dangerFill), SilverShapes.pill),
        modifier = modifier,
    )
}

@Composable
private fun ButtonBase(
    text: String,
    textColor: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    surface: Modifier,
) {
    Box(
        modifier
            .defaultMinSize(minWidth = SilverSpacing.touch, minHeight = SilverSpacing.touch)
            .then(surface)
            .clip(SilverShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SilverTheme.type.button, color = textColor, textAlign = TextAlign.Center)
    }
}

/** Round 48 dp top-bar button (Settings, Back, Close, More). [description] is read out by TalkBack. */
@Composable
fun IconCircleButton(icon: Painter, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = SilverTheme.colors
    Box(
        modifier
            .size(SilverSpacing.touch)
            .raised(c, SilverShapes.circle, c.sheenBrush())
            .clip(SilverShapes.circle)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = c.textPrimary, modifier = Modifier.size(24.dp))
    }
}

/** Home's "+ Add shoe": gradient pill with a plus icon, bottom right, with the accent glow. */
@Composable
fun AddShoeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = SilverTheme.colors
    Row(
        modifier
            .defaultMinSize(minHeight = 56.dp)
            .accentGlow(c, SilverShapes.pill)
            .clip(SilverShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.textOnAccent)
        Text(stringResource(R.string.nav_add_shoe), style = SilverTheme.type.button.copy(fontSize = SilverTheme.type.section.fontSize), color = c.textOnAccent)
    }
}

private const val DISABLED_ALPHA = 0.45f

private fun SilverColors.accentBrushFaded() =
    Brush.horizontalGradient(accent.map { it.copy(alpha = 0.6f) })
