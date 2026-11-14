package com.tryniecki.appostazja.api.client

import com.tryniecki.appostazja.api.model.DatasetStatusDto
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.api.model.PlaceReportsDto
import com.tryniecki.appostazja.api.model.PlaceSearchResultDto
import com.tryniecki.appostazja.api.model.StatsDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints that clients cache take an `If-None-Match` ETag and return the raw [Response]: a 304 means the
 * cached copy is current and carries no body.
 */
interface AppostazjaApi {
    @GET("places")
    suspend fun getPlaces(
        @Query("minLat") minLat: Double? = null,
        @Query("maxLat") maxLat: Double? = null,
        @Query("minLon") minLon: Double? = null,
        @Query("maxLon") maxLon: Double? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<List<PlaceDto>>

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
    suspend fun getPlaceReports(
        @Path("id") id: String,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<PlaceReportsDto>

    @GET("stats")
    suspend fun getStats(@Header("If-None-Match") ifNoneMatch: String? = null): Response<StatsDto>

    @GET("dataset")
    suspend fun getDatasetStatus(): DatasetStatusDto
}
