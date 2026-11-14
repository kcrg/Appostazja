package com.tryniecki.appostazja.ui.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.appostazja.api.model.StatsDto
import com.tryniecki.appostazja.data.MapRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class StatsUiState(
    val stats: StatsDto? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

/** Loads `GET /stats`; every aggregate comes from the API. */
internal class StatsViewModel(private val repository: MapRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = false) }
        viewModelScope.launch {
            try {
                val stats = repository.getStats()
                _uiState.value = StatsUiState(stats = stats, isLoading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.w("Stats", "Loading statistics failed", exception)
                _uiState.update { it.copy(isLoading = false, error = true) }
            }
        }
    }

    companion object {
        fun factory(repository: MapRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = StatsViewModel(repository) as T
            }
    }
}
