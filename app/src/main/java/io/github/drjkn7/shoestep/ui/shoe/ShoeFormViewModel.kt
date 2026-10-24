package io.github.drjkn7.shoestep.ui.shoe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.FieldError
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeField
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.ShoeValidation
import io.github.drjkn7.shoestep.data.ShoeValidator
import io.github.drjkn7.shoestep.data.model.ShoeType
import java.time.Instant
import kotlinx.coroutines.launch

/** Everything the form shows. Plain values only, so it can be saved across process death. */
data class ShoeFormState(
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val type: ShoeType = ShoeType.ROAD_RUNNING,
    val priceText: String = "",
    val currencyCode: String,
    val purchaseEpochDay: Long? = null,
    val accentColor: Int = 0,
) {
    fun toInput() = ShoeInput(name, brand, model, type, priceText, currencyCode, purchaseEpochDay, accentColor)
}

sealed interface FormStatus {
    data object Loading : FormStatus
    data object Editing : FormStatus
    data object Saving : FormStatus
    data class Saved(val shoeId: String) : FormStatus
    data object NotFound : FormStatus
    data object SaveFailed : FormStatus
}

/**
 * Add/Edit form. [shoeId] is null when adding. Field values are mirrored into [SavedStateHandle]
 * so typing survives rotation and the app being killed in the background.
 */
