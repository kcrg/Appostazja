package com.tryniecki.appostazja.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

/** Offline copy of what the API serves: the map dataset, reports of opened places and statistics. */
@Database(
    entities = [PlaceEntity::class, PlaceDetailsEntity::class, ReportEntity::class, CachedResponseEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppostazjaDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        fun create(context: Context): AppostazjaDatabase =
            Room.databaseBuilder<AppostazjaDatabase>(context.applicationContext, "appostazja-cache.db")
                // The platform SQLite keeps the APK small; the bundled driver would add a native library per ABI.
                .setDriver(AndroidSQLiteDriver())
                // Everything here can be downloaded again, so a schema change simply starts from empty.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
