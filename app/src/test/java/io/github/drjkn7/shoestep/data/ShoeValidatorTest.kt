package io.github.drjkn7.shoestep.data

import io.github.drjkn7.shoestep.data.model.ShoeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShoeValidatorTest {

    private val today = LocalDate.of(2026, 10, 17)

    private fun input(
        name: String = "Daily trainers",
        brand: String = "Brand",
        model: String = "Model 3",
        priceText: String = "129.99",
        currencyCode: String = "EUR",
        purchaseEpochDay: Long? = today.minusDays(30).toEpochDay(),
        accentColor: Int = 2,
    ) = ShoeInput(name, brand, model, ShoeType.ROAD_RUNNING, priceText, currencyCode, purchaseEpochDay, accentColor)

    private fun valid(i: ShoeInput): ValidShoe {
        val result = ShoeValidator.validate(i, today)
        assertTrue("Expected valid but was $result", result is ShoeValidation.Valid)
        return (result as ShoeValidation.Valid).shoe
    }

    private fun errors(i: ShoeInput): Map<ShoeField, FieldError> {
        val result = ShoeValidator.validate(i, today)
        assertTrue("Expected invalid but was $result", result is ShoeValidation.Invalid)
        return (result as ShoeValidation.Invalid).errors
    }

    @Test fun validInputIsCleanedAndConverted() {
        val shoe = valid(input(name = "  Daily   trainers  ", priceText = "129,99"))
        assertEquals("Daily trainers", shoe.name)
        assertEquals(12_999L, shoe.priceMinor)
        assertEquals("EUR", shoe.currencyCode)
    }

    @Test fun emptyNameIsRequired() = assertEquals(FieldError.REQUIRED, errors(input(name = "   "))[ShoeField.NAME])

    @Test fun nameOfOnlyControlCharactersIsRequired() =
        assertEquals(FieldError.REQUIRED, errors(input(name = "\u0000\u0007"))[ShoeField.NAME])

    @Test fun twoHundredCharacterNameIsTooLong() =
        assertEquals(FieldError.TOO_LONG, errors(input(name = "a".repeat(200)))[ShoeField.NAME])

    @Test fun sixtyCharacterNameIsAccepted() = assertEquals(60, valid(input(name = "a".repeat(60))).name.length)

    @Test fun emojiCountAsOneCharacterEach() {
        val name = "👟".repeat(60) // 60 shoe emoji, 120 UTF-16 units
        assertEquals(name, valid(input(name = name)).name)
    }

    @Test fun brandAndModelLimits() {
        val e = errors(input(brand = "b".repeat(41), model = "m".repeat(61)))
        assertEquals(FieldError.TOO_LONG, e[ShoeField.BRAND])
        assertEquals(FieldError.TOO_LONG, e[ShoeField.MODEL])
    }

    @Test fun brandAndModelMayBeEmpty() {
        val shoe = valid(input(brand = "", model = " "))
        assertEquals("", shoe.brand)
        assertEquals("", shoe.model)
    }

    @Test fun newlinesAndTabsBecomeSpaces() = assertEquals("Left Right", valid(input(name = "Left\n\tRight")).name)

    @Test fun controlCharactersAreRemoved() = assertEquals("AB", valid(input(name = "A\u0000\u001B\u007FB")).name)

    @Test fun bidiOverridesAreRemoved() = assertEquals("evilgpj.exe", valid(input(name = "evil‮gpj.exe")).name)

    @Test fun negativePriceRejected() = assertEquals(FieldError.NEGATIVE, errors(input(priceText = "-10"))[ShoeField.PRICE])

    @Test fun nonNumericPriceRejected() = assertEquals(FieldError.INVALID, errors(input(priceText = "free"))[ShoeField.PRICE])

    @Test fun hugePriceRejected() = assertEquals(FieldError.TOO_LARGE, errors(input(priceText = "5000000"))[ShoeField.PRICE])

    @Test fun emptyPriceMeansNoPrice() = assertNull(valid(input(priceText = "  ")).priceMinor)

    @Test fun zeroPriceIsValid() = assertEquals(0L, valid(input(priceText = "0")).priceMinor)

    @Test fun invalidCurrencyRejected() = assertEquals(FieldError.INVALID, errors(input(currencyCode = "EURO"))[ShoeField.CURRENCY])

    @Test fun futurePurchaseDateRejected() =
        assertEquals(FieldError.IN_FUTURE, errors(input(purchaseEpochDay = today.plusDays(1).toEpochDay()))[ShoeField.PURCHASE_DATE])

    @Test fun todayIsAValidPurchaseDate() = assertEquals(today.toEpochDay(), valid(input(purchaseEpochDay = today.toEpochDay())).purchaseEpochDay)

    @Test fun ancientPurchaseDateRejected() =
        assertEquals(FieldError.TOO_OLD, errors(input(purchaseEpochDay = LocalDate.of(1899, 12, 31).toEpochDay()))[ShoeField.PURCHASE_DATE])

    @Test fun purchaseDateIsOptional() = assertNull(valid(input(purchaseEpochDay = null)).purchaseEpochDay)

    @Test fun accentColourOutOfPaletteRejected() {
        assertEquals(FieldError.INVALID, errors(input(accentColor = 8))[ShoeField.ACCENT_COLOR])
        assertEquals(FieldError.INVALID, errors(input(accentColor = -1))[ShoeField.ACCENT_COLOR])
    }

    @Test fun reportsAllErrorsAtOnce() {
        val e = errors(input(name = "", priceText = "-1", accentColor = 99))
        assertEquals(setOf(ShoeField.NAME, ShoeField.PRICE, ShoeField.ACCENT_COLOR), e.keys)
    }
}