class ShoeFormViewModel(
    private val repository: ShoeRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    val shoeId: String?,
    defaultCurrencyCode: String,
) : ViewModel() {

    var form by mutableStateOf(restore(PREFIX_FORM) ?: ShoeFormState(currencyCode = defaultCurrencyCode))
        private set

    /** The values the form started with (the saved pair, or a blank form), to tell whether anything changed. */
    private var initial by mutableStateOf(
        restore(PREFIX_INITIAL) ?: ShoeFormState(currencyCode = defaultCurrencyCode).also { persist(PREFIX_INITIAL, it) },
    )

    /** True once the user changed something; Back or Close then asks "Discard changes?". */
    val hasChanges: Boolean get() = form != initial

    /** The pair's name as saved (editing), for "Your changes to … won't be saved." */
    val savedName: String get() = initial.name

    var status by mutableStateOf<FormStatus>(FormStatus.Editing)
        private set

    /** Errors are shown only after the first save attempt, then kept live while typing. */
    var errors by mutableStateOf<Map<ShoeField, FieldError>>(emptyMap())
        private set

    /** True after Save finds problems; the screen shows a popup listing them until OK is tapped. */
    var showProblemsDialog by mutableStateOf(false)
        private set

    private var showErrors = savedState[KEY_SHOW_ERRORS] ?: false

    init {
        if (shoeId != null && savedState.get<Boolean>(KEY_RESTORED) != true) {
            status = FormStatus.Loading
            viewModelScope.launch {
                val shoe = repository.findShoe(shoeId)
                if (shoe == null) {
                    status = FormStatus.NotFound
                } else {
                    val loaded = ShoeFormState(
                        name = shoe.name,
                        brand = shoe.brand,
                        model = shoe.model,
                        type = shoe.type,
                        priceText = shoe.priceMinor?.let { formatPriceForEditing(it, shoe.currencyCode) }.orEmpty(),
                        currencyCode = shoe.currencyCode,
                        purchaseEpochDay = shoe.purchaseEpochDay,
                        accentColor = shoe.accentColor,
                    )
                    rememberAsInitial(loaded)
                    update(loaded)
                    status = FormStatus.Editing
                }
            }
        }
        if (showErrors) revalidate()
    }

    /** True once the currency was set on purpose (editing, restored typing, or picked by the user). */
    private var currencyChosen = shoeId != null || savedState.get<String>(KEY_CURRENCY) != null

    /**
     * The default currency from Settings arrives just after the form opens. It replaces the region's
     * guess unless the user already chose a currency (or this is an existing pair).
     */
    fun applyDefaultCurrency(code: String) {
        if (currencyChosen) return
        currencyChosen = true
        // The blank form now starts with this currency, so it doesn't count as a change.
        rememberAsInitial(initial.copy(currencyCode = code))
        update(form.copy(currencyCode = code))
    }

    private fun rememberAsInitial(state: ShoeFormState) {
        initial = state
        persist(PREFIX_INITIAL, state)
    }

    fun update(newState: ShoeFormState) {
        if (newState.currencyCode != form.currencyCode) currencyChosen = true
        form = newState
        persist(PREFIX_FORM, newState)
        if (showErrors) revalidate()
    }

    fun save() {
        if (status == FormStatus.Saving || status is FormStatus.Saved) return // ignore double taps
        showErrors = true
        savedState[KEY_SHOW_ERRORS] = true
        if (!revalidate()) {
            showProblemsDialog = true
            return
        }

        status = FormStatus.Saving
        viewModelScope.launch {
            val result = runCatching {
                if (shoeId == null) repository.add(form.toInput()) else repository.update(shoeId, form.toInput())
            }.getOrNull()
            status = when (result) {
                is SaveResult.Saved -> FormStatus.Saved(result.shoeId)
                is SaveResult.Invalid -> {
                    errors = result.errors
                    showProblemsDialog = true
                    FormStatus.Editing
                }
                SaveResult.NotFound -> FormStatus.NotFound
                null -> FormStatus.SaveFailed
            }
        }
    }

    fun dismissProblemsDialog() {
        showProblemsDialog = false
    }

    fun dismissSaveError() {
        if (status == FormStatus.SaveFailed) status = FormStatus.Editing
    }

    private fun revalidate(): Boolean {
        val today = Instant.ofEpochMilli(clock.nowUtcMillis()).atZone(clock.zoneId()).toLocalDate()
        val result = ShoeValidator.validate(form.toInput(), today)
        errors = (result as? ShoeValidation.Invalid)?.errors.orEmpty()
        return result is ShoeValidation.Valid
    }

    private fun persist(prefix: String, s: ShoeFormState) {
        savedState["$prefix$RESTORED"] = true
        savedState["$prefix$NAME"] = s.name
        savedState["$prefix$BRAND"] = s.brand
        savedState["$prefix$MODEL"] = s.model
        savedState["$prefix$TYPE"] = s.type.name
        savedState["$prefix$PRICE"] = s.priceText
        savedState["$prefix$CURRENCY"] = s.currencyCode
        savedState["$prefix$DATE"] = s.purchaseEpochDay ?: NO_DATE
        savedState["$prefix$COLOUR"] = s.accentColor
    }

    /** Restores only well-formed values; anything odd falls back to defaults. */
    private fun restore(prefix: String): ShoeFormState? {
        if (savedState.get<Boolean>("$prefix$RESTORED") != true) return null
        val currency = savedState.get<String>("$prefix$CURRENCY") ?: return null
        return ShoeFormState(
            name = savedState["$prefix$NAME"] ?: "",
            brand = savedState["$prefix$BRAND"] ?: "",
            model = savedState["$prefix$MODEL"] ?: "",
            type = savedState.get<String>("$prefix$TYPE")
                ?.let { name -> ShoeType.entries.firstOrNull { it.name == name } } ?: ShoeType.ROAD_RUNNING,
            priceText = savedState["$prefix$PRICE"] ?: "",
            currencyCode = currency,
            purchaseEpochDay = savedState.get<Long>("$prefix$DATE")?.takeIf { it != NO_DATE },
            accentColor = savedState["$prefix$COLOUR"] ?: 0,
        )
    }

    private companion object {
        /** What the user is typing. */
        const val PREFIX_FORM = "form."
        /** What the form started with. */
        const val PREFIX_INITIAL = "initial."
        const val KEY_RESTORED = PREFIX_FORM + "restored"
        const val KEY_SHOW_ERRORS = "form.showErrors"
        const val KEY_CURRENCY = PREFIX_FORM + "currency"
        const val RESTORED = "restored"
        const val NAME = "name"
        const val BRAND = "brand"
        const val MODEL = "model"
        const val TYPE = "type"
        const val PRICE = "price"
        const val CURRENCY = "currency"
        const val DATE = "date"
        const val COLOUR = "colour"
        const val NO_DATE = Long.MIN_VALUE
    }
}

/** "12999" EUR -> "129.99" (plain digits and a dot, which Money.parse accepts in every locale). */
internal fun formatPriceForEditing(priceMinor: Long, currencyCode: String): String =
    java.math.BigDecimal.valueOf(priceMinor, io.github.drjkn7.shoestep.core.Money.fractionDigits(currencyCode)).toPlainString()
