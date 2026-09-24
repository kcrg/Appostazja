package com.tryniecki.appostazja

import android.content.Context
import com.tryniecki.appostazja.api.client.AppostazjaApiClientFactory
import com.tryniecki.appostazja.data.MapRepository
import com.tryniecki.appostazja.pdf.ApostasyPdfGenerator
import com.tryniecki.appostazja.security.SecureDraftStore

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val mapRepository = MapRepository(
        AppostazjaApiClientFactory.create(BuildConfig.APPOSTAZJA_API_BASE_URL),
    )
    val secureDraftStore = SecureDraftStore(applicationContext)
    val pdfGenerator = ApostasyPdfGenerator()
}
