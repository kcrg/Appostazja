package com.tryniecki.appostazja.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.data.MapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class MapUiState(
    val places: List<PlaceDto> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: Boolean = false,
)

internal class MapViewModel(private val repository: MapRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.places.isEmpty(),
                    isRefreshing = true,
                    error = false,
                )
            }
            runCatching { repository.getPlaces() }
                .onSuccess { places ->
                    _uiState.value = MapUiState(
                        places = places,
                        isLoading = false,
                        isRefreshing = false,
                        error = false,
                    )
                }
                .onFailure {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = current.places.isEmpty(),
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(repository: MapRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MapViewModel(repository) as T
            }
    }
}
