package com.tryniecki.appostazja.api.model

import kotlinx.serialization.Serializable

@Serializable
data class RatingBreakdownDto(
    val positive: Int,
    val neutral: Int,
    val negative: Int,
    val total: Int,
)

@Serializable
data class PlaceDto(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val score: Double,
    val ratings: RatingBreakdownDto,
)

@Serializable
data class DatasetStatusDto(
    val sourceUrl: String,
    val version: String? = null,
    val placeCount: Int,
    val lastCheckedAtUtc: String? = null,
    val lastChangedAtUtc: String? = null,
    val isFresh: Boolean,
    val freshForHours: Int,
)
