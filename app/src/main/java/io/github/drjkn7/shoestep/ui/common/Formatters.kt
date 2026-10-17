package io.github.drjkn7.shoestep.ui.common

import io.github.drjkn7.shoestep.core.CostCalculator
import io.github.drjkn7.shoestep.core.Money
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Locale-aware formatting for display. Pure functions so they are unit-tested on the JVM. */
object Formatters {

    fun count(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun date(epochDay: Long, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

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
