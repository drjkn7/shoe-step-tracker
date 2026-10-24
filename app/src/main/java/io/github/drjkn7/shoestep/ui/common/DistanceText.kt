package io.github.drjkn7.shoestep.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.settings.DistanceUnit

/**
 * Kilometres or miles, from Settings → Units (or the phone's region until chosen). Provided once at the
 * top of the app (AppNavHost); a change redraws every screen straight away.
 */
val LocalDistanceUnit = staticCompositionLocalOf { DistanceUnit.KILOMETRES }

/** A distance as shown and as read out by TalkBack ("2.4 km" / "2.4 kilometres"). */
data class DistanceText(val shown: String, val spoken: String)

/**
 * "2.4 km", "About 2.4 km" while any part may still change ([unfinal]), or a dash when no distance is
 * known ([meters] null): never "0" for a missing distance (T34).
 */
@Composable
fun distanceText(meters: Double?, unfinal: Boolean): DistanceText {
    if (meters == null) {
        val dash = stringResource(R.string.dash)
        return DistanceText(dash, dash)
    }
    val unit = LocalDistanceUnit.current
    val number = Formatters.distance(meters, unit, LocalConfiguration.current.locales[0])
    val shown = stringResource(if (unit == DistanceUnit.MILES) R.string.distance_mi else R.string.distance_km, number)
    val spoken = stringResource(if (unit == DistanceUnit.MILES) R.string.distance_mi_spoken else R.string.distance_km_spoken, number)
    return if (unfinal) {
        DistanceText(stringResource(R.string.steps_about, shown), stringResource(R.string.steps_about, spoken))
    } else {
        DistanceText(shown, spoken)
    }
}
