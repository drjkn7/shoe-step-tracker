package io.github.drjkn7.shoestep.export

import io.github.drjkn7.shoestep.core.MILLIS_PER_MINUTE
import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.core.zoneIdOrUtc
import io.github.drjkn7.shoestep.data.db.SessionEntity
import io.github.drjkn7.shoestep.data.db.SessionExportRow
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds the two exports (product owner: separate files for pairs and sessions). Pure Kotlin so the
 * columns and formats are unit-tested; labels come from the caller (string resources in the app).
 * Totals are computed by the same SQL as the screens, never stored.
 */
object CsvExporter {

    val PAIRS_HEADER = listOf(
        "Name", "Brand", "Model", "Type", "Price", "Currency", "Purchase date", "Colour", "Archived",
        "Sessions", "Total steps", "Steps status", "Walking minutes", "Last worn",
        "Total distance in metres", "Distance status",
    )

    val SESSIONS_HEADER = listOf(
        "Pair", "Started", "Ended", "Time zone", "Status", "Walking minutes", "Steps", "Steps status",
        "Distance in metres", "Distance status",
    )

    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun pairs(
        rows: List<ShoeWithStats>,
        typeLabel: (ShoeType) -> String,
        colourLabel: (Int) -> String,
        zone: ZoneId,
    ): String = buildString {
        append(Csv.header(PAIRS_HEADER))
        for (row in rows) {
            val shoe = row.shoe
            append(
                Csv.row(
                    shoe.name,
                    shoe.brand,
                    shoe.model,
                    typeLabel(shoe.type),
                    shoe.priceMinor?.let { price(it, shoe.currencyCode) },
                    shoe.currencyCode,
                    shoe.purchaseEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
                    colourLabel(shoe.accentColor),
                    if (shoe.archived) "yes" else "no",
                    row.sessionCount,
                    row.totalSteps,
                    readStatus(row.hasPendingSteps, row.hasUnfinalSteps),
                    row.activeMillis.coerceAtLeast(0) / MILLIS_PER_MINUTE,
                    row.lastUsedUtc?.let { dateTime(it, zone) },
                    row.totalDistanceMeters?.let(::meters),
                    readStatus(row.hasPendingDistance, row.hasUnfinalDistance),
                ),
            )
        }
    }

    fun sessions(rows: List<SessionExportRow>): String = buildString {
        append(Csv.header(SESSIONS_HEADER))
        for (row in rows) {
            val session = row.session
            val zone = zoneIdOrUtc(session.zoneId)
            append(
                Csv.row(
                    row.shoeName,
                    dateTime(session.startUtc, zone),
                    session.endUtc?.let { dateTime(it, zone) },
                    zone.id,
                    status(session),
                    row.activeMillis.coerceAtLeast(0) / MILLIS_PER_MINUTE,
                    row.totalSteps,
                    readStatus(row.hasPendingSteps, row.hasUnfinalSteps),
                    row.totalDistanceMeters?.let(::meters),
                    readStatus(row.hasPendingDistance, row.hasUnfinalDistance),
                ),
            )
        }
    }

    /**
     * Whole metres, always metric whatever the app shows, so other apps can read it. An unknown
     * distance is an empty cell, never 0; negative or unreadable values count as 0.
     */
    fun meters(value: Double): Long = if (value.isFinite() && value > 0) BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toLong() else 0

    /** Major units with the currency's own decimals, e.g. 12000 EUR → 120.00, 500 JPY → 500. */
    fun price(minor: Long, currencyCode: String): String =
        BigDecimal.valueOf(minor).movePointLeft(Money.fractionDigits(currencyCode).coerceAtLeast(0)).toPlainString()

    /** Status of a step count or a distance: they follow the same rules, each with its own flags. */
    fun readStatus(hasPending: Boolean, hasUnfinal: Boolean): String = when {
        hasPending -> "counted later"
        hasUnfinal -> "provisional"
        else -> "final"
    }

    private fun status(session: SessionEntity): String = when (session.state) {
        SessionState.RUNNING -> "running"
        SessionState.PAUSED -> "paused"
        SessionState.ENDED -> when {
            session.addedLater -> "ended (added later)"
            session.endReason == EndReason.SWITCH -> "ended (switched pairs)"
            else -> "ended"
        }
    }

    private fun dateTime(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(DATE_TIME)
}
