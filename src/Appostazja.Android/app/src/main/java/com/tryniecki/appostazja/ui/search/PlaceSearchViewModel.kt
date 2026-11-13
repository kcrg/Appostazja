package com.tryniecki.appostazja.ui.search

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.data.MapRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sort orders offered by the API; the app only passes the choice through. */
internal enum class SearchSort(val apiValue: String, @StringRes val label: Int) {
    Relevance("relevance", R.string.search_sort_relevance),
    Best("best", R.string.search_sort_best),
    Worst("worst", R.string.search_sort_worst),
    Reports("reports", R.string.search_sort_reports),
    Name("name", R.string.search_sort_name),
}

internal data class PlaceSearchUiState(
    val query: String = "",
    val sort: SearchSort = SearchSort.Relevance,
    val withReportsOnly: Boolean = false,
    val results: List<PlaceDto> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
) {
    val canLoadMore: Boolean get() = !isLoading && !isLoadingMore && !error && results.size < total
}

/**
 * Holds the search form and pages through `GET /places/search`. All matching, sorting and filtering
 * happen in the API.
 */
internal class PlaceSearchViewModel(private val repository: MapRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaceSearchUiState())
    val uiState: StateFlow<PlaceSearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    private var loaded = false

    /** The first search runs when the user opens the search, not at app start. */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        search(debounce = false)
    }

    fun onQueryChange(query: String) {
        if (query == _uiState.value.query) return
        _uiState.update { it.copy(query = query) }
        search(debounce = true)
    }

    fun onSortChange(sort: SearchSort) {
        if (sort == _uiState.value.sort) return
        _uiState.update { it.copy(sort = sort) }
        search(debounce = false)
    }

    fun onWithReportsOnlyChange(enabled: Boolean) {
        _uiState.update { it.copy(withReportsOnly = enabled) }
        search(debounce = false)
    }

    fun retry() = search(debounce = false)

    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        // Runs on the same job, so a new query cancels a page that would belong to the old one.
        val previous = searchJob
        searchJob = viewModelScope.launch {
            previous?.join()
            fetch(state, offset = state.results.size)
        }
    }

    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        _uiState.update { it.copy(isLoading = true, isLoadingMore = false, error = false) }
        searchJob = viewModelScope.launch {
            // Wait for a pause in typing instead of querying on every key press.
            if (debounce) delay(DEBOUNCE_MS)
            fetch(_uiState.value, offset = 0)
        }
    }

    private suspend fun fetch(request: PlaceSearchUiState, offset: Int) {
        try {
            val page = repository.searchPlaces(
                query = request.query.trim(),
                sort = request.sort.apiValue,
                withReportsOnly = request.withReportsOnly,
                offset = offset,
                limit = PAGE_SIZE,
            )
            _uiState.update {
                it.copy(
                    results = if (offset == 0) page.items else it.results + page.items,
                    total = page.total,
                    isLoading = false,
                    isLoadingMore = false,
                    error = false,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            Log.w(TAG, "Search failed", exception)
            _uiState.update { it.copy(isLoading = false, isLoadingMore = false, error = true) }
        }
    }

    companion object {
        private const val TAG = "PlaceSearch"
        private const val DEBOUNCE_MS = 300L
        private const val PAGE_SIZE = 30

        fun factory(repository: MapRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaceSearchViewModel(repository) as T
            }
    }
}
