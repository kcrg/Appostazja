package com.tryniecki.appostazja.ui.map

import com.tryniecki.appostazja.api.model.PlaceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A parish picked outside the map (in the search) that the map should open and fly to, exactly once. */
class MapFocus {
    private val _place = MutableStateFlow<PlaceDto?>(null)
    val place: StateFlow<PlaceDto?> = _place.asStateFlow()

    fun show(place: PlaceDto) {
        _place.value = place
    }

    fun take(place: PlaceDto): Boolean = _place.compareAndSet(place, null)
}
