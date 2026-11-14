package com.tryniecki.appostazja.data

import androidx.compose.ui.graphics.ImageBitmap
import com.tryniecki.appostazja.api.client.AppostazjaApi
import com.tryniecki.appostazja.api.model.DatasetStatusDto
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.api.model.PlaceReportsDto
import com.tryniecki.appostazja.api.model.PlaceSearchResultDto
import com.tryniecki.appostazja.api.model.StatsDto
import com.tryniecki.appostazja.api.model.StreetViewDto
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StreetViewRepositoryTest {
    private val api = FakeApi()
    private val google = FakeGoogle()
    private val decoded = mutableListOf<ByteArray>()
    private val repository = StreetViewRepository(
        api = api,
        enabled = true,
        baseUrl = "https://api.example/appostazja/".toHttpUrl(),
        images = OkHttpClient.Builder().addInterceptor { google.respond(it.request()) }.build(),
        decode = { bytes -> decoded += bytes; FakeBitmap },
        warn = { _, _ -> },
    )

    @Test
    fun `a panorama with its thumbnail is loaded once and then served from memory`() = runBlocking {
        api.streetView = { ok(panorama) }
        google.image = image(200)

        val first = repository.load("mariacki") as StreetViewPreview.Available
        val again = repository.load("mariacki")

        assertNotNull(first.image)
        assertEquals("pano-1", first.panoId)
        assertEquals(first, again)
        assertEquals(1, api.calls)
        assertEquals(listOf("https://api.example/appostazja/api/v1/places/mariacki/street-view/thumbnail?v=abc"), google.requested)
    }

    @Test
    fun `a thumbnail Google refused still offers the panorama`() = runBlocking {
        // Our API answers 503 when Google refuses the image and it has no copy.
        api.streetView = { ok(panorama) }
        google.image = image(503)

        val preview = repository.load("mariacki") as StreetViewPreview.Available

        assertNull(preview.image)
        assertEquals("pano-1", preview.panoId)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `server answers map to no panorama, the location fallback, or a retry`() = runBlocking {
        api.streetView = { retrofit2.Response.success<StreetViewDto>(204, null) }
        assertEquals(StreetViewPreview.NotAvailable, repository.load("none"))

        api.streetView = { error(503) }
        assertEquals(StreetViewPreview.PreviewDenied, repository.load("unconfigured"))

        api.streetView = { error(500) }
        assertEquals(StreetViewPreview.Failed, repository.load("broken"))
    }

    @Test
    fun `failures are not remembered, so a retry asks again`() = runBlocking {
        api.streetView = { throw IOException("offline") }
        assertEquals(StreetViewPreview.Failed, repository.load("mariacki"))

        api.streetView = { ok(panorama) }
        google.image = image(200)
        assertTrue(repository.load("mariacki") is StreetViewPreview.Available)
        assertEquals(2, api.calls)
    }

    @Test
    fun `refusals still offer the panorama while server errors ask for a retry`() {
        assertEquals(ImageFailure.ThumbnailRefused, classifyImageFailure(503, ""))
        assertEquals(ImageFailure.ThumbnailRefused, classifyImageFailure(403, ""))
        assertEquals(ImageFailure.ThumbnailRefused, classifyImageFailure(429, ""))
        assertEquals(ImageFailure.ThumbnailRefused, classifyImageFailure(400, "You have exceeded your daily request quota"))
        assertEquals(ImageFailure.NoPanorama, classifyImageFailure(404, ""))
        assertEquals(ImageFailure.Other, classifyImageFailure(502, ""))
        assertEquals(ImageFailure.Other, classifyImageFailure(500, ""))
    }

    @Test
    fun `bearing points from panorama towards the parish`() {
        assertEquals(0, bearing(52.0, 19.0, 52.001, 19.0))
        assertEquals(90, bearing(52.0, 19.0, 52.0, 19.001))
        assertEquals(180, bearing(52.001, 19.0, 52.0, 19.0))
        assertEquals(270, bearing(52.0, 19.001, 52.0, 19.0))
    }

    private val panorama = StreetViewDto(
        panoId = "pano-1",
        heading = 120,
        copyright = "© 2026 Google",
        date = "2025-07",
        thumbnailUrl = "api/v1/places/mariacki/street-view/thumbnail?v=abc",
    )

    private fun ok(body: StreetViewDto) = retrofit2.Response.success(body)

    private fun error(code: Int): retrofit2.Response<StreetViewDto> =
        retrofit2.Response.error(code, "{}".toResponseBody("application/json".toMediaType()))

    private class FakeGoogle {
        var image: (Request) -> Response = { throw IOException("not configured") }
        val requested = mutableListOf<String>()

        fun respond(request: Request): Response {
            requested += request.url.toString()
            return image(request)
        }
    }

    private fun image(code: Int) = { request: Request ->
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(byteArrayOf(1, 2, 3).toResponseBody("image/png".toMediaType()))
            .build()
    }

    private class FakeApi : AppostazjaApi {
        var streetView: () -> retrofit2.Response<StreetViewDto> = { throw IOException("not configured") }
        var calls = 0

        override suspend fun getStreetView(id: String): retrofit2.Response<StreetViewDto> {
            calls++
            return streetView()
        }

        override suspend fun getPlaces(
            minLat: Double?,
            maxLat: Double?,
            minLon: Double?,
            maxLon: Double?,
            ifNoneMatch: String?,
        ): retrofit2.Response<List<PlaceDto>> = throw UnsupportedOperationException()

        override suspend fun searchPlaces(
            query: String?,
            sort: String?,
            withReports: Boolean,
            offset: Int,
            limit: Int,
        ): PlaceSearchResultDto = throw UnsupportedOperationException()

        override suspend fun getPlace(id: String): PlaceDto = throw UnsupportedOperationException()

        override suspend fun getPlaceReports(id: String, ifNoneMatch: String?): retrofit2.Response<PlaceReportsDto> =
            throw UnsupportedOperationException()

        override suspend fun getStats(ifNoneMatch: String?): retrofit2.Response<StatsDto> =
            throw UnsupportedOperationException()

        override suspend fun getDatasetStatus(): DatasetStatusDto = throw UnsupportedOperationException()
    }
}

/** Stands in for a decoded thumbnail; real bitmaps need Android. */
private object FakeBitmap : ImageBitmap {
    override val width = 1
    override val height = 1
    override val config = androidx.compose.ui.graphics.ImageBitmapConfig.Argb8888
    override val hasAlpha = false
    override val colorSpace = androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb

    override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) = Unit

    override fun prepareToDraw() = Unit
}
