package io.github.drjkn7.shoestep.ui.home

import io.github.drjkn7.shoestep.ui.common.WhileScreenShown
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.settings.SettingsStore
import io.github.drjkn7.shoestep.steps.StepReconciler
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.steps.SoFarSteps
import io.github.drjkn7.shoestep.steps.StepsSource
import io.github.drjkn7.shoestep.steps.soFarSteps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ShoeRepository,
    private val sessions: SessionManager,
    private val stepsSource: StepsSource,
    private val reconciler: StepReconciler,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    /** Null while the first result is loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val shoes: StateFlow<List<ShoeWithStats>?> = _showArchived
        .flatMapLatest { archived -> repository.observeShoes(archived) }
        .stateIn<List<ShoeWithStats>?>(viewModelScope, WhileScreenShown, null)

    /** The running or paused session shown in the card at the top, or null. */
    val activeSession: StateFlow<ActiveSessionWithShoe?> = sessions.activeSession
        .stateIn(viewModelScope, WhileScreenShown, null)

    private val _stepsAccess = MutableStateFlow<StepsAccess?>(null)

    /** Whether steps can be read; null until first checked. Anything but READY shows the Home banner. */
    val stepsAccess: StateFlow<StepsAccess?> = _stepsAccess.asStateFlow()

    private val _distanceAllowed = MutableStateFlow<Boolean?>(null)

    /**
     * Home's "Show distance" card (PNG 03): steps are allowed but distance isn't, and "Not now" was never
     * tapped. False until both checks have answered, so the card never flashes up at launch.
     */
    val showDistanceCard: StateFlow<Boolean> = combine(_stepsAccess, _distanceAllowed, settings.distanceCardDismissed) { access, allowed, dismissed ->
        access == StepsAccess.READY && allowed == false && !dismissed
    }.stateIn(viewModelScope, WhileScreenShown, false)

    fun dismissDistanceCard() {
        viewModelScope.launch { settings.dismissDistanceCard() }
    }

    private val stepsRefresh = MutableStateFlow(0)

    /**
     * Steps so far for the active session, read live from Health Connect (display only, nothing saved).
     * Re-read when the session changes, when Home is shown again, and every minute while it is visible.
     */
    val stepsSoFar: StateFlow<SoFarSteps?> = soFarSteps(
        sessionKey = sessions.activeSession.map { active -> active?.session?.let { it.id to it.state } }.distinctUntilChanged(),
        refresh = stepsRefresh,
        reconciler = reconciler,
    ).stateIn(viewModelScope, WhileScreenShown, null)

    fun setShowArchived(show: Boolean) {
        _showArchived.value = show
    }

    fun pause(sessionId: String) {
        viewModelScope.launch { sessions.pause(sessionId) }
    }

    fun resume(sessionId: String) {
        viewModelScope.launch { sessions.resume(sessionId) }
    }

    fun end(sessionId: String) {
        viewModelScope.launch { sessions.end(sessionId) }
    }

    /** Called each time Home is shown: re-checks access (it can change in Health Connect) and re-reads steps. */
    fun refreshSteps() {
        viewModelScope.launch {
            _stepsAccess.value = stepsSource.access()
            _distanceAllowed.value = stepsSource.distanceAllowed()
            stepsRefresh.value += 1
        }
    }

    /** After the Health Connect permission screen closes: if allowed, fill in waiting segments now. */
    fun onStepsPermissionResult() {
        viewModelScope.launch {
            val access = stepsSource.access()
            _stepsAccess.value = access
            _distanceAllowed.value = stepsSource.distanceAllowed()
            stepsRefresh.value += 1
            if (access == StepsAccess.READY) reconciler.reconcile()
        }
    }
}
