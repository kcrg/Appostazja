package com.tryniecki.appostazja.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tryniecki.appostazja.AppContainer
import com.tryniecki.appostazja.ui.declaration.DeclarationRoute
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
    val motion = MaterialTheme.motionScheme

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            ShortNavigationBar {
                AppDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelResId)
                    ShortNavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = {
                            if (currentDestination != destination) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
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
        val mapVisible = currentDestination == AppDestination.MAP
        Box(contentModifier) {
            PersistentMapHost(visible = mapVisible) {
                MapRoute(container.mapRepository, container.streetViewRepository, themeMode)
            }
        }
        // Covers the map whenever another tab is current, so crossfades between two other tabs never show it.
        val coverAlpha by animateFloatAsState(if (mapVisible) 0f else 1f, motion.fastEffectsSpec(), label = "map-cover")
        val coverColor = MaterialTheme.colorScheme.background
        Box(contentModifier.drawBehind { if (coverAlpha > 0f) drawRect(coverColor, alpha = coverAlpha) })
        NavHost(
            navController = navController,
            startDestination = AppRoute.Map,
            modifier = contentModifier,
            enterTransition = {
                val direction = transitionDirection(initialState.destination, targetState.destination)
                fadeIn(motion.fastEffectsSpec()) +
                    slideInHorizontally(motion.fastSpatialSpec()) { fullWidth ->
                        (fullWidth * 0.08f * direction).toInt()
                    }
            },
            exitTransition = {
                val direction = transitionDirection(initialState.destination, targetState.destination)
                fadeOut(motion.fastEffectsSpec()) +
                    slideOutHorizontally(motion.fastSpatialSpec()) { fullWidth ->
                        (-fullWidth * 0.08f * direction).toInt()
                    }
            },
            popEnterTransition = {
                val direction = transitionDirection(initialState.destination, targetState.destination)
                fadeIn(motion.fastEffectsSpec()) +
                    slideInHorizontally(motion.fastSpatialSpec()) { fullWidth ->
                        (fullWidth * 0.08f * direction).toInt()
                    }
            },
            popExitTransition = {
                val direction = transitionDirection(initialState.destination, targetState.destination)
                fadeOut(motion.fastEffectsSpec()) +
                    slideOutHorizontally(motion.fastSpatialSpec()) { fullWidth ->
                        (-fullWidth * 0.08f * direction).toInt()
                    }
            },
        ) {
            composable<AppRoute.Map> {
                // Drawn by PersistentMapHost underneath.
            }
            composable<AppRoute.Stats> {
                StatsRoute(container.mapRepository)
            }
            composable<AppRoute.Declaration> {
                DeclarationRoute(
                    draftStore = container.secureDraftStore,
                    pdfGenerator = container.pdfGenerator,
                )
            }
            composable<AppRoute.More> {
                MoreScreen(
                    themeMode = themeMode,
                    loadDatasetStatus = container.mapRepository::getDatasetStatus,
                    onThemeModeChange = onThemeModeChange,
                )
            }
        }
    }
}

private fun NavDestination?.topLevelDestination(): AppDestination = when {
    this?.hasRoute<AppRoute.Stats>() == true -> AppDestination.STATS
    this?.hasRoute<AppRoute.Declaration>() == true -> AppDestination.DECLARATION
    this?.hasRoute<AppRoute.More>() == true -> AppDestination.MORE
    else -> AppDestination.MAP
}

private fun transitionDirection(from: NavDestination, to: NavDestination): Int {
    val fromIndex = from.topLevelDestination().ordinal
    val toIndex = to.topLevelDestination().ordinal
    return if (toIndex >= fromIndex) 1 else -1
}
