package io.github.drjkn7.shoestep.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.accentBrush
import io.github.drjkn7.shoestep.ui.theme.accentGlow
import io.github.drjkn7.shoestep.ui.theme.pressedIn
import io.github.drjkn7.shoestep.ui.theme.raised

/**
 * A raised silver card. With [onClick] the whole card is one button (its contents are read out
 * together by TalkBack). One raised surface per list row: put no other raised items inside a row card.
 */
@Composable
fun SilverCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    register: Boolean = false,
    /** Space inside the card; the Show archived row uses less top and bottom, like the mockup's pill. */
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = SilverTheme.colors
    val fill = SolidColor(if (register) c.cardRegister else c.card)
    Column(
        modifier
            .fillMaxWidth()
            .raised(c, SilverShapes.panel, fill)
            .clip(SilverShapes.panel)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(SilverSpacing.s),
        content = content,
    )
}

/**
 * Small capitals label at the top of a card ("STATS", "HISTORY") and above form fields. Drawn in
 * capitals but read out and found by its normal wording, and marked as a heading for TalkBack.
 */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier, heading: Boolean = true) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text.uppercase(locale),
        style = SilverTheme.type.label,
        color = SilverTheme.colors.textMuted,
        modifier = modifier.clearAndSetSemantics {
            this.text = AnnotatedString(text)
            if (heading) heading()
        },
    )
}

/** "Running" (accent gradient) or "Paused" (pressed in) pill on a Home row. */
@Composable
fun StatusBadge(text: String, running: Boolean, modifier: Modifier = Modifier) {
    val c = SilverTheme.colors
    Box(
        modifier
            .then(
                if (running) Modifier.background(c.accentBrush(), SilverShapes.pill)
                else Modifier.pressedIn(c, SilverShapes.pill),
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = SilverTheme.type.label, color = if (running) c.textOnAccent else c.textPrimary)
    }
}

/** One choice among several (shoe type). Selected = accent gradient with glow; others pressed in. */
@Composable
fun SilverChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = SilverTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = SilverSpacing.touch)
            .then(if (selected) Modifier.accentGlow(c, SilverShapes.pill) else Modifier.pressedIn(c, SilverShapes.pill))
            .clip(SilverShapes.pill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize),
            color = if (selected) c.textOnAccent else c.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A row with a label (and optional note) and an on/off switch ("Show archived", "Session
 * notification"). The whole row is the touch target and is read out as one switch.
 */
@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, note: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SilverSpacing.touch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize), color = SilverTheme.colors.textPrimary)
            note?.let { Text(it, style = SilverTheme.type.note, color = SilverTheme.colors.textMuted) }
        }
        SwitchVisual(checked)
    }
}

/** The switch drawing only; the surrounding [SwitchRow] handles touch and TalkBack. */
@Composable
private fun SwitchVisual(checked: Boolean) {
    val c = SilverTheme.colors
    val knobOffset by animateDpAsState(if (checked) 22.dp else 3.dp, label = "knob")
    Box(
        Modifier
            .width(52.dp)
            .height(30.dp)
            .then(if (checked) Modifier.background(c.accentBrush(), SilverShapes.pill) else Modifier.pressedIn(c, SilverShapes.pill)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset(knobOffset.roundToPx(), 0) }
                .size(26.dp)
                .raised(c, SilverShapes.circle, SolidColor(if (c.isNight) c.sheen.first() else c.dialog.first())),
        )
    }
}

/**
 * Two or three exclusive choices in one pill (Kilometres / Miles; Light / Dark / System). Pressed-in
 * track; the selected segment uses the accent gradient. Each segment is at least 48 dp tall.
 * [description] names the group for TalkBack ("Distance", "Theme").
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val c = SilverTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .pressedIn(c, SilverShapes.pill)
            .padding(4.dp)
            .semantics { contentDescription = description }
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = SilverSpacing.touch)
                    .then(if (isSelected) Modifier.accentGlow(c, SilverShapes.pill) else Modifier)
                    .clip(SilverShapes.pill)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = SilverSpacing.s, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = SilverTheme.type.button,
                    color = if (isSelected) c.textOnAccent else c.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
