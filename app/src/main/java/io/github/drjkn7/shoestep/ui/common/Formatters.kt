package io.github.drjkn7.shoestep.ui.common

import io.github.drjkn7.shoestep.core.CostCalculator
import io.github.drjkn7.shoestep.core.MILLIS_PER_MINUTE
import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.core.zoneIdOrUtc
import io.github.drjkn7.shoestep.settings.DistanceUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
        Instant.ofEpochMilli(epochMillis).atZone(zoneIdOrUtc(zoneId))
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))

    /** Time of day of [epochMillis] in the zone it was recorded in, e.g. "14:05". */
    fun time(epochMillis: Long, zoneId: String, locale: Locale): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneIdOrUtc(zoneId))
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    /** The calendar day [epochMillis] falls on in [zone] (the phone's zone by default), for [date]. */
    fun localEpochDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

    /** Whole hours and remaining minutes of a duration; negative durations count as zero. */
    fun hoursAndMinutes(millis: Long): Pair<Long, Long> {
        val totalMinutes = millis.coerceAtLeast(0) / MILLIS_PER_MINUTE
        return totalMinutes / 60 to totalMinutes % 60
    }


    fun price(priceMinor: Long, currencyCode: String, locale: Locale): String =
        Money(priceMinor, currencyCode).format(locale)

    /**
     * Cost per 1,000 steps, e.g. "€0.40", "€190.55" or "€0.0333". Returns null when not available
     * (caller shows a dash).
     */
    fun costPerThousand(cost: CostCalculator.Cost, currencyCode: String, locale: Locale): String? {
        if (cost !is CostCalculator.Cost.Amount) return null
        return smallCost(cost.perThousandSteps, currencyCode, locale)
    }

    /** Cost per km or mile (see [CostCalculator.costPerDistance]); null when not available (a dash). */
    fun costPerDistance(cost: BigDecimal?, currencyCode: String, locale: Locale): String? {
        if (cost == null) return null
        return smallCost(cost, currencyCode, locale)
    }

    /**
     * The currency's own decimals; below one whole unit (e.g. under £1) up to two more, so small
     * costs don't round to zero ("£0.0333") while larger ones stay plain ("£190.55").
     */
    private fun smallCost(amount: BigDecimal, currencyCode: String, locale: Locale): String {
        val digits = Money.fractionDigits(currencyCode).coerceAtLeast(0)
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode)
            minimumFractionDigits = digits
            maximumFractionDigits = if (amount.abs() < BigDecimal.ONE) digits + 2 else digits
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(amount)
    }

    /**
     * A distance number in [unit] with one decimal and the locale's separators, e.g. "2.4" or "1,234.5"
     * (the caller adds "km" or "mi"). Negative or unreadable values count as zero.
     */
    fun distance(meters: Double, unit: DistanceUnit, locale: Locale): String {
        val value = if (meters.isFinite() && meters > 0) unit.fromMeters(meters) else 0.0
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(BigDecimal.valueOf(value))
    }
}
