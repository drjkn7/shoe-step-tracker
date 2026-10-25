package io.github.drjkn7.shoestep.core

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * An amount of money in minor units (for example cents) plus an ISO 4217 currency code.
 * Never stored or calculated as a floating-point number.
 */
data class Money(val minorUnits: Long, val currencyCode: String) {

    init {
        require(minorUnits >= 0) { "Money cannot be negative" }
        require(isSupportedCurrency(currencyCode)) { "Unsupported currency" }
    }

    /** Formats for display in the given locale, e.g. "€12.50" or "12,50 €". */
    fun format(locale: Locale): String {
        val digits = fractionDigits(currencyCode)
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return formatter.format(BigDecimal.valueOf(minorUnits, digits))
    }

    companion object {
        /** Highest accepted price in major units (whole euros, dollars, ...). */
        const val MAX_MAJOR_UNITS: Long = 1_000_000

        /** Longest price text the form accepts (also the field's typing limit). */
        const val MAX_INPUT_LENGTH = 20
        private const val FALLBACK_CURRENCY = "EUR"
        private val CODE_PATTERN = Regex("^[A-Z]{3}$")
        private val NUMBER_PATTERN = Regex("^([0-9]+)(?:[.,]([0-9]+))?$")

        /** True for a known ISO 4217 code that has a defined number of decimal places. */
        fun isSupportedCurrency(code: String): Boolean {
            if (!CODE_PATTERN.matches(code)) return false
            val currency = runCatching { Currency.getInstance(code) }.getOrNull() ?: return false
            return currency.defaultFractionDigits >= 0
        }

        /** Number of decimal places for the currency, e.g. 2 for EUR, 0 for JPY. */
        fun fractionDigits(code: String): Int = Currency.getInstance(code).defaultFractionDigits

        /** The currency of the locale's country, or EUR when the locale has none. */
        fun defaultCurrencyCode(locale: Locale): String {
            val code = runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull()
            return if (code != null && isSupportedCurrency(code)) code else FALLBACK_CURRENCY
        }

        /**
         * Parses user input such as "129.99" or "129,99". Accepts digits with one optional
         * decimal point or comma; no signs, spaces inside the number, or grouping separators.
         */
        fun parse(input: String, currencyCode: String): MoneyParseResult {
            if (!isSupportedCurrency(currencyCode)) return MoneyParseResult.Invalid(MoneyError.UNSUPPORTED_CURRENCY)
            val text = input.trim()
            if (text.isEmpty()) return MoneyParseResult.Invalid(MoneyError.EMPTY)
            if (text.length > MAX_INPUT_LENGTH) return MoneyParseResult.Invalid(MoneyError.TOO_LARGE)
            if (text.startsWith("-")) return MoneyParseResult.Invalid(MoneyError.NEGATIVE)

            val match = NUMBER_PATTERN.matchEntire(text)
                ?: return MoneyParseResult.Invalid(MoneyError.NOT_A_NUMBER)
            val wholePart = match.groupValues[1].trimStart('0').ifEmpty { "0" }
            val fractionPart = match.groupValues[2].trimEnd('0')

            val digits = fractionDigits(currencyCode)
            if (fractionPart.length > digits) return MoneyParseResult.Invalid(MoneyError.TOO_MANY_DECIMALS)
            if (wholePart.length > MAX_MAJOR_UNITS.toString().length) {
                return MoneyParseResult.Invalid(MoneyError.TOO_LARGE)
            }
            val whole = wholePart.toLong()
            val fraction = fractionPart.padEnd(digits, '0').ifEmpty { "0" }.toLong()
            val minor = whole * pow10(digits) + fraction
            if (minor > MAX_MAJOR_UNITS * pow10(digits)) return MoneyParseResult.Invalid(MoneyError.TOO_LARGE)

            return MoneyParseResult.Valid(Money(minor, currencyCode))
        }

        private fun pow10(n: Int): Long {
            var result = 1L
            repeat(n) { result *= 10 }
            return result
        }
    }
}

sealed interface MoneyParseResult {
    data class Valid(val money: Money) : MoneyParseResult
    data class Invalid(val error: MoneyError) : MoneyParseResult
}

enum class MoneyError { EMPTY, NOT_A_NUMBER, NEGATIVE, TOO_MANY_DECIMALS, TOO_LARGE, UNSUPPORTED_CURRENCY }
