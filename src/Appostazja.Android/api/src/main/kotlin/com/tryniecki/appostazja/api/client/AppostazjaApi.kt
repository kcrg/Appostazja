package com.tryniecki.appostazja.api.client

import com.tryniecki.appostazja.api.model.DatasetStatusDto
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.api.model.PlaceReportsDto
import com.tryniecki.appostazja.api.model.PlaceSearchResultDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface AppostazjaApi {
    @GET("places")
    suspend fun getPlaces(
        @Query("minLat") minLat: Double? = null,
        @Query("maxLat") maxLat: Double? = null,
        @Query("minLon") minLon: Double? = null,
        @Query("maxLon") maxLon: Double? = null,
        @Header("Cache-Control") cacheControl: String? = null,
    ): List<PlaceDto>

    /** Search, sorting and filtering happen on the server; see `GET /api/v1/places/search`. */
    @GET("places/search")
    suspend fun searchPlaces(
        @Query("q") query: String? = null,
        @Query("sort") sort: String? = null,
        @Query("withReports") withReports: Boolean = false,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
    ): PlaceSearchResultDto

    @GET("places/{id}")
    suspend fun getPlace(@Path("id") id: String): PlaceDto

    @GET("places/{id}/reports")
    suspend fun getPlaceReports(@Path("id") id: String): PlaceReportsDto

    @GET("dataset")
    suspend fun getDatasetStatus(): DatasetStatusDto
}
