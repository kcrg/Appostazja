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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException

internal data class MapUiState(
    val places: List<ChurchClusterItem> = emptyList(),
    /** True until the cache has been read once, so an empty first frame is not mistaken for "no data". */
    val cacheRead: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val errorDetails: String? = null,
) {
    val isLoading: Boolean get() = places.isEmpty() && (!cacheRead || isRefreshing)

    /** Only a map with nothing to show is an error; with cached places the app simply works offline. */
    val error: Boolean get() = places.isEmpty() && cacheRead && refreshFailed && !isRefreshing
}

internal class MapViewModel(private val repository: MapRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var current = emptyList<ChurchClusterItem>()
            repository.places
                .map { places ->
                    // Keep the same items when nothing changed, so clustering does not run again.
                    if (places.size == current.size && places.indices.all { places[it] == current[it].place }) {
                        current
                    } else {
                        places.map(::ChurchClusterItem)
                    }
                }
                .flowOn(Dispatchers.Default)
                .collect { items ->
                    current = items
                    _uiState.update { it.copy(places = items, cacheRead = true) }
                }
        }
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, refreshFailed = false, errorDetails = null) }
        viewModelScope.launch {
            try {
                repository.refreshPlaces()
                _uiState.update { it.copy(isRefreshing = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Refreshing places from ${BuildConfig.APPOSTAZJA_API_BASE_URL} failed", failure)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        refreshFailed = true,
                        errorDetails = if (BuildConfig.DEBUG) failure.toDebugMessage() else null,
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
