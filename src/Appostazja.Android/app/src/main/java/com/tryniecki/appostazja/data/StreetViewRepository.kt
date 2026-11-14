package com.tryniecki.appostazja.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

sealed interface StreetViewPreview {
    data class Available(
        val image: ImageBitmap,
        val panoId: String,
        val heading: Int,
        val copyright: String,
        val date: String?,
    ) : StreetViewPreview

    /**
     * The Static API refused the preview (billing or API not enabled), but the interactive panorama uses the
     * Maps SDK and still works, so the app offers it without a thumbnail.
     */
    data object PreviewDenied : StreetViewPreview

    /** No outdoor panorama close enough to the parish. */
    data object NotAvailable : StreetViewPreview

    /** The free Google Maps Platform quota has been used up. */
    data object QuotaExceeded : StreetViewPreview

    /** Network problem or an unexpected response; retrying may help. */
    data object Failed : StreetViewPreview

    /** No API key configured for this build. */
    data object Disabled : StreetViewPreview
}

/**
 * Loads Street View previews through the Street View Static API.
 *
 * The metadata endpoint is free and checked first, so the billed image request is only made when a
 * panorama exists. Results are cached in memory and over HTTP so reopening a parish does not spend quota.
 */
class StreetViewRepository(
    context: Context,
    private val apiKey: String,
    cacheDirectory: File,
) {
    private val packageName = context.packageName
    private val certificateSha1 by lazy { signingCertificateSha1(context) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(cacheDirectory, 16L * 1024 * 1024))
        .build()
    private val memoryCache = LruCache<String, StreetViewPreview>(24)

    @Volatile
    private var quotaBlockedUntil = 0L

    /**
     * @param userRetry true when the user explicitly asked to try again, which skips the quota back-off
     * that otherwise keeps automatic loads from hitting an exhausted API.
     */
    suspend fun load(
        placeId: String,
        latitude: Double,
        longitude: Double,
        userRetry: Boolean = false,
    ): StreetViewPreview {
        if (apiKey.isBlank()) return StreetViewPreview.Disabled
        memoryCache.get(placeId)?.let { return it }
        if (!userRetry && SystemClock.elapsedRealtime() < quotaBlockedUntil) return StreetViewPreview.QuotaExceeded

        val result = withContext(Dispatchers.IO) {
            try {
                fetch(latitude, longitude)
            } catch (error: IOException) {
                Log.w(TAG, "Street View request failed", error)
                StreetViewPreview.Failed
            }
        }
        when (result) {
            is StreetViewPreview.Available, StreetViewPreview.NotAvailable -> memoryCache.put(placeId, result)
            StreetViewPreview.QuotaExceeded -> quotaBlockedUntil = SystemClock.elapsedRealtime() + QUOTA_BACKOFF_MS
            else -> Unit
        }
        return result
    }

    private fun fetch(latitude: Double, longitude: Double): StreetViewPreview {
        val metadataUrl = baseUrl("metadata")
            .addQueryParameter("location", "$latitude,$longitude")
            .addQueryParameter("radius", SEARCH_RADIUS_METERS.toString())
            .addQueryParameter("source", "outdoor")
            .addQueryParameter("key", apiKey)
            .build()
        val metadata = execute(metadataUrl) { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                return@execute StreetViewMetadata(status = classifyHttpError(response.code, body))
            }
            json.decodeFromString<StreetViewMetadata>(body)
        }

        when (metadata.status) {
            "OK" -> Unit
            "ZERO_RESULTS", "NOT_FOUND" -> return StreetViewPreview.NotAvailable
            "OVER_QUERY_LIMIT" -> return StreetViewPreview.QuotaExceeded
            // Billing or API configuration problem on the Static API; the in-app panorama does not depend on it.
            "REQUEST_DENIED" -> {
                Log.w(TAG, "Street View request denied: ${metadata.errorMessage}")
                return StreetViewPreview.PreviewDenied
            }
            else -> {
                Log.w(TAG, "Street View metadata status ${metadata.status}: ${metadata.errorMessage}")
                return StreetViewPreview.Failed
            }
        }
        val panoId = metadata.panoId ?: return StreetViewPreview.NotAvailable
        val heading = metadata.location
            ?.let { bearing(it.lat, it.lng, latitude, longitude) }
            ?: 0

        val imageUrl = baseUrl()
            .addQueryParameter("size", "640x400")
            .addQueryParameter("pano", panoId)
            .addQueryParameter("heading", heading.toString())
            .addQueryParameter("fov", "80")
            .addQueryParameter("pitch", "8")
            .addQueryParameter("return_error_code", "true")
            .addQueryParameter("key", apiKey)
            .build()
        return execute(imageUrl) { response ->
            val isImage = response.body.contentType()?.type == "image"
            if (!response.isSuccessful || !isImage) {
                val body = if (isImage) "" else response.body.string()
                return@execute when (classifyHttpError(response.code, body)) {
                    "OVER_QUERY_LIMIT" -> StreetViewPreview.QuotaExceeded
                    "ZERO_RESULTS" -> StreetViewPreview.NotAvailable
                    else -> {
                        Log.w(TAG, "Street View image HTTP ${response.code}: ${body.take(300)}")
                        StreetViewPreview.Failed
                    }
                }
            }
            val bytes = response.body.bytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@execute StreetViewPreview.Failed
            StreetViewPreview.Available(
                image = bitmap.asImageBitmap(),
                panoId = panoId,
                heading = heading,
                copyright = metadata.copyright ?: "© Google",
                date = metadata.date,
            )
        }
    }

    private fun <T> execute(url: HttpUrl, handle: (Response) -> T): T {
        val request = Request.Builder()
            .url(url)
            // Lets the request pass an API key restricted to this Android app.
            .header("X-Android-Package", packageName)
            .apply { certificateSha1?.let { header("X-Android-Cert", it) } }
            .build()
        return client.newCall(request).execute().use(handle)
    }

    private fun baseUrl(path: String? = null) = STREET_VIEW_URL.toHttpUrl().newBuilder()
        .apply { if (path != null) addPathSegment(path) }

    private companion object {
        const val TAG = "StreetView"
        const val STREET_VIEW_URL = "https://maps.googleapis.com/maps/api/streetview"
        const val SEARCH_RADIUS_METERS = 120
        const val QUOTA_BACKOFF_MS = 10 * 60 * 1000L

        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Maps an HTTP failure to a metadata-style status so both endpoints share one decision table. */
internal fun classifyHttpError(code: Int, body: String): String {
    val text = body.lowercase()
    return when {
        code == 429 -> "OVER_QUERY_LIMIT"
        "quota" in text || "exceeded" in text || "rate limit" in text || "over_query_limit" in text ->
            "OVER_QUERY_LIMIT"
        code == 404 -> "ZERO_RESULTS"
        else -> "HTTP_$code"
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

private fun signingCertificateSha1(context: Context): String? = runCatching {
    val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_SIGNING_CERTIFICATES,
    )
    val signature = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
    MessageDigest.getInstance("SHA-1")
        .digest(signature.toByteArray())
        .joinToString("") { "%02X".format(it) }
}.getOrNull()

@Serializable
private data class StreetViewMetadata(
    val status: String,
    @SerialName("pano_id") val panoId: String? = null,
    val location: StreetViewLocation? = null,
    val copyright: String? = null,
    val date: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
private data class StreetViewLocation(val lat: Double, val lng: Double)
