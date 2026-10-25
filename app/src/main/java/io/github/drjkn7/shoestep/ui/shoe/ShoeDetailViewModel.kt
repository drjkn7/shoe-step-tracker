package io.github.drjkn7.shoestep.ui.shoe

import io.github.drjkn7.shoestep.ui.common.WhileScreenShown
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.SessionWithSteps
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.session.SessionResult
import io.github.drjkn7.shoestep.steps.SoFarSteps
import io.github.drjkn7.shoestep.steps.StepReconciler
import io.github.drjkn7.shoestep.steps.soFarSteps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailState {
    data object Loading : DetailState
    data object NotFound : DetailState
    data class Loaded(val shoe: ShoeWithStats) : DetailState
}

class ShoeDetailViewModel(
    private val repository: ShoeRepository,
    private val sessions: SessionManager,
    private val shoeId: String,
    reconciler: StepReconciler,
) : ViewModel() {

    val state: StateFlow<DetailState> = repository.observeShoe(shoeId)
        .map { if (it == null) DetailState.NotFound else DetailState.Loaded(it) }
        .stateIn(viewModelScope, WhileScreenShown, DetailState.Loading)

    /** The app-wide active session (this pair's or another pair's), or null. */
    val activeSession: StateFlow<ActiveSessionWithShoe?> = sessions.activeSession
        .stateIn(viewModelScope, WhileScreenShown, null)

    /** This pair's sessions, newest first. */
    val history: StateFlow<List<SessionWithSteps>> = sessions.observeHistory(shoeId)
        .stateIn(viewModelScope, WhileScreenShown, emptyList())

    private val stepsRefresh = MutableStateFlow(0)

    /** "About N steps so far" while this pair's session runs or is paused (same as the Home card). */
    val stepsSoFar: StateFlow<SoFarSteps?> = soFarSteps(
        sessionKey = sessions.activeSession
            .map { active -> active?.session?.takeIf { it.shoeId == shoeId }?.let { it.id to it.state } }
            .distinctUntilChanged(),
        refresh = stepsRefresh,
        reconciler = reconciler,
    ).stateIn(viewModelScope, WhileScreenShown, null)

    /** Called when the page is shown again: re-read the "so far" count. */
    fun refreshSteps() {
        stepsRefresh.value += 1
    }

    private val _askSwitch = MutableStateFlow(false)
    /** True while the "Switch to this pair?" question is showing. */
    val askSwitch: StateFlow<Boolean> = _askSwitch.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    /** Becomes true once the shoe is deleted; the screen then navigates back. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun start() {
        viewModelScope.launch {
            if (sessions.start(shoeId) is SessionResult.NeedsSwitch) _askSwitch.value = true
        }
    }

    fun confirmSwitch() {
        _askSwitch.value = false
        viewModelScope.launch { sessions.switchTo(shoeId) }
    }

    fun dismissSwitch() {
        _askSwitch.value = false
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

    /** Only sessions added later can be deleted; SessionManager refuses any other. */
    fun deleteAddedLater(sessionId: String) {
        viewModelScope.launch { sessions.deleteAddedLater(sessionId) }
    }

    /** Archiving ends this pair's session too (SessionManager does both at once). */
    fun setArchived(archived: Boolean) {
        viewModelScope.launch { sessions.setArchived(shoeId, archived) }
    }

    fun delete() {
        viewModelScope.launch {
            sessions.deleteShoe(shoeId)
            _deleted.value = true
        }
    }
}
