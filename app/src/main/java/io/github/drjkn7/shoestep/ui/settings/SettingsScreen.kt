package io.github.drjkn7.shoestep.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.DebugTools
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.export.ExportKind
import io.github.drjkn7.shoestep.notify.AndroidNotificationAccess
import io.github.drjkn7.shoestep.settings.DistanceUnit
import io.github.drjkn7.shoestep.settings.ThemeMode
import io.github.drjkn7.shoestep.steps.HealthConnectStepsSource
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.ui.common.CardLabel
import io.github.drjkn7.shoestep.ui.common.InfoDialog
import io.github.drjkn7.shoestep.ui.common.LinkRow
import io.github.drjkn7.shoestep.ui.common.LocalDistanceUnit
import io.github.drjkn7.shoestep.ui.common.NavIcon
import io.github.drjkn7.shoestep.ui.common.PrimaryButton
import io.github.drjkn7.shoestep.ui.common.ScreenColumn
import io.github.drjkn7.shoestep.ui.common.SecondaryButton
import io.github.drjkn7.shoestep.ui.common.SegmentedControl
import io.github.drjkn7.shoestep.ui.common.SilverCard
import io.github.drjkn7.shoestep.ui.common.SilverScreen
import io.github.drjkn7.shoestep.ui.common.SwitchRow
import io.github.drjkn7.shoestep.ui.home.StepsAccessContent
import io.github.drjkn7.shoestep.ui.shoe.CurrencyPicker
import io.github.drjkn7.shoestep.ui.shoe.currencyName
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme

