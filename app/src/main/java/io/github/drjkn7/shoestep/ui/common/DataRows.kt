package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme

/** One figure in the stats grid: label, value, and an optional second line (e.g. distance under steps). */
data class StatItem(val label: String, val value: String, val extra: String? = null)

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
        Text(item.value, style = SilverTheme.type.section.copy(fontSize = 20.sp, lineHeight = 26.sp), color = c.textPrimary)
        item.extra?.let { Text(it, style = SilverTheme.type.body.copy(fontSize = 16.sp), color = c.textPrimary) }
    }
}

/**
 * One session in a pair's history (PNG 08): "date, time · duration" with an optional note below on
 * the left; steps in accent ink with the distance below on the right. Read out as one item.
 */
@Composable
fun HistoryRow(
    title: String,
    note: String?,
    steps: String,
    distance: String?,
    modifier: Modifier = Modifier,
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
            }
            Column(Modifier.widthIn(max = 160.dp), horizontalAlignment = Alignment.End) {
                Text(steps, style = SilverTheme.type.button, color = c.accentInk, textAlign = TextAlign.End)
                distance?.let { Text(it, style = SilverTheme.type.body, color = c.textMuted, textAlign = TextAlign.End) }
            }
        }
    }
}
