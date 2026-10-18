package io.github.drjkn7.shoestep.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.export.ExportKind
import io.github.drjkn7.shoestep.steps.HealthConnectStepsSource
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.ui.home.StepsAccessCard
import io.github.drjkn7.shoestep.ui.nfc.InfoDialog
import io.github.drjkn7.shoestep.ui.shoe.CurrencyPicker
import io.github.drjkn7.shoestep.ui.shoe.currencyName

/** Default currency, step counting, export, privacy, licences and version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicences: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val currency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val stepsAccess by viewModel.stepsAccess.collectAsStateWithLifecycle()
    val exportOutcome by viewModel.exportOutcome.collectAsStateWithLifecycle()
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSteps() }
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectStepsSource.permissionContract()) {
        viewModel.refreshSteps()
    }
    // The system file picker: the user chooses where the file goes, so no storage permission is needed.
    val exportPairs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) {
        viewModel.export(ExportKind.PAIRS, it)
    }
    val exportSessions = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) {
        viewModel.export(ExportKind.SESSIONS, it)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(stringResource(R.string.settings_new_pairs)) {
                val code = currency ?: Money.defaultCurrencyCode(locale)
                val label = stringResource(
                    if (currency == null) R.string.settings_currency_not_set else R.string.settings_currency_value,
                    code,
                    currencyName(code, locale),
                )
                Text(stringResource(R.string.settings_default_currency), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { showCurrencyPicker = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }

            Section(stringResource(R.string.settings_steps)) {
                when (val access = stepsAccess) {
                    StepsAccess.READY -> {
                        Text(stringResource(R.string.settings_steps_ready), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { openHealthConnect(context) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_manage_health_connect))
                        }
                    }
                    null -> Unit
                    else -> StepsAccessCard(
                        access = access,
                        onAllow = { permissionLauncher.launch(HealthConnectStepsSource.PERMISSIONS) },
                        onInstall = { startSafely(context) { it.startActivity(HealthConnectStepsSource.installIntent()) } },
                    )
                }
            }

            Section(stringResource(R.string.settings_your_data)) {
                Text(stringResource(R.string.settings_export_body), style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { exportPairs.launch(viewModel.suggestedFileName(ExportKind.PAIRS)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_export_pairs)) }
                Button(
                    onClick = { exportSessions.launch(viewModel.suggestedFileName(ExportKind.SESSIONS)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_export_sessions)) }
            }

            Section(stringResource(R.string.settings_about)) {
                TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.settings_privacy)) }
                TextButton(onClick = onOpenLicences) { Text(stringResource(R.string.settings_licences)) }
                Text(stringResource(R.string.settings_version, appVersion(context)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showCurrencyPicker) {
        CurrencyPicker(
            locale = locale,
            onPick = {
                showCurrencyPicker = false
                viewModel.setDefaultCurrency(it)
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    exportOutcome?.let {
        InfoDialog(
            title = stringResource(R.string.settings_your_data),
            text = stringResource(if (it == ExportOutcome.SAVED) R.string.settings_export_done else R.string.settings_export_failed),
            onDismiss = viewModel::dismissExportOutcome,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private const val CSV_MIME = "text/csv"

private fun openHealthConnect(context: Context) =
    startSafely(context) { it.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(it)) }

/** Opening another app can fail (not installed, disabled); the screen text already explains the state. */
private fun startSafely(context: Context, start: (Context) -> Unit) {
    try {
        start(context)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
