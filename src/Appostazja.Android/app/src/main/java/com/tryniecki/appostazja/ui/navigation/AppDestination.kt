package com.tryniecki.appostazja.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tryniecki.appostazja.R
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Map : AppRoute
    @Serializable data object Declaration : AppRoute
    @Serializable data object More : AppRoute
}

enum class AppDestination(
    @StringRes val labelResId: Int,
    @DrawableRes val iconResId: Int,
    val route: AppRoute,
) {
    MAP(
        R.string.nav_map,
        com.composables.icons.tabler.outline.R.drawable.tabler_ic_map_2_outline,
        AppRoute.Map,
    ),
    DECLARATION(
        R.string.nav_declaration,
        com.composables.icons.tabler.outline.R.drawable.tabler_ic_file_description_outline,
        AppRoute.Declaration,
    ),
    MORE(
        R.string.nav_more,
        com.composables.icons.tabler.outline.R.drawable.tabler_ic_dots_outline,
        AppRoute.More,
    ),
}
