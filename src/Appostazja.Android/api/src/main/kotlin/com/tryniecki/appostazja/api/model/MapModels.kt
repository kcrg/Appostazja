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
    /** Ratings split by the stage a report describes. Null when talking to an older API. */
    val stages: StageRatingsDto? = null,
    /** Latest action date (yyyy-MM-dd) mentioned in the place's reports. */
    val lastActionDate: String? = null,
    /** Score on a 0-5 scale for display; null without reports. */
    val rating: Double? = null,
    /** positive, mixed, negative or none; decides the pin colour. */
    val tone: String? = null,
)

@Serializable
data class PlaceSearchResultDto(
    val total: Int,
    val offset: Int,
    val limit: Int,
    val items: List<PlaceDto>,
)

@Serializable
data class StageRatingsDto(
    val beforeApostasy: RatingBreakdownDto,
    val apostasy: RatingBreakdownDto,
    val afterApostasy: RatingBreakdownDto,
)

@Serializable
data class ReportDto(
    val stage: String,
    val rating: String,
    val date: String? = null,
    val comment: String? = null,
)

@Serializable
data class PlaceReportsDto(
    val website: String? = null,
    val reports: List<ReportDto>,
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
    val coordinatesUrl: String? = null,
    val lastImport: ImportStatusDto? = null,
)

@Serializable
data class ImportStatusDto(
    val lastAttemptAtUtc: String? = null,
    val outcome: String? = null,
    val error: String? = null,
    val skippedRows: Int = 0,
    val unmatchedReports: Int = 0,
)

@Serializable
data class StatsDto(
    val placeCount: Int,
    val reportCount: Int,
    val commentCount: Int,
    val datedReportCount: Int,
    val ratings: RatingBreakdownDto,
    val stages: StageRatingsDto,
    val placeTones: ToneCountsDto,
    /** Oldest first; a period spans several years when [PeriodStatsDto.fromYear] differs from [PeriodStatsDto.toYear]. */
    val years: List<PeriodStatsDto>,
    val topCities: List<CityStatsDto>,
)

@Serializable
data class ToneCountsDto(val positive: Int, val mixed: Int, val negative: Int, val none: Int)

@Serializable
data class PeriodStatsDto(val fromYear: Int, val toYear: Int, val ratings: RatingBreakdownDto)

@Serializable
data class CityStatsDto(
    val city: String,
    val placeCount: Int,
    val ratings: RatingBreakdownDto,
    val rating: Double? = null,
)
