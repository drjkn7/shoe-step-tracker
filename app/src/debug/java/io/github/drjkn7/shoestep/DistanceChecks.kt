package io.github.drjkn7.shoestep

import io.github.drjkn7.shoestep.steps.DistanceRead
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.steps.StepsRead
import java.util.Locale

/**
 * Debug build only: two read-only distance checks (added in M2 Phase 4; still useful now distance is on screen).
 * Nothing is saved or logged; the results are shown in a pop-up in the test app.
 */
object DistanceChecks {

    /** Each pair's distance total as stored, to see the backfill working. */
    suspend fun totals(container: AppContainer): String {
        val pairs = container.database.shoeDao().getAllWithStats()
        if (pairs.isEmpty()) return "No pairs yet."
        val allowed = if (container.stepsSource.distanceAllowed()) "Distance is allowed." else "Distance is not allowed yet."
        return allowed + "\n\n" + pairs.joinToString("\n") { p ->
            val distance = p.totalDistanceMeters?.let { String.format(Locale.UK, "%.2f km", it / 1000) } ?: "no distance yet"
            "${p.shoe.name}: $distance"
        }
    }

    /**
     * Whether Health Connect answers or refuses reads 29 to 40 days back, for steps and for distance.
     * Shows only "answered" or "refused", never the values. If steps are answered where distance is
     * refused, the 30-day limit counts from each permission's own grant; if both are answered, it
     * counts from the first grant.
     */
    suspend fun howFarBack(container: AppContainer): String {
        val source = container.stepsSource
        val stepsOk = source.access() == StepsAccess.READY
        val distanceOk = source.distanceAllowed()
        if (!stepsOk || !distanceOk) return "Allow both steps and distance first (Home → Allow)."
        val now = container.clock.nowUtcMillis()
        val lines = mutableListOf<String>()
        for (days in listOf(29, 31, 33, 35, 40)) {
            val end = now - days * DAY_MILLIS
            val start = end - HOUR_MILLIS
            val steps = if (source.read(start, end) is StepsRead.Failed) "refused" else "answered"
            val distance = if (source.readDistance(start, end) is DistanceRead.Failed) "refused" else "answered"
            lines += "$days days ago: steps $steps, distance $distance"
        }
        return lines.joinToString("\n")
    }

    private const val HOUR_MILLIS = 60 * 60 * 1000L
    private const val DAY_MILLIS = 24 * HOUR_MILLIS
}
