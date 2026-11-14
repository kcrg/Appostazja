package com.tryniecki.appostazja.data

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tryniecki.appostazja.api.client.AppostazjaApi
import com.tryniecki.appostazja.api.model.StreetViewDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

sealed interface StreetViewPreview {
    /**
     * A Google panorama to open in the app. [image] is the billed Static API thumbnail; null when Google refused
     * it, and the sheet then offers a plain button instead (the in-app panorama does not need it).
     */
    data class Available(
        val image: ImageBitmap?,
        val panoId: String,
        val heading: Int,
        val copyright: String,
        val date: String?,
    ) : StreetViewPreview

    /**
     * Street View is unavailable on our server (not configured, or Google refusing), but the interactive
     * panorama uses the Maps SDK and still works, so the app offers it without a thumbnail.
     */
    data object PreviewDenied : StreetViewPreview

    /** No street-level panorama close enough to the parish. */
    data object NotAvailable : StreetViewPreview

    /** Network problem or an unexpected response; retrying may help. */
    data object Failed : StreetViewPreview

    /** No Maps API key in this build, so no in-app panorama either. */
    data object Disabled : StreetViewPreview
}

/**
 * Street View previews through our API, which picks a street-level Google panorama and serves its thumbnail (kept
 * on the server, so the Maps key and signing secret never ship in the app). Thumbnails are also cached here over
 * HTTP: their URL changes with the image. Results are kept in memory too, so reopening a parish costs nothing.
 */
class StreetViewRepository(
    private val api: AppostazjaApi,
    private val enabled: Boolean,
    /** The API base URL, which thumbnail URLs are relative to. */
    private val baseUrl: HttpUrl,
    private val images: OkHttpClient,
    private val decode: (ByteArray) -> ImageBitmap? = { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    },
    private val warn: (String, Throwable?) -> Unit = { message, error -> Log.w(TAG, message, error) },
) {
    constructor(api: AppostazjaApi, enabled: Boolean, baseUrl: String, cacheDirectory: File) : this(
        api,
        enabled,
        // Relative URLs resolve under the base path only when it ends with a slash.
        (if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/").toHttpUrl(),
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(cacheDirectory, 16L * 1024 * 1024))
            .build(),
    )

    private val memoryCache = mutableMapOf<String, StreetViewPreview>()

    suspend fun load(placeId: String): StreetViewPreview {
        if (!enabled) return StreetViewPreview.Disabled
        synchronized(memoryCache) { memoryCache[placeId] }?.let { return it }

        val result = withContext(Dispatchers.IO) {
            try {
                fetch(placeId)
            } catch (error: IOException) {
                warn("Street View request failed", error)
                StreetViewPreview.Failed
            }
        }
        if (result is StreetViewPreview.Available || result == StreetViewPreview.NotAvailable) {
            synchronized(memoryCache) {
                if (memoryCache.size >= MEMORY_CACHE_SIZE) memoryCache.remove(memoryCache.keys.first())
                memoryCache[placeId] = result
            }
        }
        return result
    }

    private suspend fun fetch(placeId: String): StreetViewPreview {
        val response = api.getStreetView(placeId)
        return when (response.code()) {
            200 -> response.body()?.let { thumbnail(it) } ?: StreetViewPreview.Failed
            204, 404 -> StreetViewPreview.NotAvailable
            503 -> StreetViewPreview.PreviewDenied
            else -> {
                warn("Street View API HTTP ${response.code()}", null)
                StreetViewPreview.Failed
            }
        }
    }

    private fun thumbnail(panorama: StreetViewDto): StreetViewPreview {
        fun available(image: ImageBitmap?) = StreetViewPreview.Available(
            image = image,
            panoId = panorama.panoId,
            heading = panorama.heading,
            copyright = panorama.copyright,
            date = panorama.date,
        )

        val url = baseUrl.resolve(panorama.thumbnailUrl) ?: return StreetViewPreview.Failed
        images.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val isImage = response.body.contentType()?.type == "image"
            if (!response.isSuccessful || !isImage) {
                // Only the status code says what happened.
                val body = if (isImage) "" else response.body.string()
                return when (classifyImageFailure(response.code, body)) {
                    ImageFailure.ThumbnailRefused -> {
                        warn("Street View image refused, HTTP ${response.code}: ${body.take(300)}", null)
                        available(image = null)
                    }
                    ImageFailure.NoPanorama -> StreetViewPreview.NotAvailable
                    ImageFailure.Other -> {
                        warn("Street View image HTTP ${response.code}: ${body.take(300)}", null)
                        StreetViewPreview.Failed
                    }
                }
            }
            val image = decode(response.body.bytes()) ?: return StreetViewPreview.Failed
            return available(image)
        }
    }

    private companion object {
        private const val TAG = "StreetView"
        const val MEMORY_CACHE_SIZE = 24
    }
}

internal enum class ImageFailure { ThumbnailRefused, NoPanorama, Other }

/**
 * Why our API did not return the thumbnail. 503 means Google refused it (billing, API restrictions, signature,
 * quota) and the server has no copy: that only takes the thumbnail away, as the in-app panorama does not depend on
 * the Static API, so the sheet still offers it. Only network and server errors (502 included) are worth a retry.
 */
internal fun classifyImageFailure(code: Int, body: String): ImageFailure {
    val text = body.lowercase()
    return when {
        code == 503 || code == 403 || code == 429 || "quota" in text || "over_query_limit" in text -> ImageFailure.ThumbnailRefused
        code == 404 -> ImageFailure.NoPanorama
        else -> ImageFailure.Other
    }
}

/** Initial compass bearing from the panorama to the parish, so the camera faces the church. */
internal fun bearing(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Int {
    val phi1 = Math.toRadians(fromLat)
    val phi2 = Math.toRadians(toLat)
    val deltaLambda = Math.toRadians(toLng - fromLng)
    val y = sin(deltaLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
    return (Math.toDegrees(atan2(y, x)) + 360.0).roundToInt() % 360
}
