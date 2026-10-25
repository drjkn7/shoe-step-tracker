package io.github.drjkn7.shoestep.ui.shoe

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.session.PastTimeProblem
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.ui.common.CardLabel
import io.github.drjkn7.shoestep.ui.common.DialogAction
import io.github.drjkn7.shoestep.ui.common.FilterTouchesWhenObscured
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.common.NavIcon
import io.github.drjkn7.shoestep.ui.common.PickerField
import io.github.drjkn7.shoestep.ui.common.PrimaryButton
import io.github.drjkn7.shoestep.ui.common.ScreenColumn
import io.github.drjkn7.shoestep.ui.common.ScreenLoading
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.common.SilverDialog
import io.github.drjkn7.shoestep.ui.common.SilverScreen
import io.github.drjkn7.shoestep.ui.common.TextActionButton
import io.github.drjkn7.shoestep.ui.common.distanceText
import io.github.drjkn7.shoestep.ui.common.stepsCountText
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Add past session (PNGs 12, 13, 29): the pair (fixed), Date, Start and End, then what Health Connect
 * has for that time. Problems appear under the times and keep Save disabled.
 */
@Composable
fun AddPastSessionScreen(viewModel: AddPastSessionViewModel, onBack: () -> Unit) {
    val status = viewModel.status
    LaunchedEffect(status) { if (status == PastStatus.Saved) onBack() }

    SilverScreen(
        title = stringResource(R.string.title_add_past_session),
        navIcon = NavIcon.CLOSE,
        onNavigate = onBack,
        actions = {
            if (status == PastStatus.Editing || status == PastStatus.Saving) {
                PrimaryButton(stringResource(R.string.action_save), viewModel::save, enabled = viewModel.canSave)
            }
        },
    ) {
        when (status) {
            PastStatus.Loading -> ScreenLoading()
            PastStatus.NotFound -> ScreenColumn {
                SilverCard { Text(stringResource(R.string.form_not_found), style = SilverTheme.type.body, color = SilverTheme.colors.textPrimary) }
            }
            else -> PastContent(
                shoeName = viewModel.shoeName,
                times = viewModel.times,
                problem = viewModel.problem,
                preview = viewModel.preview,
                canSave = viewModel.canSave,
                onChange = viewModel::update,
                onSave = viewModel::save,
            )
        }
    }
}

@Composable
private fun PastContent(
    shoeName: String,
    times: PastTimes,
    problem: PastProblem?,
    preview: PastPreview,
    canSave: Boolean,
    onChange: (PastTimes) -> Unit,
    onSave: () -> Unit,
) {
    val c = SilverTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    var pickDate by rememberSaveable { mutableStateOf(false) }
    // Which time pop-up is open: "start", "end" or none.
    var pickTime by rememberSaveable { mutableStateOf<String?>(null) }
    val noteStyle = SilverTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp)

    ScreenColumn {
        SilverCard {
            Column(verticalArrangement = Arrangement.spacedBy(SilverSpacing.m)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    CardLabel(stringResource(R.string.past_pair), heading = false)
                    Text(shoeName, style = SilverTheme.type.section.copy(fontSize = 18.sp), color = c.textPrimary)
                }
                PickerField(
                    label = stringResource(R.string.past_date),
                    value = dayText(times.epochDay, locale),
                    icon = painterResource(R.drawable.ic_calendar_month),
                    onClick = { pickDate = true },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        PickerField(
                            label = stringResource(R.string.past_start),
                            value = minuteText(times.startMinute, locale),
                            icon = painterResource(R.drawable.ic_chevron_right),
                            onClick = { pickTime = "start" },
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        PickerField(
                            label = stringResource(R.string.past_end),
                            value = minuteText(times.endMinute, locale),
                            icon = painterResource(R.drawable.ic_chevron_right),
                            onClick = { pickTime = "end" },
                        )
                    }
                }
                problem?.let {
                    Text(
                        problemText(it, locale),
                        style = SilverTheme.type.note,
                        color = c.danger,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (problem == null) PreviewBox(preview)
            }
        }

        Text(stringResource(R.string.past_help), style = noteStyle, color = c.textMuted, modifier = Modifier.padding(horizontal = SilverSpacing.xs))

        PrimaryButton(stringResource(R.string.action_save), onSave, Modifier.fillMaxWidth(), enabled = canSave)
    }

    if (pickDate) {
        PastDatePicker(
            initialEpochDay = times.epochDay,
            onPick = {
                pickDate = false
                onChange(times.copy(epochDay = it))
            },
            onDismiss = { pickDate = false },
        )
    }
    pickTime?.let { which ->
        val start = which == "start"
        PastTimeDialog(
            title = stringResource(if (start) R.string.past_pick_start else R.string.past_pick_end),
            initialMinute = if (start) times.startMinute else times.endMinute,
            onPick = {
                pickTime = null
                onChange(if (start) times.copy(startMinute = it) else times.copy(endMinute = it))
            },
            onDismiss = { pickTime = null },
        )
    }
}

