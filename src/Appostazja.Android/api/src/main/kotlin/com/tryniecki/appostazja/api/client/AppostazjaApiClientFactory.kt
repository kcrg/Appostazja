package com.tryniecki.appostazja.api.client

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

private val AppostazjaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

object AppostazjaApiClientFactory {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    fun create(baseUrl: String): AppostazjaApi = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(baseUrl))
        .client(httpClient)
        .addConverterFactory(AppostazjaJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create<AppostazjaApi>()

    internal fun normalizeBaseUrl(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        require(root.isNotEmpty()) { "baseUrl cannot be blank." }
        return if (root.endsWith("/api/v1", ignoreCase = true)) "$root/" else "$root/api/v1/"
    }
}
