package com.tryniecki.appostazja.api.client

import com.tryniecki.appostazja.api.model.DatasetStatusDto
import com.tryniecki.appostazja.api.model.PlaceDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AppostazjaApi {
    @GET("places")
    suspend fun getPlaces(
        @Query("minLat") minLat: Double? = null,
        @Query("maxLat") maxLat: Double? = null,
        @Query("minLon") minLon: Double? = null,
        @Query("maxLon") maxLon: Double? = null,
    ): List<PlaceDto>

    @GET("places/{id}")
    suspend fun getPlace(@Path("id") id: String): PlaceDto

    @GET("dataset")
    suspend fun getDatasetStatus(): DatasetStatusDto
}
