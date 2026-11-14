package com.tryniecki.appostazja.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CacheDao {
    @Query("SELECT * FROM places ORDER BY position")
    abstract fun observePlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT COUNT(*) FROM places")
    abstract suspend fun placeCount(): Int

    /** Swaps the whole dataset in one transaction, so observers never see a half-written map. */
    @Transaction
    open suspend fun replacePlaces(places: List<PlaceEntity>, response: CachedResponseEntity) {
        deleteAllPlaces()
        insertPlaces(places)
        upsertResponse(response)
    }

    @Query("SELECT * FROM place_details WHERE placeId = :placeId")
    abstract suspend fun placeDetails(placeId: String): PlaceDetailsEntity?

    @Query("SELECT * FROM reports WHERE placeId = :placeId ORDER BY position")
    abstract suspend fun reports(placeId: String): List<ReportEntity>

    @Transaction
    open suspend fun replaceReports(details: PlaceDetailsEntity, reports: List<ReportEntity>) {
        deleteReports(details.placeId)
        upsertPlaceDetails(details)
        insertReports(reports)
    }

    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    abstract suspend fun response(key: String): CachedResponseEntity?

    // REPLACE instead of @Upsert: plain rows without foreign keys, and no exception-driven insert-then-update.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertResponse(response: CachedResponseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPlaceDetails(details: PlaceDetailsEntity)

    @Query("DELETE FROM places")
    abstract suspend fun deleteAllPlaces()

    @Insert
    abstract suspend fun insertPlaces(places: List<PlaceEntity>)

    @Query("DELETE FROM reports WHERE placeId = :placeId")
    abstract suspend fun deleteReports(placeId: String)

    @Insert
    abstract suspend fun insertReports(reports: List<ReportEntity>)
}
