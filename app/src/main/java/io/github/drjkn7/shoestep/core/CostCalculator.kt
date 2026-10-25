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
    private const val EXTRA_COST_DIGITS = 2
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
                .divide(steps, digits + EXTRA_COST_DIGITS, RoundingMode.HALF_UP),
        )
    }

    /**
     * Cost per km or per mile: price / distance in that unit ([unitMeters] metres per unit), in major
     * units with two extra decimals. Null when the price is missing or no distance is known yet (the UI
     * shows a dash); free shoes cost 0.
     */
    fun costPerDistance(priceMinor: Long?, currencyCode: String, meters: Double?, unitMeters: Double): BigDecimal? {
        if (priceMinor == null || priceMinor < 0 || meters == null || !(meters > 0) || meters.isInfinite()) return null
        if (!Money.isSupportedCurrency(currencyCode) || !(unitMeters > 0)) return null

        val digits = Money.fractionDigits(currencyCode)
        val price = BigDecimal.valueOf(priceMinor, digits)
        val units = BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(unitMeters), DISTANCE_SCALE, RoundingMode.HALF_UP)
        if (units.signum() == 0) return null
        return price.divide(units, digits + EXTRA_COST_DIGITS, RoundingMode.HALF_UP)
    }

    /** Decimal places kept for the distance before dividing (micro-units: far below what is shown). */
    private const val DISTANCE_SCALE = 6
}
