package io.github.drjkn7.shoestep.export

import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.data.db.SessionExportRow
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.data.model.EndReason
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.data.model.ShoeType
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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
    )

    val SESSIONS_HEADER = listOf(
        "Pair", "Started", "Ended", "Time zone", "Status", "Walking minutes", "Steps", "Steps status",
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
                    stepsStatus(row.hasPendingSteps, row.hasUnfinalSteps),
                    row.activeMillis.coerceAtLeast(0) / 60_000,
                    row.lastUsedUtc?.let { dateTime(it, zone) },
                ),
            )
        }
    }

    fun sessions(rows: List<SessionExportRow>): String = buildString {
        append(Csv.header(SESSIONS_HEADER))
        for (row in rows) {
            val session = row.session
            val zone = zoneOrUtc(session.zoneId)
            append(
                Csv.row(
                    row.shoeName,
                    dateTime(session.startUtc, zone),
                    session.endUtc?.let { dateTime(it, zone) },
                    zone.id,
                    status(session.state, session.endReason),
                    row.activeMillis.coerceAtLeast(0) / 60_000,
                    row.totalSteps,
                    stepsStatus(row.hasPendingSteps, row.hasUnfinalSteps),
                ),
            )
        }
    }

    /** Major units with the currency's own decimals, e.g. 12000 EUR → 120.00, 500 JPY → 500. */
    fun price(minor: Long, currencyCode: String): String =
        BigDecimal.valueOf(minor).movePointLeft(Money.fractionDigits(currencyCode).coerceAtLeast(0)).toPlainString()

    fun stepsStatus(hasPending: Boolean, hasUnfinal: Boolean): String = when {
        hasPending -> "counted later"
        hasUnfinal -> "provisional"
        else -> "final"
    }

    private fun status(state: SessionState, reason: EndReason?): String = when (state) {
        SessionState.RUNNING -> "running"
        SessionState.PAUSED -> "paused"
        SessionState.ENDED -> if (reason == EndReason.SWITCH) "ended (switched pairs)" else "ended"
    }

    private fun dateTime(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(DATE_TIME)

    private fun zoneOrUtc(id: String): ZoneId = try {
        ZoneId.of(id)
    } catch (_: DateTimeException) {
        ZoneOffset.UTC
    }
}
