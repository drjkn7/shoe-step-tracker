package io.github.drjkn7.shoestep.ui.shoe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ui.common.CardLabel
import io.github.drjkn7.shoestep.ui.common.DialogAction
import io.github.drjkn7.shoestep.ui.common.DialogText
import io.github.drjkn7.shoestep.ui.common.InfoDialog
import io.github.drjkn7.shoestep.ui.common.PickerField
import io.github.drjkn7.shoestep.ui.common.PrimaryButton
import io.github.drjkn7.shoestep.ui.common.SilverChip
import io.github.drjkn7.shoestep.ui.common.SilverDialog
import io.github.drjkn7.shoestep.ui.common.SilverTextField
import io.github.drjkn7.shoestep.ui.theme.SilverShapes
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.data.FieldError
import io.github.drjkn7.shoestep.data.ShoeField
import io.github.drjkn7.shoestep.data.ShoeValidator
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.ui.common.Formatters
import io.github.drjkn7.shoestep.ui.common.ShoePalette
import io.github.drjkn7.shoestep.ui.common.labelRes
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoeFormScreen(
    viewModel: ShoeFormViewModel,
    onBack: () -> Unit,
    onSaved: (shoeId: String) -> Unit,
) {
    val status = viewModel.status
    LaunchedEffect(status) { if (status is FormStatus.Saved) onSaved(status.shoeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.shoeId == null) R.string.title_add_shoe else R.string.title_edit_shoe)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        when (status) {
            FormStatus.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            FormStatus.NotFound -> Text(
                stringResource(R.string.form_not_found),
                modifier = Modifier.padding(padding).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> FormContent(
                form = viewModel.form,
                errors = viewModel.errors,
                saving = status == FormStatus.Saving,
                onChange = viewModel::update,
                onSave = viewModel::save,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (viewModel.showProblemsDialog && viewModel.errors.isNotEmpty()) {
        ProblemsDialog(
            errors = viewModel.errors,
            currencyCode = viewModel.form.currencyCode,
            onOk = viewModel::dismissProblemsDialog,
        )
    }

    if (status == FormStatus.SaveFailed) {
        InfoDialog(title = null, text = stringResource(R.string.error_save_failed), onDismiss = viewModel::dismissSaveError)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormContent(
    form: ShoeFormState,
    errors: Map<ShoeField, FieldError>,
    saving: Boolean,
    onChange: (ShoeFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextInput(
            value = form.name,
            onValueChange = { onChange(form.copy(name = it)) },
            label = stringResource(R.string.field_name),
            placeholder = stringResource(R.string.field_name_hint),
            max = ShoeValidator.NAME_MAX,
            error = errors[ShoeField.NAME]?.let { errorText(ShoeField.NAME, it, form.currencyCode) },
        )
        TextInput(
            value = form.brand,
            onValueChange = { onChange(form.copy(brand = it)) },
            label = stringResource(R.string.field_brand),
            max = ShoeValidator.BRAND_MAX,
            error = errors[ShoeField.BRAND]?.let { errorText(ShoeField.BRAND, it, form.currencyCode) },
        )
        TextInput(
            value = form.model,
            onValueChange = { onChange(form.copy(model = it)) },
            label = stringResource(R.string.field_model),
            max = ShoeValidator.MODEL_MAX,
            error = errors[ShoeField.MODEL]?.let { errorText(ShoeField.MODEL, it, form.currencyCode) },
        )

        CardLabel(stringResource(R.string.field_type), heading = false)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s), verticalArrangement = Arrangement.spacedBy(SilverSpacing.s)) {
            ShoeType.entries.forEach { type ->
                SilverChip(
                    text = stringResource(type.labelRes()),
                    selected = form.type == type,
                    onClick = { onChange(form.copy(type = type)) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m), verticalAlignment = Alignment.Top) {
            SilverTextField(
                value = form.priceText,
                onValueChange = { if (it.length <= 20) onChange(form.copy(priceText = it)) },
                label = stringResource(R.string.field_price),
                error = errors[ShoeField.PRICE]?.let { errorText(ShoeField.PRICE, it, form.currencyCode) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1.4f),
            )
            val currencyDescription = stringResource(
                R.string.currency_button_description,
                stringResource(R.string.field_currency),
                form.currencyCode,
                currencyName(form.currencyCode, locale),
            )
            Column(Modifier.weight(1f)) {
                PickerField(
                    label = stringResource(R.string.field_currency),
                    value = form.currencyCode,
                    icon = painterResource(R.drawable.ic_chevron_right),
                    onClick = { showCurrencyPicker = true },
                    modifier = Modifier.semantics { contentDescription = currencyDescription },
                )
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(SilverSpacing.s)) {
            Column(Modifier.weight(1f)) {
                PickerField(
                    label = stringResource(R.string.field_purchase_date),
                    value = form.purchaseEpochDay?.let { Formatters.date(it, locale) } ?: stringResource(R.string.field_purchase_date_none),
                    icon = painterResource(R.drawable.ic_calendar_month),
                    onClick = { showDatePicker = true },
                    placeholder = form.purchaseEpochDay == null,
                )
            }
            if (form.purchaseEpochDay != null) {
                IconButton(onClick = { onChange(form.copy(purchaseEpochDay = null)) }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.field_purchase_date_clear), tint = SilverTheme.colors.textPrimary)
                }
            }
        }
        errors[ShoeField.PURCHASE_DATE]?.let {
            Text(errorText(ShoeField.PURCHASE_DATE, it, form.currencyCode), style = SilverTheme.type.note, color = SilverTheme.colors.danger)
        }

        CardLabel(stringResource(R.string.field_colour), heading = false)
        ColourSwatches(selected = form.accentColor, onSelect = { onChange(form.copy(accentColor = it)) })

        PrimaryButton(stringResource(R.string.action_save), onSave, Modifier.fillMaxWidth(), enabled = !saving)
    }

    if (showCurrencyPicker) {
        CurrencyPicker(
            locale = locale,
            onPick = {
                onChange(form.copy(currencyCode = it))
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }
    if (showDatePicker) {
        PurchaseDatePicker(
            initialEpochDay = form.purchaseEpochDay,
            onPick = {
                onChange(form.copy(purchaseEpochDay = it))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** Shown when Save finds problems, so the message is visible wherever the form is scrolled. */
@Composable
private fun ProblemsDialog(errors: Map<ShoeField, FieldError>, currencyCode: String, onOk: () -> Unit) {
    SilverDialog(
        title = stringResource(R.string.save_problems_title),
        onDismiss = onOk,
        actions = listOf(DialogAction(stringResource(R.string.action_ok), DialogAction.Kind.PRIMARY, onOk)),
    ) {
        DialogText(stringResource(R.string.save_problems_intro))
        // Same order as the fields appear on screen.
        ShoeField.entries.filter { it in errors }.forEach { field ->
            DialogText(
                stringResource(
                    R.string.save_problems_item,
                    stringResource(field.labelRes()),
                    errorText(field, errors.getValue(field), currencyCode),
                ),
            )
        }
    }
}

/**
 * The eight shoe colours. Each is a 30 dp circle with a 48 dp touch target; the selected one gets a
 * white ring and tick, so colour is not the only cue, and each is read out by its colour name. One
 * row when there is room for eight 48 dp targets, otherwise two rows of four.
 */
@Composable
private fun ColourSwatches(selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= SilverSpacing.touch * ShoePalette.size) ShoePalette.size else ShoePalette.size / 2
        Column(Modifier.selectableGroup()) {
            ShoePalette.indices.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { index -> Swatch(index, selected == index) { onSelect(index) } }
                }
            }
        }
    }
}

@Composable
private fun Swatch(index: Int, selected: Boolean, onClick: () -> Unit) {
    val accent = ShoePalette[index]
    val name = stringResource(accent.nameRes)
    Box(
        Modifier
            .size(SilverSpacing.touch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(40.dp).border(3.dp, Color.White, SilverShapes.circle))
        Box(
            Modifier.size(SilverSpacing.swatch).background(accent.colour, SilverShapes.circle),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

private fun ShoeField.labelRes(): Int = when (this) {
    ShoeField.NAME -> R.string.field_label_name
    ShoeField.BRAND -> R.string.field_label_brand
    ShoeField.MODEL -> R.string.field_label_model
    ShoeField.PRICE -> R.string.field_label_price
    ShoeField.CURRENCY -> R.string.field_label_currency
    ShoeField.PURCHASE_DATE -> R.string.field_label_purchase_date
    ShoeField.ACCENT_COLOR -> R.string.field_label_colour
}

@Composable
private fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    max: Int,
    error: String?,
    placeholder: String? = null,
) {
    val length = value.codePointCount(0, value.length)
    SilverTextField(
        value = value,
        // Allow typing a little past the limit so the error is visible, but cap pasted text (SEC-07).
        onValueChange = { if (it.length <= max * 4) onValueChange(it) },
        label = label,
        placeholder = placeholder,
        error = error,
        helper = stringResource(R.string.char_counter, length, max),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
    )
}

@Composable
private fun errorText(field: ShoeField, error: FieldError, currencyCode: String): String = when (field) {
    ShoeField.NAME -> if (error == FieldError.REQUIRED) {
        stringResource(R.string.error_name_required)
    } else {
        pluralStringResource(R.plurals.error_too_long, ShoeValidator.NAME_MAX, ShoeValidator.NAME_MAX)
    }
    ShoeField.BRAND -> pluralStringResource(R.plurals.error_too_long, ShoeValidator.BRAND_MAX, ShoeValidator.BRAND_MAX)
    ShoeField.MODEL -> pluralStringResource(R.plurals.error_too_long, ShoeValidator.MODEL_MAX, ShoeValidator.MODEL_MAX)
    ShoeField.PRICE -> when (error) {
        FieldError.NEGATIVE -> stringResource(R.string.error_price_negative)
        FieldError.TOO_MANY_DECIMALS -> stringResource(R.string.error_price_decimals, currencyCode)
        FieldError.TOO_LARGE -> stringResource(R.string.error_price_too_large)
        else -> stringResource(R.string.error_price_invalid)
    }
    ShoeField.CURRENCY -> stringResource(R.string.error_currency_invalid)
    ShoeField.PURCHASE_DATE -> if (error == FieldError.IN_FUTURE) {
        stringResource(R.string.error_date_future)
    } else {
        stringResource(R.string.error_date_too_old)
    }
    ShoeField.ACCENT_COLOR -> stringResource(R.string.error_colour_invalid)
}

internal fun currencyName(code: String, locale: Locale): String =
    runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencyPicker(locale: Locale, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val all = remember(locale) {
        Currency.getAvailableCurrencies()
            .map { it.currencyCode }
            .filter { Money.isSupportedCurrency(it) }
            .sorted()
            .map { it to currencyName(it, locale) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { (code, name) -> code.contains(q, true) || name.contains(q, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.currency_choose),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { if (it.length <= 40) query = it },
                label = { Text(stringResource(R.string.currency_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn {
                items(filtered, key = { it.first }) { (code, name) ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.currency_item, code, name)) },
                        modifier = Modifier.selectable(selected = false, role = Role.Button) { onPick(code) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDatePicker(initialEpochDay: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val earliest = ShoeValidator.EARLIEST_PURCHASE_DATE
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochDay?.times(MILLIS_PER_DAY),
        yearRange = earliest.year..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY)
                return day in earliest.toEpochDay()..today.toEpochDay()
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    // The picker works in UTC midnight millis; floorDiv gives the epoch day on every API level.
                    state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) }
                        ?: onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    stringResource(R.string.field_purchase_date_pick),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}
