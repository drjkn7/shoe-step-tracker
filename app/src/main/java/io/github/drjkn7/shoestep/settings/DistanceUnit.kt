package io.github.drjkn7.shoestep.settings

import java.util.Locale

/**
 * Settings → Units → Distance. Only how distance is shown: it is always stored in metres, so switching
 * never changes any data (T32).
 */
enum class DistanceUnit(val metres: Double) {
    KILOMETRES(1_000.0),
    MILES(1_609.344);

    /** [meters] in this unit, e.g. 2414.0 m → 1.5 mi. */
    fun fromMeters(meters: Double): Double = meters / metres

    companion object {
        /** Countries where walking and running distances are given in miles. */
        private val MILES_COUNTRIES = setOf("US", "GB", "LR", "MM")

        /** Before the user chooses, the phone's region decides (like the default currency). */
        fun defaultFor(locale: Locale): DistanceUnit = if (locale.country.uppercase(Locale.ROOT) in MILES_COUNTRIES) MILES else KILOMETRES

        /** Reads a stored value; anything missing or unexpected counts as "not chosen" (null). */
        fun fromStored(value: String?): DistanceUnit? = entries.firstOrNull { it.name == value }
    }
}
