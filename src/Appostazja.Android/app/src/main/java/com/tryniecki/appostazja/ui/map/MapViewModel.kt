package com.tryniecki.appostazja.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.appostazja.BuildConfig
import com.tryniecki.appostazja.data.MapRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

internal data class MapUiState(
    val places: List<ChurchClusterItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: Boolean = false,
    val errorDetails: String? = null,
)

internal class MapViewModel(private val repository: MapRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        load(revalidate = false)
    }

    fun refresh() = load(revalidate = true)

    private fun load(revalidate: Boolean) {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.places.isEmpty(),
                    isRefreshing = true,
                    error = false,
                    errorDetails = null,
                )
            }
            val currentPlaces = _uiState.value.places
            runCatching {
                val places = repository.getPlaces(revalidate)
                withContext(Dispatchers.Default) {
                    if (places.size == currentPlaces.size &&
                        places.indices.all { places[it] == currentPlaces[it].place }
                    ) currentPlaces else places.map(::ChurchClusterItem)
                }
            }
                .onSuccess { places ->
                    _uiState.value = MapUiState(
                        places = places,
                        isLoading = false,
                        isRefreshing = false,
                        error = false,
                        errorDetails = null,
                    )
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(
                        TAG,
                        "Failed to load places from ${BuildConfig.APPOSTAZJA_API_BASE_URL}",
                        throwable,
                    )
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = current.places.isEmpty(),
                            errorDetails = if (BuildConfig.DEBUG) throwable.toDebugMessage() else null,
                        )
                    }
                }
        }
    }

    private fun Throwable.toDebugMessage(): String {
        val category = when (this) {
            is SerializationException -> "JSON"
            is IOException -> "Sieć"
            else -> "API"
        }
        val message = message?.takeIf(String::isNotBlank) ?: "brak szczegółów"
        return "$category: ${javaClass.simpleName}: $message\n${BuildConfig.APPOSTAZJA_API_BASE_URL}"
    }

    companion object {
        private const val TAG = "AppostazjaApi"

        fun factory(repository: MapRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MapViewModel(repository) as T
            }
    }
}
