package com.tryniecki.appostazja.data

import com.tryniecki.appostazja.api.client.AppostazjaApi
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.api.model.PlaceReportsDto
import com.tryniecki.appostazja.api.model.ReportDto
import com.tryniecki.appostazja.api.model.StatsDto
import com.tryniecki.appostazja.data.local.CacheDao
import com.tryniecki.appostazja.data.local.CachedResponseEntity
import com.tryniecki.appostazja.data.local.PlaceDetailsEntity
import com.tryniecki.appostazja.data.local.ReportEntity
import com.tryniecki.appostazja.data.local.toDto
import com.tryniecki.appostazja.data.local.toEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response

/**
 * The API mirrored in Room. Cached data is shown first and revalidated with its ETag, so the app opens
 * instantly, works offline and downloads a dataset only when it changed. Search stays online-only.
 */
class MapRepository(
    private val api: AppostazjaApi,
    private val cache: CacheDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The cached map dataset; refreshed by [refreshPlaces]. */
    val places: Flow<List<PlaceDto>> = cache.observePlaces().map { rows -> rows.map { it.toDto() } }

    /**
     * Revalidates the map dataset. Returns whether it changed; a 304 or an unchanged ETag leaves the rows
     * untouched. Network and server errors are thrown, and the cached dataset stays as it was.
     */
    suspend fun refreshPlaces(): Boolean {
        val known = cache.response(PLACES_KEY)?.takeIf { cache.placeCount() > 0 }
        val response = api.getPlaces(ifNoneMatch = known?.etag)
        val now = clock()
        if (response.code() == HTTP_NOT_MODIFIED && known != null) {
            cache.upsertResponse(known.copy(syncedAtMillis = now))
            return false
        }
        val places = response.requireBody()
        cache.replacePlaces(
            places.mapIndexed { index, place -> place.toEntity(index) },
            CachedResponseEntity(PLACES_KEY, response.etag(), body = null, syncedAtMillis = now),
        )
        return true
    }

    /**
     * Emits the cached reports of a place first (if any), then the current ones when they changed. Without
     * a connection the cached copy is enough; the flow fails only when there is nothing to show.
     */
    fun placeReports(id: String): Flow<PlaceReportsDto> = flow {
        val details = cache.placeDetails(id)
        val cached = details?.let { PlaceReportsDto(it.website, cache.reports(id).map(ReportEntity::toDto)) }
        cached?.let { emit(it) }

        val response = fetchOrNull(cached != null) { api.getPlaceReports(id, ifNoneMatch = details?.etag) }
            ?: return@flow
        if (response.code() == HTTP_NOT_MODIFIED && details != null) {
            cache.upsertPlaceDetails(details.copy(syncedAtMillis = clock()))
            return@flow
        }
        val fresh = response.requireBody()
        cache.replaceReports(
            PlaceDetailsEntity(id, fresh.website, response.etag(), clock()),
            fresh.reports.mapIndexed { index, report -> report.toEntity(id, index) },
        )
        emit(fresh)
    }

    /** Statistics with the same cache-first behaviour as [placeReports]. */
    fun stats(): Flow<StatsDto> = flow {
        val known = cache.response(STATS_KEY)
        val cached = known?.body?.let { body -> runCatching { CacheJson.decodeFromString<StatsDto>(body) }.getOrNull() }
        cached?.let { emit(it) }

        val response = fetchOrNull(cached != null) { api.getStats(ifNoneMatch = known?.etag.takeIf { cached != null }) }
            ?: return@flow
        if (response.code() == HTTP_NOT_MODIFIED && known != null && cached != null) {
            cache.upsertResponse(known.copy(syncedAtMillis = clock()))
            return@flow
        }
        val fresh = response.requireBody()
        cache.upsertResponse(CachedResponseEntity(STATS_KEY, response.etag(), CacheJson.encodeToString(fresh), clock()))
        emit(fresh)
    }

    suspend fun getDatasetStatus() = api.getDatasetStatus()

    suspend fun searchPlaces(query: String, sort: String, withReportsOnly: Boolean, offset: Int, limit: Int) =
        api.searchPlaces(query.ifBlank { null }, sort, withReportsOnly, offset, limit)

    /** Runs a request; when a cached copy is already shown, a failure is swallowed instead of thrown. */
    private suspend fun <T> fetchOrNull(hasCachedCopy: Boolean, request: suspend () -> Response<T>): Response<T>? =
        try {
            request().also { if (!it.isSuccessful && it.code() != HTTP_NOT_MODIFIED) throw HttpException(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (hasCachedCopy && failure.isRecoverable()) null else throw failure
        }

    private companion object {
        const val PLACES_KEY = "places"
        const val STATS_KEY = "stats"
        const val HTTP_NOT_MODIFIED = 304
        val CacheJson = Json { ignoreUnknownKeys = true }
    }
}

private fun Exception.isRecoverable() =
    this is java.io.IOException || this is HttpException || this is SerializationException

private fun <T> Response<T>.etag(): String? = headers()["ETag"]

private fun <T> Response<T>.requireBody(): T {
    if (!isSuccessful) throw HttpException(this)
    return body() ?: throw SerializationException("Empty body for HTTP ${code()}.")
}

private fun ReportEntity.toDto() = ReportDto(stage, rating, date, comment)

private fun ReportDto.toEntity(placeId: String, position: Int) =
    ReportEntity(placeId, position, stage, rating, date, comment)
