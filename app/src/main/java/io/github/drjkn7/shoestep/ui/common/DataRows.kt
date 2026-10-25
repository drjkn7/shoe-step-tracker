package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme

/**
 * One figure in the stats grid: label, value, an optional small [note] right under the value ("Some
 * counted later"), and an optional second line (e.g. distance under steps). The spoken versions, when
 * given, are what TalkBack reads instead ("2.4 kilometres" for "2.4 km").
 */
data class StatItem(
    val label: String,
    val value: String,
    val extra: String? = null,
    val valueSpoken: String? = null,
    val extraSpoken: String? = null,
    val note: String? = null,
)

/** Shows one text but has TalkBack read [spoken] instead (inside a merged row or cell). */
internal fun Modifier.spokenAs(spoken: String?): Modifier =
    if (spoken == null) this else clearAndSetSemantics { text = AnnotatedString(spoken) }

/** The pair's stats in two columns (PNG 08). Each figure is read out as "label, value". */
@Composable
fun StatsGrid(items: List<StatItem>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
                row.forEach { StatCell(it, Modifier.weight(1f)) }
                if (row.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun StatCell(item: StatItem, modifier: Modifier) {
    val c = SilverTheme.colors
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(item.label, style = SilverTheme.type.body, color = c.textMuted)
        Text(
            item.value,
            style = SilverTheme.type.section.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = c.textPrimary,
            modifier = Modifier.spokenAs(item.valueSpoken),
        )
        item.note?.let { Text(it, style = SilverTheme.type.note, color = c.textMuted) }
        item.extra?.let {
            Text(it, style = SilverTheme.type.body.copy(fontSize = 16.sp), color = c.textPrimary, modifier = Modifier.spokenAs(item.extraSpoken))
        }
    }
}

/**
 * A row that opens something (PNG 21: "Default currency", "Privacy statement"): title, an optional
 * value under it, and a chevron at the end. The whole row is one button, at least 56 dp tall.
 */
@Composable
fun LinkRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, value: String? = null) {
    val c = SilverTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(SilverShapes.input)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = SilverSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SilverTheme.type.body.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium), color = c.textPrimary)
            value?.let { Text(it, style = SilverTheme.type.body, color = c.textMuted) }
        }
        // The icon is auto-mirrored, so it points the other way in right-to-left languages.
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(22.dp))
    }
}

/**
 * One session in a pair's history (PNG 08): "date, time · duration" with an optional note below on
 * the left; steps in accent ink with the distance below on the right. Read out as one item. With
 * [onDelete] ("Added later" sessions only) a Delete button sits under the note; TalkBack reads it as
 * [deleteDescription].
 */
@Composable
fun HistoryRow(
    title: String,
    note: String?,
    steps: String,
    distance: String?,
    modifier: Modifier = Modifier,
    distanceSpoken: String? = null,
    onDelete: (() -> Unit)? = null,
    deleteDescription: String? = null,
    /** "No steps": shown quietly instead of in accent ink. */
    stepsMuted: Boolean = false,
) {
    val c = SilverTheme.colors
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = c.divider)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = SilverTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = c.textPrimary)
                note?.let { Text(it, style = SilverTheme.type.note, color = c.textMuted) }
                onDelete?.let {
                    // Lined up with the text above: the button's own side padding is 20 dp.
                    TextActionButton(
                        stringResource(R.string.action_delete),
                        onClick = it,
                        danger = true,
                        modifier = Modifier.offset(x = (-20).dp).semantics { deleteDescription?.let { d -> contentDescription = d } },
                    )
                }
            }
            Column(Modifier.widthIn(max = 160.dp), horizontalAlignment = Alignment.End) {
                if (stepsMuted) {
                    Text(steps, style = SilverTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = c.textMuted, textAlign = TextAlign.End)
                } else {
                    Text(steps, style = SilverTheme.type.button, color = c.accentInk, textAlign = TextAlign.End)
                }
                distance?.let {
                    Text(it, style = SilverTheme.type.body, color = c.textMuted, textAlign = TextAlign.End, modifier = Modifier.spokenAs(distanceSpoken))
                }
            }
        }
    }
}
