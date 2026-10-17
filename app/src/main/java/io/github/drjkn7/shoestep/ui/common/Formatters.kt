package io.github.drjkn7.shoestep.ui.common

import io.github.drjkn7.shoestep.core.CostCalculator
import io.github.drjkn7.shoestep.core.Money
import java.text.NumberFormat
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Locale-aware formatting for display. Pure functions so they are unit-tested on the JVM. */
object Formatters {

    fun count(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun date(epochDay: Long, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    /** Date and time of [epochMillis] in the zone it was recorded in (falls back to UTC if unreadable). */
    fun dateTime(epochMillis: Long, zoneId: String, locale: Locale): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneOrUtc(zoneId))
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))

    /** Time of day of [epochMillis] in the zone it was recorded in, e.g. "14:05". */
    fun time(epochMillis: Long, zoneId: String, locale: Locale): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneOrUtc(zoneId))
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    /** Whole hours and remaining minutes of a duration; negative durations count as zero. */
    fun hoursAndMinutes(millis: Long): Pair<Long, Long> {
        val totalMinutes = millis.coerceAtLeast(0) / 60_000
        return totalMinutes / 60 to totalMinutes % 60
    }

    private fun zoneOrUtc(zoneId: String): ZoneId =
        try {
            ZoneId.of(zoneId)
        } catch (_: DateTimeException) {
            ZoneOffset.UTC
        }

    fun price(priceMinor: Long, currencyCode: String, locale: Locale): String =
        Money(priceMinor, currencyCode).format(locale)

    /**
     * Cost per 1,000 steps, e.g. "€0.40" or "€0.0333". Shows up to two extra decimals so small
     * costs don't round to zero. Returns null when not available (caller shows a dash).
     */
    fun costPerThousand(cost: CostCalculator.Cost, currencyCode: String, locale: Locale): String? {
        if (cost !is CostCalculator.Cost.Amount) return null
        val digits = Money.fractionDigits(currencyCode)
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode)
            minimumFractionDigits = digits
            maximumFractionDigits = digits + 2
        }
        return formatter.format(cost.perThousandSteps)
    }
}
