package com.tryniecki.appostazja.data

import com.tryniecki.appostazja.api.client.AppostazjaApi
import com.tryniecki.appostazja.api.model.PlaceDto

class MapRepository(private val api: AppostazjaApi) {
    suspend fun getPlaces(): List<PlaceDto> = api.getPlaces()
}
