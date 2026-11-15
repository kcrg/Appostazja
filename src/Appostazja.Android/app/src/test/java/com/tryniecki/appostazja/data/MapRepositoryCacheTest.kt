package com.tryniecki.appostazja.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tryniecki.appostazja.api.client.AppostazjaApi
import com.tryniecki.appostazja.api.model.DatasetStatusDto
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.api.model.PlaceReportsDto
import com.tryniecki.appostazja.api.model.PlaceSearchResultDto
import com.tryniecki.appostazja.api.model.RatingBreakdownDto
import com.tryniecki.appostazja.api.model.ReportDto
import com.tryniecki.appostazja.api.model.StageRatingsDto
import com.tryniecki.appostazja.api.model.StatsDto
import com.tryniecki.appostazja.api.model.ToneCountsDto
import com.tryniecki.appostazja.data.local.AppostazjaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class MapRepositoryCacheTest {
    private val database = Room.inMemoryDatabaseBuilder<AppostazjaDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val api = FakeApi()
    private val repository = MapRepository(api, database.cacheDao(), clock = { 1_000L })

    @After
    fun close() = database.close()

    @Test
    fun `unchanged dataset is revalidated with its ETag and not rewritten`() = runBlocking {
        api.places = { ok(listOf(place("a"), place("b")), etag = "\"v1\"") }
        assertTrue(repository.refreshPlaces())

        api.places = { notModified() }
        assertFalse(repository.refreshPlaces())

        assertEquals("\"v1\"", api.lastPlacesEtag)
        assertEquals(listOf("a", "b"), repository.places.first().map { it.id })
    }

    @Test
    fun `changed dataset replaces every place and keeps the API order`() = runBlocking {
        api.places = { ok(listOf(place("a"), place("b"), place("c")), etag = "\"v1\"") }
        repository.refreshPlaces()

        api.places = { ok(listOf(place("c"), place("a")), etag = "\"v2\"") }
        assertTrue(repository.refreshPlaces())

        assertEquals(listOf("c", "a"), repository.places.first().map { it.id })
    }

    @Test
    fun `a failed refresh keeps the cached map and the next request still revalidates`() = runBlocking {
        api.places = { ok(listOf(place("a")), etag = "\"v1\"") }
        repository.refreshPlaces()

        api.places = { throw IOException("offline") }
        assertThrows(IOException::class.java) { runBlocking { repository.refreshPlaces() } }
        assertEquals(listOf("a"), repository.places.first().map { it.id })

        api.places = { notModified() }
        repository.refreshPlaces()
        assertEquals("\"v1\"", api.lastPlacesEtag)
    }

    @Test
    fun `stage breakdown and missing optional fields survive the cache`() = runBlocking {
        val withStages = place("a").copy(
            stages = StageRatingsDto(ratings(1, 0, 0), ratings(0, 2, 0), ratings(0, 0, 3)),
            lastActionDate = "2021-05-04",
            rating = 4.5,
            tone = "positive",
        )
        val bare = place("b").copy(ratings = ratings(0, 0, 0), stages = null, rating = null, tone = "none")
        api.places = { ok(listOf(withStages, bare), etag = "\"v1\"") }
        repository.refreshPlaces()

        assertEquals(listOf(withStages, bare), repository.places.first())
    }

    @Test
    fun `cached reports are shown offline without an error`() = runBlocking {
        api.reports = { ok(PlaceReportsDto("https://parafia.example", listOf(report("easy"))), etag = "\"v1\"") }
        repository.placeReports("a").toList()

        api.reports = { throw IOException("offline") }
        val offline = repository.placeReports("a").toList()

        assertEquals(listOf(PlaceReportsDto("https://parafia.example", listOf(report("easy")))), offline)
    }

    @Test
    fun `reports never fetched fail when offline`() {
        api.reports = { throw IOException("offline") }

        assertThrows(IOException::class.java) { runBlocking { repository.placeReports("a").toList() } }
    }

    @Test
    fun `fresh reports replace the cached ones and 304 emits only the cached copy`() = runBlocking {
        api.reports = { ok(PlaceReportsDto(null, listOf(report("easy"), report("hard"), report("medium"))), "\"v1\"") }
        repository.placeReports("a").toList()

        api.reports = { ok(PlaceReportsDto(null, listOf(report("hard"))), etag = "\"v2\"") }
        val updated = repository.placeReports("a").toList()
        assertEquals(3, updated.first().reports.size)
        assertEquals(listOf(report("hard")), updated.last().reports)

        api.reports = { notModified() }
        val revalidated = repository.placeReports("a").toList()
        assertEquals("\"v2\"", api.lastReportsEtag)
        assertEquals(listOf(PlaceReportsDto(null, listOf(report("hard")))), revalidated)
    }

    @Test
    fun `statistics fall back to the cached copy when the server fails`() = runBlocking {
        api.stats = { ok(stats(placeCount = 10), etag = "\"v1\"") }
        repository.stats().toList()

        api.stats = { serverError() }
        val fallback = repository.stats().toList()

        assertEquals(listOf(10), fallback.map { it.placeCount })
    }

    @Test
    fun `statistics without a cached copy surface the server error`() {
        api.stats = { serverError() }

        assertThrows(retrofit2.HttpException::class.java) { runBlocking { repository.stats().toList() } }
        assertNull(api.lastStatsEtag)
    }

    @Test
    fun `the dataset status is asked again only after a few minutes, and a failure is not remembered`() = runBlocking {
        var now = 0L
        val repository = MapRepository(api, database.cacheDao(), clock = { now })
        assertNull(repository.cachedDatasetStatus)

        api.datasetStatus = { throw IOException("offline") }
        assertThrows(IOException::class.java) { runBlocking { repository.getDatasetStatus() } }
        assertNull(repository.cachedDatasetStatus)

        api.datasetStatus = { status("2026-11-15T10:00:00Z") }
        repository.getDatasetStatus()
        now += 4 * 60_000
        assertEquals("2026-11-15T10:00:00Z", repository.getDatasetStatus().lastCheckedAtUtc)
        assertEquals(2, api.datasetStatusCalls)

        api.datasetStatus = { status("2026-11-15T10:06:00Z") }
        now += 2 * 60_000
        assertEquals("2026-11-15T10:06:00Z", repository.getDatasetStatus().lastCheckedAtUtc)
        assertEquals("2026-11-15T10:06:00Z", repository.cachedDatasetStatus?.lastCheckedAtUtc)
        assertEquals(3, api.datasetStatusCalls)
    }

    private fun status(lastChecked: String) = DatasetStatusDto(
        sourceUrl = "https://mapaapostazji.pl/",
        placeCount = 2,
        lastCheckedAtUtc = lastChecked,
        isFresh = true,
        freshForHours = 24,
    )

    private class FakeApi : AppostazjaApi {
        var places: () -> Response<List<PlaceDto>> = { throw IOException("not configured") }
        var reports: () -> Response<PlaceReportsDto> = { throw IOException("not configured") }
        var stats: () -> Response<StatsDto> = { throw IOException("not configured") }
        var lastPlacesEtag: String? = null
        var lastReportsEtag: String? = null
        var lastStatsEtag: String? = null

        override suspend fun getPlaces(
            minLat: Double?,
            maxLat: Double?,
            minLon: Double?,
            maxLon: Double?,
            ifNoneMatch: String?,
        ): Response<List<PlaceDto>> {
            lastPlacesEtag = ifNoneMatch
            return places()
        }

        override suspend fun getPlaceReports(id: String, ifNoneMatch: String?): Response<PlaceReportsDto> {
            lastReportsEtag = ifNoneMatch
            return reports()
        }

        override suspend fun getStats(ifNoneMatch: String?): Response<StatsDto> {
            lastStatsEtag = ifNoneMatch
            return stats()
        }

        override suspend fun searchPlaces(
            query: String?,
            sort: String?,
            withReports: Boolean,
            offset: Int,
            limit: Int,
        ): PlaceSearchResultDto = throw UnsupportedOperationException()

        override suspend fun getPlace(id: String): PlaceDto = throw UnsupportedOperationException()

        override suspend fun getStreetView(id: String): Response<com.tryniecki.appostazja.api.model.StreetViewDto> =
            throw UnsupportedOperationException()

        var datasetStatus: () -> DatasetStatusDto = { throw IOException("not configured") }
        var datasetStatusCalls = 0

        override suspend fun getDatasetStatus(): DatasetStatusDto {
            datasetStatusCalls++
            return datasetStatus()
        }
    }

    private companion object {
        fun <T> ok(body: T, etag: String): Response<T> = Response.success(body, headersOf("ETag", etag))

        fun <T> notModified(): Response<T> = Response.error("".toResponseBody(), rawResponse(304, "Not Modified"))

        fun <T> serverError(): Response<T> = Response.error("{}".toResponseBody(), rawResponse(500, "Server Error"))

        fun rawResponse(code: Int, message: String) = okhttp3.Response.Builder()
            .code(code)
            .message(message)
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/api/v1/").build())
            .build()

        fun ratings(positive: Int, neutral: Int, negative: Int) =
            RatingBreakdownDto(positive, neutral, negative, positive + neutral + negative)

        fun place(id: String) = PlaceDto(
            id = id,
            name = "Parafia $id",
            address = "Długa 1, 00-001 Warszawa",
            latitude = 52.2,
            longitude = 21.0,
            score = 1.0,
            ratings = ratings(1, 0, 0),
        )

        fun report(rating: String) = ReportDto("apostasy", rating, "2021-03-01", "Komentarz $rating")

        fun stats(placeCount: Int) = StatsDto(
            placeCount = placeCount,
            reportCount = 0,
            commentCount = 0,
            datedReportCount = 0,
            ratings = ratings(0, 0, 0),
            stages = StageRatingsDto(ratings(0, 0, 0), ratings(0, 0, 0), ratings(0, 0, 0)),
            placeTones = ToneCountsDto(0, 0, 0, 0),
            years = emptyList(),
            topCities = emptyList(),
        )
    }
}
