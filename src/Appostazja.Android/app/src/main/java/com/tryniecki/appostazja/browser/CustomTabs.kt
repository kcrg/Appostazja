package com.tryniecki.appostazja.browser

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler

fun openCustomTab(
    context: Context,
    uriHandler: UriHandler,
    url: String,
    colors: CustomTabColorSchemeParams? = null,
) {
    val builder = CustomTabsIntent.Builder()
    if (colors != null) builder.setDefaultColorSchemeParams(colors)
    val intent = builder
        .setShowTitle(true)
        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        .build()

    try {
        intent.launchUrl(context, url.toUri())
    } catch (_: ActivityNotFoundException) {
        // No browser that supports Custom Tabs; with no browser at all the link cannot be opened.
        try {
            uriHandler.openUri(url)
        } catch (_: IllegalArgumentException) {
        }
    }
}

@Composable
fun rememberOpenCustomTab(): (String) -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val colors = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(scheme.surfaceContainer.toArgb())
        .setNavigationBarColor(scheme.surface.toArgb())
        .setNavigationBarDividerColor(scheme.outlineVariant.toArgb())
        .build()

    return { url -> openCustomTab(context, uriHandler, url, colors) }
}
