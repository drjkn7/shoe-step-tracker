package io.github.drjkn7.shoestep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.db.ActiveSessionWithShoe
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import io.github.drjkn7.shoestep.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ShoeRepository,
    private val sessions: SessionManager,
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
}
