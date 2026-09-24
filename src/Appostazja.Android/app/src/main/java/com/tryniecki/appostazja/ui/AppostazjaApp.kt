package com.tryniecki.appostazja.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.tryniecki.appostazja.ui.more.MoreScreen
import com.tryniecki.appostazja.ui.navigation.AppDestination
import com.tryniecki.appostazja.ui.navigation.AppRoute
import com.tryniecki.appostazja.ui.theme.ThemeMode

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
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelResId)
                    NavigationBarItem(
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
        NavHost(
            navController = navController,
            startDestination = AppRoute.Map,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
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
                MapRoute(container.mapRepository)
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
                    onThemeModeChange = onThemeModeChange,
                )
            }
        }
    }
}

private fun NavDestination?.topLevelDestination(): AppDestination = when {
    this?.hasRoute<AppRoute.Declaration>() == true -> AppDestination.DECLARATION
    this?.hasRoute<AppRoute.More>() == true -> AppDestination.MORE
    else -> AppDestination.MAP
}

private fun transitionDirection(from: NavDestination, to: NavDestination): Int {
    val fromIndex = from.topLevelDestination().ordinal
    val toIndex = to.topLevelDestination().ordinal
    return if (toIndex >= fromIndex) 1 else -1
}
