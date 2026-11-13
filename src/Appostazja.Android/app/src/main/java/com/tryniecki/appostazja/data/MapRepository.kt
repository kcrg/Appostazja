package com.tryniecki.appostazja.data

import com.tryniecki.appostazja.api.client.AppostazjaApi
import com.tryniecki.appostazja.api.model.PlaceDto

class MapRepository(private val api: AppostazjaApi) {
    suspend fun getPlaces(revalidate: Boolean): List<PlaceDto> =
        api.getPlaces(cacheControl = if (revalidate) "no-cache" else null)

    suspend fun getPlaceReports(id: String) = api.getPlaceReports(id)

    suspend fun getDatasetStatus() = api.getDatasetStatus()

    suspend fun searchPlaces(query: String, sort: String, withReportsOnly: Boolean, offset: Int, limit: Int) =
        api.searchPlaces(query.ifBlank { null }, sort, withReportsOnly, offset, limit)
}
