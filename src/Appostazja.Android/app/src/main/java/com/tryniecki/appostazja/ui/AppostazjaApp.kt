package com.tryniecki.appostazja.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.tryniecki.appostazja.ui.navigation.NavMotion
import com.tryniecki.appostazja.ui.navigation.leavingScreenClip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tryniecki.appostazja.AppContainer
import com.tryniecki.appostazja.ui.declaration.DeclarationRoute
import com.tryniecki.appostazja.ui.declaration.DeclarationHistoryRoute
import com.tryniecki.appostazja.ui.declaration.DeclarationPreviewRoute
import androidx.navigation.toRoute
import com.tryniecki.appostazja.ui.map.MapRoute
import com.tryniecki.appostazja.ui.map.PersistentMapHost
import com.tryniecki.appostazja.ui.more.MoreScreen
import com.tryniecki.appostazja.ui.stats.StatsRoute
import com.tryniecki.appostazja.ui.navigation.AppDestination
import com.tryniecki.appostazja.ui.navigation.AppRoute
import com.tryniecki.appostazja.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppostazjaApp(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination.topLevelDestination()
    val tabSwitch = remember { TabSwitch() }
    // Cleared once the switch has been composed and its transitions picked.
    LaunchedEffect(backStackEntry) {
        withFrameNanos { }
        tabSwitch.inProgress = false
    }
    fun switchTab(destination: AppDestination) {
        tabSwitch.inProgress = true
        navController.navigateToTab(destination)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            ShortNavigationBar {
                AppDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelResId)
                    ShortNavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = {
                            if (currentDestination != destination) switchTab(destination)
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconResId),
                                contentDescription = label,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
            // Screens with their own Scaffold must not add the navigation bar inset a second time.
            .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
        // The map lives under the NavHost instead of in it: a NavHost destination is disposed on every tab
        // switch, which recreated the MapView (about 40 ms frames each way). Other tabs are opaque and cover it.
        // It counts as shown while its entry is visible at all, including under a screen being swiped away.
        val visibleEntries by navController.visibleEntries.collectAsState()
        val mapVisible = visibleEntries.any { it.destination.hasRoute<AppRoute.Map>() }
        Box(contentModifier) {
            PersistentMapHost(visible = mapVisible) {
                MapRoute(
                    container.mapRepository,
                    container.streetViewRepository,
                    themeMode,
                    onUseInDeclaration = { place, field ->
                        container.parishHandoff.send(place, field)
                        switchTab(AppDestination.DECLARATION)
                        // The tab may come back on its history or a preview; the picked church belongs on the form.
                        navController.popBackStack<AppRoute.Declaration>(inclusive = false)
                    },
                )
            }
        }
        // Covers the map while two other tabs cross-fade, so it never shows through between them. When the map
        // is one side of a transition, the other side is an opaque screen and the map must show behind it.
        val coverColor = MaterialTheme.colorScheme.background
        Box(contentModifier.drawBehind { if (!mapVisible) drawRect(coverColor) })
        val density = LocalDensity.current
        NavHost(
            navController = navController,
            startDestination = AppRoute.Map,
            modifier = contentModifier,
            enterTransition = {
                if (sameTab(initialState.destination, targetState.destination)) NavMotion.sharedAxisEnter(density)
                else NavMotion.fadeThroughEnter()
            },
            exitTransition = {
                if (sameTab(initialState.destination, targetState.destination)) NavMotion.sharedAxisExit(density)
                else NavMotion.fadeThroughExit()
            },
            // Tapping a tab can also pop (back to the map is a pop), but it is a tab switch, not going back.
            popEnterTransition = {
                if (tabSwitch.inProgress) NavMotion.fadeThroughEnter() else NavMotion.backEnter()
            },
            popExitTransition = {
                if (tabSwitch.inProgress) NavMotion.fadeThroughExit() else NavMotion.backExit()
            },
        ) {
            screen<AppRoute.Map> {
                // Drawn by PersistentMapHost underneath.
            }
            screen<AppRoute.Stats> {
                StatsRoute(container.mapRepository)
            }
            screen<AppRoute.Declaration> {
                DeclarationRoute(
                    draftStore = container.secureDraftStore,
                    pdfGenerator = container.pdfGenerator,
                    archive = container.declarationArchive,
                    onOpenPreview = { navController.navigate(AppRoute.DeclarationPreview(it)) },
                    onOpenHistory = { navController.navigate(AppRoute.DeclarationHistory) },
                    parishHandoff = container.parishHandoff,
                )
            }
            screen<AppRoute.DeclarationHistory> {
                DeclarationHistoryRoute(
                    archive = container.declarationArchive,
                    onOpenPreview = { navController.navigate(AppRoute.DeclarationPreview(it)) },
                    onBack = navController::popBackStack,
                )
            }
            screen<AppRoute.DeclarationPreview> { entry ->
                DeclarationPreviewRoute(
                    archive = container.declarationArchive,
                    declarationId = entry.toRoute<AppRoute.DeclarationPreview>().declarationId,
                    onBack = navController::popBackStack,
                )
            }
            screen<AppRoute.More> {
                MoreScreen(
                    themeMode = themeMode,
                    loadDatasetStatus = container.mapRepository::getDatasetStatus,
                    onThemeModeChange = onThemeModeChange,
                )
            }
        }
    }
}

/** Set while a tab switch is being laid out, so its transitions are told apart from going back. */
private class TabSwitch {
    var inProgress = false
}

/** A destination whose leaving frame gets rounded corners, see [leavingScreenClip]. */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        Box(Modifier.fillMaxSize().then(leavingScreenClip())) { content(entry) }
    }
}

private fun NavDestination?.topLevelDestination(): AppDestination = when {
    this?.hasRoute<AppRoute.Stats>() == true -> AppDestination.STATS
    this?.hasRoute<AppRoute.Declaration>() == true ||
        this?.hasRoute<AppRoute.DeclarationHistory>() == true ||
        this?.hasRoute<AppRoute.DeclarationPreview>() == true -> AppDestination.DECLARATION
    this?.hasRoute<AppRoute.More>() == true -> AppDestination.MORE
    else -> AppDestination.MAP
}

private fun sameTab(from: NavDestination, to: NavDestination) =
    from.topLevelDestination() == to.topLevelDestination()

private fun NavHostController.navigateToTab(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
