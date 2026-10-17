package io.github.drjkn7.shoestep.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyTest {

    private fun valid(input: String, code: String = "EUR"): Money {
        val result = Money.parse(input, code)
        assertTrue("Expected valid for '$input' but was $result", result is MoneyParseResult.Valid)
        return (result as MoneyParseResult.Valid).money
    }

    private fun error(input: String, code: String = "EUR"): MoneyError {
        val result = Money.parse(input, code)
        assertTrue("Expected invalid for '$input' but was $result", result is MoneyParseResult.Invalid)
        return (result as MoneyParseResult.Invalid).error
    }

    @Test fun parsesWholeNumber() = assertEquals(Money(12_900, "EUR"), valid("129"))

    @Test fun parsesDotDecimal() = assertEquals(Money(12_999, "EUR"), valid("129.99"))

    @Test fun parsesCommaDecimal() = assertEquals(Money(12_999, "EUR"), valid("129,99"))

    @Test fun parsesSingleDecimalDigit() = assertEquals(Money(12_950, "EUR"), valid("129.5"))

    @Test fun trimsWhitespace() = assertEquals(Money(500, "EUR"), valid("  5  "))

    @Test fun zeroIsValid() = assertEquals(Money(0, "EUR"), valid("0"))

    @Test fun leadingZerosIgnored() = assertEquals(Money(700, "EUR"), valid("0007"))

    @Test fun trailingZeroDecimalsAllowedForZeroDecimalCurrency() =
        assertEquals(Money(15_000, "JPY"), valid("15000.00", "JPY"))

    @Test fun threeDecimalCurrency() = assertEquals(Money(1_234, "KWD"), valid("1.234", "KWD"))

    @Test fun maximumAccepted() = assertEquals(Money(100_000_000, "EUR"), valid("1000000"))

    @Test fun aboveMaximumRejected() = assertEquals(MoneyError.TOO_LARGE, error("1000000.01"))

    @Test fun hugeNumberRejectedWithoutOverflow() = assertEquals(MoneyError.TOO_LARGE, error("99999999999999999999"))

    @Test fun overlongInputRejected() = assertEquals(MoneyError.TOO_LARGE, error("1".repeat(200)))

    @Test fun emptyRejected() = assertEquals(MoneyError.EMPTY, error("   "))

    @Test fun negativeRejected() = assertEquals(MoneyError.NEGATIVE, error("-5"))

    @Test fun tooManyDecimalsRejected() = assertEquals(MoneyError.TOO_MANY_DECIMALS, error("1.999"))

    @Test fun decimalsRejectedForZeroDecimalCurrency() = assertEquals(MoneyError.TOO_MANY_DECIMALS, error("100.5", "JPY"))

    @Test fun lettersRejected() = assertEquals(MoneyError.NOT_A_NUMBER, error("12a"))

    @Test fun groupingSeparatorsRejected() = assertEquals(MoneyError.NOT_A_NUMBER, error("1,000.00"))

    @Test fun twoSeparatorsRejected() = assertEquals(MoneyError.NOT_A_NUMBER, error("1.2.3"))

    @Test fun barePointRejected() = assertEquals(MoneyError.NOT_A_NUMBER, error("."))

    @Test fun scientificNotationRejected() = assertEquals(MoneyError.NOT_A_NUMBER, error("1e5"))

    @Test fun nonAsciiDigitsRejected() = assertEquals(MoneyError.NOT_A_NUMBER, error("١٢٣"))

    @Test fun unknownCurrencyRejected() = assertEquals(MoneyError.UNSUPPORTED_CURRENCY, error("10", "XYZ"))

    @Test fun lowercaseCurrencyRejected() = assertEquals(MoneyError.UNSUPPORTED_CURRENCY, error("10", "eur"))

    @Test fun currencyWithoutDecimalsDefinitionRejected() = assertFalse(Money.isSupportedCurrency("XAU"))

    @Test(expected = IllegalArgumentException::class)
    fun constructorRejectsNegative() {
        Money(-1, "EUR")
    }

    @Test fun formatsWithCurrencyAndDecimals() {
        val text = Money(12_950, "USD").format(Locale.US)
        assertEquals("$129.50", text)
    }

    @Test fun formatsZeroDecimalCurrency() {
        val text = Money(15_000, "JPY").format(Locale.US)
        assertTrue(text, text.contains("15,000") && !text.contains("."))
    }

    @Test fun defaultCurrencyFromLocale() {
        assertEquals("GBP", Money.defaultCurrencyCode(Locale.UK))
        assertEquals("JPY", Money.defaultCurrencyCode(Locale.JAPAN))
    }

    @Test fun defaultCurrencyFallsBackWithoutCountry() =
        assertEquals("EUR", Money.defaultCurrencyCode(Locale.ENGLISH))
}
