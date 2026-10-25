package io.github.drjkn7.shoestep.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.drjkn7.shoestep.R

/** How a step total is labelled, from the states of the segments behind it. */
enum class StepsLabel {
    /** Every closed segment is final (or unavailable): "1,234 steps". */
    EXACT,

    /** Some counts may still change for up to a day: "About 1,234 steps". */
    ABOUT,

    /** Some segments have no count yet: "1,234 steps (counted later)". */
    COUNTED_LATER,
    ;

    companion object {
        fun of(hasPendingSteps: Boolean, hasUnfinalSteps: Boolean): StepsLabel = when {
            hasPendingSteps -> COUNTED_LATER
            hasUnfinalSteps -> ABOUT
            else -> EXACT
        }
    }
}

/** "1,234 steps" in the user's locale, without any label. */
@Composable
fun stepsCountText(steps: Long): String = pluralStringResource(
    R.plurals.steps_count,
    steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    Formatters.count(steps, LocalConfiguration.current.locales[0]),
)

/** [text] (already formatted) with the wording for [label]. */
@Composable
fun labelledSteps(text: String, label: StepsLabel): String = when (label) {
    StepsLabel.EXACT -> text
    StepsLabel.ABOUT -> stringResource(R.string.steps_about, text)
    StepsLabel.COUNTED_LATER -> stringResource(R.string.history_steps_pending, text)
}

/** Walking time as "1 h 05 min" or "12 min" (pair stats and History rows). */
@Composable
fun durationText(millis: Long): String {
    val (hours, minutes) = Formatters.hoursAndMinutes(millis)
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}
