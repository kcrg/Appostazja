package com.tryniecki.appostazja.data.local

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.api.model.RatingBreakdownDto
import com.tryniecki.appostazja.api.model.StageRatingsDto

/** One place of the map dataset; [position] keeps the API order. */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val score: Double,
    @Embedded(prefix = "ratings_") val ratings: RatingColumns,
    @Embedded(prefix = "before_") val beforeApostasy: RatingColumns?,
    @Embedded(prefix = "apostasy_") val apostasy: RatingColumns?,
    @Embedded(prefix = "after_") val afterApostasy: RatingColumns?,
    val lastActionDate: String?,
    val rating: Double?,
    val tone: String?,
)

data class RatingColumns(val positive: Int, val neutral: Int, val negative: Int, val total: Int)

/** Marks that the reports of a place were fetched (even when there are none) and with which ETag. */
@Entity(tableName = "place_details")
data class PlaceDetailsEntity(
    @PrimaryKey val placeId: String,
    val website: String?,
    val etag: String?,
    val syncedAtMillis: Long,
)

@Entity(tableName = "reports", primaryKeys = ["placeId", "position"])
data class ReportEntity(
    val placeId: String,
    val position: Int,
    val stage: String,
    val rating: String,
    val date: String?,
    val comment: String?,
)

/**
 * A cached response identified by [key]: the ETag to revalidate with and, for documents kept whole, the JSON
 * [body]. The places dataset stores only its ETag here; its rows live in [PlaceEntity].
 */
@Entity(tableName = "cached_responses")
data class CachedResponseEntity(
    @PrimaryKey val key: String,
    val etag: String?,
    val body: String?,
    val syncedAtMillis: Long,
)

internal fun PlaceDto.toEntity(position: Int) = PlaceEntity(
    id = id,
    position = position,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    score = score,
    ratings = ratings.toColumns(),
    beforeApostasy = stages?.beforeApostasy?.toColumns(),
    apostasy = stages?.apostasy?.toColumns(),
    afterApostasy = stages?.afterApostasy?.toColumns(),
    lastActionDate = lastActionDate,
    rating = rating,
    tone = tone,
)

internal fun PlaceEntity.toDto(): PlaceDto {
    val stages = if (beforeApostasy != null && apostasy != null && afterApostasy != null) {
        StageRatingsDto(beforeApostasy.toDto(), apostasy.toDto(), afterApostasy.toDto())
    } else {
        null
    }
    return PlaceDto(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        score = score,
        ratings = ratings.toDto(),
        stages = stages,
        lastActionDate = lastActionDate,
        rating = rating,
        tone = tone,
    )
}

private fun RatingBreakdownDto.toColumns() = RatingColumns(positive, neutral, negative, total)

private fun RatingColumns.toDto() = RatingBreakdownDto(positive, neutral, negative, total)
