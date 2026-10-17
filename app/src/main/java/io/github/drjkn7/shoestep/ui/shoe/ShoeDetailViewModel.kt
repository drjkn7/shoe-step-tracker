package io.github.drjkn7.shoestep.ui.shoe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private val shoeId: String,
) : ViewModel() {

    val state: StateFlow<DetailState> = repository.observeShoe(shoeId)
        .map { if (it == null) DetailState.NotFound else DetailState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    private val _deleted = MutableStateFlow(false)
    /** Becomes true once the shoe is deleted; the screen then navigates back. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { repository.setArchived(shoeId, archived) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(shoeId)
            _deleted.value = true
        }
    }
}
