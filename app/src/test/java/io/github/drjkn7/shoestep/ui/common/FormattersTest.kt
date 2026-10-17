package io.github.drjkn7.shoestep.ui.common

import io.github.drjkn7.shoestep.core.CostCalculator
import io.github.drjkn7.shoestep.ui.shoe.formatPriceForEditing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FormattersTest {

    @Test fun countUsesLocaleGrouping() {
        assertEquals("1,234,567", Formatters.count(1_234_567, Locale.US))
        assertEquals("1.234.567", Formatters.count(1_234_567, Locale.GERMANY))
    }

    @Test fun dateUsesLocaleFormat() {
        val day = LocalDate.of(2026, 3, 5).toEpochDay()
        assertEquals("Mar 5, 2026", Formatters.date(day, Locale.US))
    }

    @Test fun timesAreShownInTheZoneTheSessionWasRecordedIn() {
        val instant = java.time.Instant.parse("2026-03-05T13:05:00Z").toEpochMilli()
        assertEquals("13:05", Formatters.time(instant, "UTC", Locale.UK))
        assertEquals("14:05", Formatters.time(instant, "Europe/Berlin", Locale.UK))
        assertTrue(Formatters.dateTime(instant, "Europe/Berlin", Locale.UK).contains("14:05"))
    }

    @Test fun unreadableZoneFallsBackToUtc() {
        val instant = java.time.Instant.parse("2026-03-05T13:05:00Z").toEpochMilli()
        assertEquals("13:05", Formatters.time(instant, "Not/AZone", Locale.UK))
    }

    @Test fun hoursAndMinutesRoundDownAndNeverGoNegative() {
        assertEquals(0L to 0L, Formatters.hoursAndMinutes(59_999))
        assertEquals(1L to 5L, Formatters.hoursAndMinutes(65 * 60_000L + 30_000))
        assertEquals(0L to 0L, Formatters.hoursAndMinutes(-3_600_000))
    }

    @Test fun priceShowsCurrency() = assertEquals("$129.99", Formatters.price(12_999, "USD", Locale.US))

    @Test fun costPerThousandKeepsCurrencyDecimalsAtLeast() {
        val cost = CostCalculator.cost(12_000, "USD", 300_000) // $0.40 per 1,000 steps
        assertEquals("$0.40", Formatters.costPerThousand(cost, "USD", Locale.US))
    }

    @Test fun costPerThousandShowsExtraDecimalsForSmallCosts() {
        val cost = CostCalculator.cost(1_000, "USD", 300_000) // $0.0333...
        assertEquals("$0.0333", Formatters.costPerThousand(cost, "USD", Locale.US))
    }

    @Test fun costPerThousandForFreeShoeIsZero() {
        val cost = CostCalculator.cost(0, "USD", 1_000)
        assertEquals("$0.00", Formatters.costPerThousand(cost, "USD", Locale.US))
    }

    @Test fun costNotAvailableGivesNull() =
        assertNull(Formatters.costPerThousand(CostCalculator.Cost.NotAvailable, "USD", Locale.US))

    @Test fun priceForEditingUsesPlainDigits() {
        assertEquals("129.99", formatPriceForEditing(12_999, "EUR"))
        assertEquals("15000", formatPriceForEditing(15_000, "JPY"))
        assertEquals("0.00", formatPriceForEditing(0, "EUR"))
    }

    @Test fun paletteHasEightNamedColoursAndSafeFallback() {
        assertEquals(8, ShoePalette.size)
        assertEquals(ShoePalette[0], accentColour(99))
    }
}
