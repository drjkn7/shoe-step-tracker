package io.github.drjkn7.shoestep.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cost per step = price / total steps. Exact decimal maths, never floating point.
 * Results are in major units (e.g. euros) of the shoe's currency.
 */
object CostCalculator {

    sealed interface Cost {
        /** Price missing or no steps yet: the UI shows a dash. */
        data object NotAvailable : Cost

        data class Amount(val perStep: BigDecimal, val perThousandSteps: BigDecimal) : Cost
    }

    /** Extra decimal places beyond the currency's own, so small costs don't round to zero. */
    private const val EXTRA_DIGITS_PER_THOUSAND = 2
    private const val EXTRA_DIGITS_PER_STEP = 5

    fun cost(priceMinor: Long?, currencyCode: String, totalSteps: Long): Cost {
        if (priceMinor == null || priceMinor < 0 || totalSteps <= 0) return Cost.NotAvailable
        if (!Money.isSupportedCurrency(currencyCode)) return Cost.NotAvailable

        val digits = Money.fractionDigits(currencyCode)
        val price = BigDecimal.valueOf(priceMinor, digits)
        val steps = BigDecimal.valueOf(totalSteps)
        return Cost.Amount(
            perStep = price.divide(steps, digits + EXTRA_DIGITS_PER_STEP, RoundingMode.HALF_UP),
            perThousandSteps = price.multiply(BigDecimal.valueOf(1_000))
                .divide(steps, digits + EXTRA_DIGITS_PER_THOUSAND, RoundingMode.HALF_UP),
        )
    }
}