/** "About 6,120 steps · 4.6 km" on a light accent tint, or why there is no number yet. */
@Composable
private fun PreviewBox(preview: PastPreview) {
    val c = SilverTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.accentInk.copy(alpha = 0.08f), SilverShapes.input)
            .padding(SilverSpacing.m)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(SilverSpacing.xs),
    ) {
        val body = SilverTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp)
        when (preview) {
            PastPreview.Loading -> Text(stringResource(R.string.past_preview_loading), style = body, color = c.textMuted)
            is PastPreview.Known -> {
                val steps = stepsCountText(preview.steps)
                val distance = preview.meters?.let { distanceText(it, unfinal = false) }
                Text(
                    if (distance != null) stringResource(R.string.past_preview_steps_distance, steps, distance.shown)
                    else stringResource(R.string.past_preview_steps, steps),
                    style = SilverTheme.type.button.copy(fontSize = 18.sp),
                    color = c.accentInk,
                )
                Text(stringResource(R.string.past_preview_note), style = body, color = c.textPrimary)
            }
            PastPreview.NoData -> Text(stringResource(R.string.past_preview_none), style = body, color = c.textPrimary)
            PastPreview.Unavailable -> Text(stringResource(R.string.past_preview_unavailable), style = body, color = c.textPrimary)
        }
    }
}

@Composable
private fun problemText(problem: PastProblem, locale: Locale): String = when (problem) {
    is PastProblem.Time -> stringResource(
        when (problem.problem) {
            PastTimeProblem.END_NOT_AFTER_START -> R.string.past_end_before_start
            PastTimeProblem.IN_FUTURE -> R.string.past_in_future
            PastTimeProblem.TOO_OLD -> R.string.past_too_old
        },
    )
    is PastProblem.Overlap -> {
        val other = problem.other.session
        val end = other.endUtc
        if (end == null) {
            stringResource(R.string.past_overlap_running, problem.other.shoeName)
        } else {
            stringResource(
                R.string.past_overlap,
                problem.other.shoeName,
                Formatters.time(other.startUtc, other.zoneId, locale),
                Formatters.time(end, other.zoneId, locale),
            )
        }
    }
}

/** "Sun 18 Oct 2026" style, in the phone's language. */
private fun dayText(epochDay: Long, locale: Locale): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

private fun minuteText(minute: Int, locale: Locale): String =
    LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

/** The calendar, limited to the last 30 days (older sessions can't be added). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePicker(initialEpochDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val earliest = remember { today.minusDays(SessionManager.PAST_LIMIT_MILLIS / MILLIS_PER_DAY) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochDay * MILLIS_PER_DAY,
        yearRange = earliest.year..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) in earliest.toEpochDay()..today.toEpochDay()

            override fun isSelectableYear(year: Int): Boolean = year in earliest.year..today.year
        },
    )
    // Like the purchase date: Material's calendar in the pop-up colours, with the Silver buttons.
    val colors = DatePickerDefaults.colors(containerColor = SilverTheme.colors.dialog.first())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextActionButton(
                stringResource(R.string.action_ok),
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
                    stringResource(R.string.past_pick_date),
                    style = SilverTheme.type.label,
                    color = SilverTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

/** Hours and minutes typed in (Material's time input), in the phone's 12- or 24-hour style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastTimeDialog(title: String, initialMinute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = is24Hour)
    SilverDialog(
        title = title,
        onDismiss = onDismiss,
        actions = listOf(
            DialogAction(stringResource(R.string.action_cancel), DialogAction.Kind.TEXT, onDismiss),
            DialogAction(stringResource(R.string.action_ok), DialogAction.Kind.PRIMARY, onClick = { onPick(state.hour * 60 + state.minute) }),
        ),
    ) {
        TimeInput(state = state, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
