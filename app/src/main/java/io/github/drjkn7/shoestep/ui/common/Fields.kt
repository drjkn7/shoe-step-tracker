package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.pressedIn

/**
 * Silver Matte text field: label above in small capitals, pressed-in box, then either the error
 * ([error], danger colour) or a helper line such as "14/60" ([helper], right-aligned). The label and
 * the lines below belong to the field, so TalkBack reads them with it.
 */
@Composable
fun SilverTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    helper: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val c = SilverTheme.colors
    val textStyle = SilverTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp, color = c.textPrimary)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(c.accentInk),
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardLabel(label, heading = false)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .pressedIn(c, SilverShapes.input)
                        .then(if (error != null) Modifier.border(1.5.dp, c.danger, SilverShapes.input) else Modifier)
                        .padding(horizontal = SilverSpacing.m, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = c.textMuted)
                    }
                    innerTextField()
                }
                when {
                    error != null -> Text(error, style = SilverTheme.type.note, color = c.danger)
                    helper != null -> Text(
                        helper,
                        style = SilverTheme.type.note,
                        color = c.textMuted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

/**
 * A field that opens a picker when tapped (currency, purchase date): label above, pressed-in box with
 * the current value and an icon at the end. [modifier] goes on the tappable box, for its TalkBack text.
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
) {
    val c = SilverTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CardLabel(label, heading = false)
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .pressedIn(c, SilverShapes.input)
                .clip(SilverShapes.input)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = SilverSpacing.m, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = SilverTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = if (placeholder) c.textMuted else c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(22.dp))
        }
    }
}
