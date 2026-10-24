package io.github.drjkn7.shoestep.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.export.CsvExportRepository
import io.github.drjkn7.shoestep.export.ExportKind
import io.github.drjkn7.shoestep.settings.DistanceUnit
import io.github.drjkn7.shoestep.settings.SettingsStore
import io.github.drjkn7.shoestep.settings.SystemNightMode
import io.github.drjkn7.shoestep.settings.ThemeMode
import io.github.drjkn7.shoestep.steps.StepReconciler
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.steps.StepsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

enum class ExportOutcome { SAVED, FAILED }

class SettingsViewModel(
    private val settings: SettingsStore,
    private val stepsSource: StepsSource,
    private val exporter: CsvExportRepository,
    private val clock: Clock,
    private val systemNightMode: SystemNightMode,
    private val reconciler: StepReconciler,
) : ViewModel() {

    /** Null = not chosen yet (new pairs use the region's currency). */
    val defaultCurrency: StateFlow<String?> = settings.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until read (a moment); the Theme control shows no choice until then. */
    val themeMode: StateFlow<ThemeMode?> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Settings → Appearance → Theme. The app redraws as soon as the choice is saved; Android is told
     * afterwards so the launch window matches next time (this may recreate the screen on Android 12+).
     */
    fun setThemeMode(mode: ThemeMode) {
        if (mode == themeMode.value) return
        viewModelScope.launch {
            settings.setThemeMode(mode)
            systemNightMode.apply(mode)
        }
    }

    /** Settings → Units → Distance. Every screen redraws as soon as it is saved; stored metres never change. */
    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { settings.setDistanceUnit(unit) }
    }

    private val _stepsAccess = MutableStateFlow<StepsAccess?>(null)
    val stepsAccess: StateFlow<StepsAccess?> = _stepsAccess.asStateFlow()

    private val _distanceAllowed = MutableStateFlow<Boolean?>(null)

    /** Whether distance can be read; null until first checked. False shows "Allow distance" under Step counting. */
    val distanceAllowed: StateFlow<Boolean?> = _distanceAllowed.asStateFlow()

    private val _exportOutcome = MutableStateFlow<ExportOutcome?>(null)
    val exportOutcome: StateFlow<ExportOutcome?> = _exportOutcome.asStateFlow()

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { settings.setDefaultCurrency(code) }
    }

    /** Called each time Settings is shown: access can change in Health Connect meanwhile. */
    fun refreshSteps() {
        viewModelScope.launch {
            _stepsAccess.value = stepsSource.access()
            _distanceAllowed.value = stepsSource.distanceAllowed()
        }
    }

    /** After the Health Connect permission screen closes: if distance is now allowed, fill it in straight away. */
    fun onPermissionResult() {
        viewModelScope.launch {
            _stepsAccess.value = stepsSource.access()
            val allowed = stepsSource.distanceAllowed()
            _distanceAllowed.value = allowed
            if (allowed) reconciler.reconcile()
        }
    }

    fun suggestedFileName(kind: ExportKind): String =
        exporter.fileName(kind, Instant.ofEpochMilli(clock.nowUtcMillis()).atZone(clock.zoneId()).toLocalDate())

    /** The user picked [uri] in the system file picker (null = they cancelled: nothing happens). */
    fun export(kind: ExportKind, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _exportOutcome.value = if (exporter.writeTo(kind, uri)) ExportOutcome.SAVED else ExportOutcome.FAILED
        }
    }

    fun dismissExportOutcome() {
        _exportOutcome.value = null
    }
}
