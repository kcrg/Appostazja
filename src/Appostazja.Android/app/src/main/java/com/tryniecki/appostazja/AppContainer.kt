package com.tryniecki.appostazja

import android.content.Context
import com.tryniecki.appostazja.api.client.AppostazjaApiClientFactory
import com.tryniecki.appostazja.data.MapRepository
import com.tryniecki.appostazja.data.NewsRepository
import com.tryniecki.appostazja.data.ReadArticles
import com.tryniecki.appostazja.data.RecentSearches
import com.tryniecki.appostazja.data.StreetViewRepository
import com.tryniecki.appostazja.data.local.AppostazjaDatabase
import com.tryniecki.appostazja.pdf.ApostasyPdfGenerator
import com.tryniecki.appostazja.pdf.DeclarationArchive
import com.tryniecki.appostazja.security.SecureDraftStore
import com.tryniecki.appostazja.ui.declaration.ParishHandoff

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    private val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppostazjaDatabase.create(applicationContext) }

    // No HTTP cache: Room keeps the data and sends its own ETags, so 304s reach the repository.
    private val api by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppostazjaApiClientFactory.create(BuildConfig.APPOSTAZJA_API_BASE_URL)
    }
    val mapRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { MapRepository(api, database.cacheDao()) }
    val streetViewRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        StreetViewRepository(
            api,
            // The in-app panorama needs the Maps SDK key; thumbnails come from our API.
            enabled = BuildConfig.MAPS_API_KEY.isNotBlank(),
            baseUrl = BuildConfig.APPOSTAZJA_API_BASE_URL,
            cacheDirectory = applicationContext.cacheDir.resolve("street-view-http"),
        )
    }
    val newsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NewsRepository(
            feedUrl = applicationContext.getString(R.string.news_feed_url),
            cacheDirectory = applicationContext.cacheDir.resolve("news-http"),
        )
    }
    val readArticles by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ReadArticles(applicationContext) }
    val recentSearches by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RecentSearches(applicationContext) }
    val secureDraftStore = SecureDraftStore(applicationContext)
    val pdfGenerator = ApostasyPdfGenerator()
    val declarationArchive by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { DeclarationArchive(applicationContext) }
    val parishHandoff = ParishHandoff()
}
