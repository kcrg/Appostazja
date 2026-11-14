package com.tryniecki.appostazja

import android.content.Context
import com.tryniecki.appostazja.api.client.AppostazjaApiClientFactory
import com.tryniecki.appostazja.data.MapRepository
import com.tryniecki.appostazja.data.StreetViewRepository
import com.tryniecki.appostazja.data.local.AppostazjaDatabase
import com.tryniecki.appostazja.pdf.ApostasyPdfGenerator
import com.tryniecki.appostazja.security.SecureDraftStore

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
            // The in-app panorama needs the Maps SDK key; thumbnails are signed by our API.
            enabled = BuildConfig.MAPS_API_KEY.isNotBlank(),
            cacheDirectory = applicationContext.cacheDir.resolve("street-view-http"),
        )
    }
    val secureDraftStore = SecureDraftStore(applicationContext)
    val pdfGenerator = ApostasyPdfGenerator()
}
