package com.tryniecki.appostazja.data

import com.tryniecki.appostazja.api.client.AppostazjaApi
import com.tryniecki.appostazja.api.model.PlaceDto

class MapRepository(private val api: AppostazjaApi) {
    suspend fun getPlaces(revalidate: Boolean): List<PlaceDto> =
        api.getPlaces(cacheControl = if (revalidate) "no-cache" else null)

    suspend fun getPlaceReports(id: String) = api.getPlaceReports(id)
}
