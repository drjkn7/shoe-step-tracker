package io.github.drjkn7.shoestep.core

import io.github.drjkn7.shoestep.core.CostCalculator.Cost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CostCalculatorTest {

    private fun amount(priceMinor: Long?, steps: Long, currency: String = "EUR"): Cost.Amount {
        val cost = CostCalculator.cost(priceMinor, currency, steps)
        assertTrue("Expected an amount but was $cost", cost is Cost.Amount)
        return cost as Cost.Amount
    }

    @Test fun missingPriceIsNotAvailable() =
        assertEquals(Cost.NotAvailable, CostCalculator.cost(null, "EUR", 10_000))

    @Test fun zeroStepsIsNotAvailable() =
        assertEquals(Cost.NotAvailable, CostCalculator.cost(12_000, "EUR", 0))

    @Test fun zeroStepsWithFreeShoeIsStillNotAvailable() =
        assertEquals(Cost.NotAvailable, CostCalculator.cost(0, "EUR", 0))

    @Test fun negativeStepsIsNotAvailable() =
        assertEquals(Cost.NotAvailable, CostCalculator.cost(12_000, "EUR", -5))

    @Test fun negativePriceIsNotAvailable() =
        assertEquals(Cost.NotAvailable, CostCalculator.cost(-1, "EUR", 1_000))

    @Test fun unknownCurrencyIsNotAvailable() =
        assertEquals(Cost.NotAvailable, CostCalculator.cost(12_000, "XYZ", 1_000))

    @Test fun freeShoeCostsZero() {
        val cost = amount(0, 50_000)
        assertEquals(0, cost.perStep.signum())
        assertEquals(0, cost.perThousandSteps.signum())
    }

    @Test fun handCalculatedExample() {
        // €120.00 over 300,000 steps = €0.40 per 1,000 steps, €0.0004 per step.
        val cost = amount(12_000, 300_000)
        assertEquals(BigDecimal("0.4000"), cost.perThousandSteps)
        assertEquals(BigDecimal("0.0004000"), cost.perStep)
    }

    @Test fun roundsHalfUp() {
        // €100.00 / 3,000 steps per 1,000 = 33.3333... -> 33.3333
        assertEquals(BigDecimal("33.3333"), amount(10_000, 3_000).perThousandSteps)
        // €2.00 / 3 steps = 0.6666666... -> 0.6666667
        assertEquals(BigDecimal("0.6666667"), amount(200, 3).perStep)
    }

    @Test fun zeroDecimalCurrency() {
        // ¥15,000 over 1,000,000 steps = ¥15 per 1,000 steps.
        assertEquals(BigDecimal("15.00"), amount(15_000, 1_000_000, "JPY").perThousandSteps)
    }

    @Test fun largestPriceAndHugeStepsDoNotOverflow() {
        val cost = amount(Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(BigDecimal("10.0000"), cost.perThousandSteps)
    }

    @Test fun singleStep() {
        assertEquals(BigDecimal("129990.0000"), amount(12_999, 1).perThousandSteps)
    }
}
