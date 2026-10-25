package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import java.time.LocalDate

/** Milliseconds in a day: the date picker works in UTC midnight millis. */
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * A calendar pop-up for one day (purchase date, a past session's date). Only days in [earliest]..[latest]
 * can be picked; [onPick] gets the chosen epoch day. Not mocked up: Material's calendar in the pop-up
 * colours, with the Silver buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(
    title: String,
    initialEpochDay: Long?,
    earliest: LocalDate,
    latest: LocalDate,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val range = earliest.toEpochDay()..latest.toEpochDay()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochDay?.times(MILLIS_PER_DAY),
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) in range

            override fun isSelectableYear(year: Int): Boolean = year in earliest.year..latest.year
        },
    )
    val colors = DatePickerDefaults.colors(containerColor = SilverTheme.colors.dialog.first())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextActionButton(
                stringResource(R.string.action_ok),
                // floorDiv gives the epoch day from UTC midnight millis on every API level.
                onClick = { state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) } ?: onDismiss() },
                accent = true,
            )
        },
        dismissButton = { TextActionButton(stringResource(R.string.action_cancel), onDismiss) },
        shape = SilverShapes.dialog,
        colors = colors,
    ) {
        FilterTouchesWhenObscured()
        DatePicker(
            state = state,
            colors = colors,
            title = {
                Text(
                    title,
                    style = SilverTheme.type.label,
                    color = SilverTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}
