package io.github.drjkn7.shoestep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.steps.StepReconciler
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.steps.StepsSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the session card shows for steps. */
sealed interface SoFarSteps {
    /** Approximate steps since the session started ("About 1,500 steps so far"). */
    data class Count(val steps: Long) : SoFarSteps

    /** Steps can't be read right now ("Steps counted later"). */
    data object Later : SoFarSteps
}

class HomeViewModel(
    private val repository: ShoeRepository,
    private val sessions: SessionManager,
    private val stepsSource: StepsSource,
    private val reconciler: StepReconciler,
) : ViewModel() {

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    /** Null while the first result is loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val shoes: StateFlow<List<ShoeWithStats>?> = _showArchived
        .flatMapLatest { archived -> repository.observeShoes(archived) }
        .map<List<ShoeWithStats>, List<ShoeWithStats>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The running or paused session shown in the card at the top, or null. */
    val activeSession: StateFlow<ActiveSessionWithShoe?> = sessions.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _stepsAccess = MutableStateFlow<StepsAccess?>(null)

    /** Whether steps can be read; null until first checked. Anything but READY shows the Home banner. */
    val stepsAccess: StateFlow<StepsAccess?> = _stepsAccess.asStateFlow()

    private val stepsRefresh = MutableStateFlow(0)

    /**
     * Steps so far for the active session, read live from Health Connect (display only, nothing saved).
     * Re-read when the session changes, when Home is shown again, and every minute while it is visible.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val stepsSoFar: StateFlow<SoFarSteps?> = combine(
        sessions.activeSession.map { active -> active?.session?.let { it.id to it.state } }.distinctUntilChanged(),
        stepsRefresh,
    ) { key, _ -> key?.first }
        .flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf<SoFarSteps?>(null)
            } else {
                flow<SoFarSteps?> {
                    while (true) {
                        emit(reconciler.stepsSoFar(sessionId)?.let { SoFarSteps.Count(it) } ?: SoFarSteps.Later)
                        delay(SO_FAR_REFRESH_MILLIS)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
            stepsRefresh.value += 1
        }
    }

    /** After the Health Connect permission screen closes: if allowed, fill in waiting segments now. */
    fun onStepsPermissionResult() {
        viewModelScope.launch {
            val access = stepsSource.access()
            _stepsAccess.value = access
            stepsRefresh.value += 1
            if (access == StepsAccess.READY) reconciler.reconcile()
        }
    }

    private companion object {
        const val SO_FAR_REFRESH_MILLIS = 60_000L
    }
}