/** Settings (PNG 21): default currency, appearance, step counting, export, privacy, licences and version. */
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
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val distanceAllowed by viewModel.distanceAllowed.collectAsStateWithLifecycle()
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }

    val sessionNotification by viewModel.sessionNotification.collectAsStateWithLifecycle()
    val notificationsAllowed by viewModel.notificationsAllowed.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSteps()
        viewModel.refreshNotifications()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshNotifications()
    }
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectStepsSource.permissionContract()) {
        viewModel.onPermissionResult()
    }
    // The system file picker: the user chooses where the file goes, so no storage permission is needed.
    val exportPairs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) {
        viewModel.export(ExportKind.PAIRS, it)
    }
    val exportSessions = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) {
        viewModel.export(ExportKind.SESSIONS, it)
    }

    SilverScreen(title = stringResource(R.string.title_settings), navIcon = NavIcon.BACK, onNavigate = onBack) {
        ScreenColumn {
            Section(stringResource(R.string.settings_new_pairs)) {
                val code = currency ?: Money.defaultCurrencyCode(locale)
                LinkRow(
                    title = stringResource(R.string.settings_default_currency),
                    value = stringResource(
                        if (currency == null) R.string.settings_currency_not_set else R.string.settings_currency_value,
                        code,
                        currencyName(code, locale),
                    ),
                    onClick = { showCurrencyPicker = true },
                )
            }

            // Not mocked up yet: follows the Units card in PNG 21 (label, then a segmented control).
            Section(stringResource(R.string.settings_appearance)) {
                val theme = stringResource(R.string.settings_theme)
                SettingTitle(theme)
                themeMode?.let { mode ->
                    SegmentedControl(
                        options = ThemeMode.entries,
                        selected = mode,
                        label = { stringResource(it.labelRes()) },
                        onSelect = viewModel::setThemeMode,
                        description = theme,
                        modifier = Modifier.padding(top = SilverSpacing.xs),
                    )
                }
            }

            // PNG 21: Units → Distance, Kilometres / Miles.
            Section(stringResource(R.string.settings_units)) {
                val distance = stringResource(R.string.settings_distance)
                SettingTitle(distance)
                SegmentedControl(
                    options = DistanceUnit.entries,
                    selected = LocalDistanceUnit.current,
                    label = { stringResource(it.labelRes()) },
                    onSelect = viewModel::setDistanceUnit,
                    description = distance,
                    modifier = Modifier.padding(top = SilverSpacing.xs),
                )
            }

            Section(stringResource(R.string.settings_steps)) {
                when (val access = stepsAccess) {
                    StepsAccess.READY -> {
                        if (distanceAllowed == false) {
                            BodyText(stringResource(R.string.settings_steps_no_distance))
                            PrimaryButton(
                                stringResource(R.string.settings_allow_distance),
                                onClick = { permissionLauncher.launch(setOf(HealthConnectStepsSource.DISTANCE_PERMISSION)) },
                                modifier = Modifier.fillMaxWidth().padding(top = SilverSpacing.s),
                            )
                        } else {
                            BodyText(stringResource(R.string.settings_steps_ready))
                        }
                        SecondaryButton(
                            stringResource(R.string.settings_manage_health_connect),
                            onClick = { openHealthConnect(context) },
                            modifier = Modifier.fillMaxWidth().padding(top = SilverSpacing.s),
                        )
                    }
                    null -> Unit
                    else -> StepsAccessContent(
                        access = access,
                        onAllow = { permissionLauncher.launch(HealthConnectStepsSource.PERMISSIONS) },
                        onInstall = { HealthConnectStepsSource.openInstallPage(context) },
                    )
                }
            }

            // PNG 21: Notifications → Session notification.
            sessionNotification?.let { on ->
                Section(stringResource(R.string.settings_notifications)) {
                    SwitchRow(
                        label = stringResource(R.string.settings_session_notification),
                        checked = on,
                        onCheckedChange = { turnOn ->
                            viewModel.setSessionNotification(turnOn)
                            if (turnOn && viewModel.needsNotificationPermission()) {
                                notificationPermission.launch(AndroidNotificationAccess.PERMISSION)
                            }
                        },
                        note = stringResource(R.string.settings_session_notification_note),
                    )
                    // On, but refused in Android (or its prompt can't be shown any more): say so and link there.
                    if (on && !notificationsAllowed) {
                        BodyText(stringResource(R.string.settings_notifications_blocked))
                        SecondaryButton(
                            stringResource(R.string.settings_open_android_settings),
                            onClick = { AndroidNotificationAccess.openSystemSettings(context) },
                            modifier = Modifier.fillMaxWidth().padding(top = SilverSpacing.s),
                        )
                    }
                }
            }

            Section(stringResource(R.string.settings_your_data)) {
                BodyText(stringResource(R.string.settings_export_body))
                SecondaryButton(
                    stringResource(R.string.settings_export_pairs),
                    onClick = { exportPairs.launch(viewModel.suggestedFileName(ExportKind.PAIRS)) },
                    modifier = Modifier.fillMaxWidth().padding(top = SilverSpacing.s),
                )
                SecondaryButton(
                    stringResource(R.string.settings_export_sessions),
                    onClick = { exportSessions.launch(viewModel.suggestedFileName(ExportKind.SESSIONS)) },
                    modifier = Modifier.fillMaxWidth().padding(top = SilverSpacing.s),
                )
            }

            Section(stringResource(R.string.settings_about)) {
                LinkRow(stringResource(R.string.settings_privacy), onOpenPrivacy)
                LinkRow(stringResource(R.string.settings_licences), onOpenLicences)
                Text(
                    stringResource(R.string.settings_version, appVersion(context)),
                    style = SilverTheme.type.body,
                    color = SilverTheme.colors.textMuted,
                    modifier = Modifier.padding(top = SilverSpacing.s),
                )
            }

            // Test app only (src/debug); nothing in release builds.
            DebugTools.SettingsExtras()
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

/** One Settings card (PNG 21): a small-capitals heading, then its rows. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SilverCard {
        CardLabel(title, Modifier.padding(bottom = SilverSpacing.xs))
        content()
    }
}

/** A setting's name above its control ("Theme", "Distance"). */
@Composable
private fun SettingTitle(text: String) {
    Text(text, style = SilverTheme.type.body.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium), color = SilverTheme.colors.textPrimary)
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = SilverTheme.type.body.copy(fontSize = SilverTheme.type.button.fontSize, lineHeight = 22.sp), color = SilverTheme.colors.textPrimary)
}

private fun DistanceUnit.labelRes(): Int = when (this) {
    DistanceUnit.KILOMETRES -> R.string.units_kilometres
    DistanceUnit.MILES -> R.string.units_miles
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.SYSTEM -> R.string.theme_system
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
