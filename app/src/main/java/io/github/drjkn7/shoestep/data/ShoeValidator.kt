package io.github.drjkn7.shoestep.data

import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.core.MoneyError
import io.github.drjkn7.shoestep.core.MoneyParseResult
import io.github.drjkn7.shoestep.data.model.ShoeType
import java.time.LocalDate

/** Raw values from the Add/Edit form, exactly as the user typed them. */
data class ShoeInput(
    val name: String,
    val brand: String,
    val model: String,
    val type: ShoeType,
    /** Optional; empty means "no price". */
    val priceText: String,
    val currencyCode: String,
    val purchaseEpochDay: Long?,
    val accentColor: Int,
)

/** Cleaned, range-checked values that are safe to store. */
data class ValidShoe(
    val name: String,
    val brand: String,
    val model: String,
    val type: ShoeType,
    val priceMinor: Long?,
    val currencyCode: String,
    val purchaseEpochDay: Long?,
    val accentColor: Int,
)

enum class ShoeField { NAME, BRAND, MODEL, PRICE, CURRENCY, PURCHASE_DATE, ACCENT_COLOR }

enum class FieldError { REQUIRED, TOO_LONG, INVALID, NEGATIVE, TOO_MANY_DECIMALS, TOO_LARGE, IN_FUTURE, TOO_OLD }

sealed interface ShoeValidation {
    data class Valid(val shoe: ValidShoe) : ShoeValidation
    data class Invalid(val errors: Map<ShoeField, FieldError>) : ShoeValidation
}

/** Validates and cleans shoe input (SEC-07). Pure Kotlin so it is unit-tested on the JVM. */
object ShoeValidator {
    const val NAME_MAX = 60
    const val BRAND_MAX = 40
    const val MODEL_MAX = 60
    const val PALETTE_SIZE = 8
    val EARLIEST_PURCHASE_DATE: LocalDate = LocalDate.of(1900, 1, 1)

    fun validate(input: ShoeInput, today: LocalDate): ShoeValidation {
        val errors = mutableMapOf<ShoeField, FieldError>()

        val name = cleanText(input.name)
        when {
            name.isEmpty() -> errors[ShoeField.NAME] = FieldError.REQUIRED
            name.codePointLength() > NAME_MAX -> errors[ShoeField.NAME] = FieldError.TOO_LONG
        }
        val brand = cleanText(input.brand)
        if (brand.codePointLength() > BRAND_MAX) errors[ShoeField.BRAND] = FieldError.TOO_LONG
        val model = cleanText(input.model)
        if (model.codePointLength() > MODEL_MAX) errors[ShoeField.MODEL] = FieldError.TOO_LONG

        val currency = input.currencyCode.trim()
        if (!Money.isSupportedCurrency(currency)) errors[ShoeField.CURRENCY] = FieldError.INVALID

        var priceMinor: Long? = null
        if (input.priceText.isNotBlank() && ShoeField.CURRENCY !in errors) {
            when (val parsed = Money.parse(input.priceText, currency)) {
                is MoneyParseResult.Valid -> priceMinor = parsed.money.minorUnits
                is MoneyParseResult.Invalid -> errors[ShoeField.PRICE] = parsed.error.toFieldError()
            }
        }

        input.purchaseEpochDay?.let { day ->
            when {
                day > today.toEpochDay() -> errors[ShoeField.PURCHASE_DATE] = FieldError.IN_FUTURE
                day < EARLIEST_PURCHASE_DATE.toEpochDay() -> errors[ShoeField.PURCHASE_DATE] = FieldError.TOO_OLD
            }
        }

        if (input.accentColor !in 0 until PALETTE_SIZE) errors[ShoeField.ACCENT_COLOR] = FieldError.INVALID

        if (errors.isNotEmpty()) return ShoeValidation.Invalid(errors)
        return ShoeValidation.Valid(
            ValidShoe(
                name = name,
                brand = brand,
                model = model,
                type = input.type,
                priceMinor = priceMinor,
                currencyCode = currency,
                purchaseEpochDay = input.purchaseEpochDay,
                accentColor = input.accentColor,
            ),
        )
    }

    /**
     * Removes control characters (newlines, tabs, NUL) and invisible bidirectional overrides that
     * could disguise text, collapses whitespace runs, and trims.
     */
    fun cleanText(raw: String): String {
        val builder = StringBuilder(raw.length)
        var lastWasSpace = false
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> {
                    if (!lastWasSpace) builder.append(' ')
                    lastWasSpace = true
                }
                Character.getType(cp) == Character.CONTROL.toInt() || cp in BIDI_CONTROLS -> Unit
                else -> {
                    builder.appendCodePoint(cp)
                    lastWasSpace = false
                }
            }
        }
        return builder.toString().trim()
    }

    private val BIDI_CONTROLS = setOf(0x061C, 0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)

    private fun String.codePointLength(): Int = codePointCount(0, length)

    private fun MoneyError.toFieldError(): FieldError = when (this) {
        MoneyError.EMPTY -> FieldError.REQUIRED
        MoneyError.NOT_A_NUMBER, MoneyError.UNSUPPORTED_CURRENCY -> FieldError.INVALID
        MoneyError.NEGATIVE -> FieldError.NEGATIVE
        MoneyError.TOO_MANY_DECIMALS -> FieldError.TOO_MANY_DECIMALS
        MoneyError.TOO_LARGE -> FieldError.TOO_LARGE
    }
}
