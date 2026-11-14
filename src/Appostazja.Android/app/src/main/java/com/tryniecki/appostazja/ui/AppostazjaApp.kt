package com.tryniecki.appostazja.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import com.tryniecki.appostazja.ui.components.LocalNavAnimatedScope
import com.tryniecki.appostazja.ui.components.LocalSharedTransitionScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.tryniecki.appostazja.ui.navigation.NavMotion
import com.tryniecki.appostazja.ui.navigation.backNavigationDecoration
import com.tryniecki.appostazja.ui.navigation.NavDirection
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
    val navDirection = remember { NavDirection() }
    var mapEntryComposed by remember { mutableStateOf(false) }
    // True for the length of a tab switch animation; meanwhile NavHost may report the map entry as visible
    // although the map is not part of the switch.
    var tabSwitches by remember { mutableIntStateOf(0) }
    var tabSwitchRunning by remember { mutableStateOf(false) }
    LaunchedEffect(tabSwitches) {
        if (tabSwitches == 0) return@LaunchedEffect
        // Over once NavHost shows a single screen again; the timeout covers a switch that animates nothing.
        withTimeoutOrNull(TAB_SWITCH_TIMEOUT_MS) {
            withTimeoutOrNull(TAB_SWITCH_START_MS) { navController.visibleEntries.first { it.size > 1 } }
            navController.visibleEntries.first { it.size <= 1 }
            // Leaving the map, its entry goes a moment later; until then it must not count as going back.
            if (navController.currentDestination?.hasRoute<AppRoute.Map>() != true) {
                snapshotFlow { mapEntryComposed }.first { !it }
            }
        }
        tabSwitchRunning = false
    }
    fun switchTab(destination: AppDestination) {
        tabSwitch.inProgress = true
        // Set before navigating: the very first frame of the switch must already keep the map covered.
        tabSwitchRunning = true
        tabSwitches++
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
        // Going back to the map (a back gesture or button from another tab): the map shows under the screen leaving.
        // NavHost composes the map entry as soon as a back gesture starts but lists it in visibleEntries only once
        // the gesture ends, so the entry reports itself. A tab switch away from the map composes it too, as the
        // screen leaving; that is not going back.
        val backToMap = !tabSwitchRunning && currentDestination != AppDestination.MAP && mapEntryComposed
        // Covers the map whenever another tab is shown, including while two other tabs cross-fade. Leaving the map
        // it fades in with the incoming tab; returning to it, it goes at once and the leaving tab fades over the map.
        val coverHidden = currentDestination == AppDestination.MAP || backToMap
        val cover = remember { Animatable(if (coverHidden) 0f else 1f) }
        LaunchedEffect(coverHidden) {
            if (coverHidden) cover.snapTo(0f)
            else cover.animateTo(1f, tween(NavMotion.TabFadeIn, delayMillis = NavMotion.TabFadeInDelay, easing = LinearEasing))
        }
        val mapVisible = coverHidden || cover.value < 1f
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
        val coverColor = MaterialTheme.colorScheme.background
        Box(contentModifier.drawBehind { if (cover.value > 0f) drawRect(coverColor, alpha = cover.value) })
        val density = LocalDensity.current
        // Shared elements: the declaration FAB morphs into the history screen and back.
        SharedTransitionLayout(contentModifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.Map,
            modifier = Modifier.fillMaxSize(),
            // Between a FAB and the screen it opens the container transform is the whole animation: the screen
            // underneath stays in place and the opened one appears only through the morphing container.
            enterTransition = {
                navDirection.goingBack = false
                if (isFabMorph(initialState.destination, targetState.destination)) EnterTransition.None
                else if (sameTab(initialState.destination, targetState.destination)) NavMotion.sharedAxisEnter(density)
                else NavMotion.fadeThroughEnter()
            },
            exitTransition = {
                navDirection.goingBack = false
                if (isFabMorph(initialState.destination, targetState.destination)) ExitTransition.KeepUntilTransitionsFinished
                else if (sameTab(initialState.destination, targetState.destination)) NavMotion.sharedAxisExit(density)
                else NavMotion.fadeThroughExit()
            },
            // Tapping a tab can also pop (back to the map is a pop), but it is a tab switch, not going back.
            popEnterTransition = {
                navDirection.goingBack = !tabSwitch.inProgress && !isFabMorph(initialState.destination, targetState.destination)
                if (isFabMorph(initialState.destination, targetState.destination)) EnterTransition.None
                else if (tabSwitch.inProgress) NavMotion.fadeThroughEnter() else NavMotion.backEnter()
            },
            popExitTransition = {
                navDirection.goingBack = !tabSwitch.inProgress && !isFabMorph(initialState.destination, targetState.destination)
                if (isFabMorph(initialState.destination, targetState.destination)) ExitTransition.KeepUntilTransitionsFinished
                else if (tabSwitch.inProgress) NavMotion.fadeThroughExit() else NavMotion.backExit()
            },
            // Used instead of the pop transitions while the finger is on the screen; they share the same motion.
            predictivePopEnterTransition = { swipeEdge ->
                val morph = isFabMorph(initialState.destination, targetState.destination)
                navDirection.goingBack = !morph
                if (morph) EnterTransition.None else NavMotion.backEnter(swipeEdge)
            },
            predictivePopExitTransition = { swipeEdge ->
                val morph = isFabMorph(initialState.destination, targetState.destination)
                navDirection.goingBack = !morph
                if (morph) ExitTransition.KeepUntilTransitionsFinished else NavMotion.backExit(swipeEdge)
            },
        ) {
            screen<AppRoute.Map>(navDirection) {
                // Drawn by PersistentMapHost underneath.
                DisposableEffect(Unit) {
                    mapEntryComposed = true
                    onDispose { mapEntryComposed = false }
                }
            }
            screen<AppRoute.Stats>(navDirection) {
                StatsRoute(container.mapRepository)
            }
            screen<AppRoute.Declaration>(navDirection) {
                DeclarationRoute(
                    draftStore = container.secureDraftStore,
                    pdfGenerator = container.pdfGenerator,
                    archive = container.declarationArchive,
                    onOpenPreview = { navController.navigate(AppRoute.DeclarationPreview(it)) },
                    onOpenHistory = { navController.navigate(AppRoute.DeclarationHistory) },
                    parishHandoff = container.parishHandoff,
                )
            }
            screen<AppRoute.DeclarationHistory>(navDirection) {
                DeclarationHistoryRoute(
                    archive = container.declarationArchive,
                    onOpenPreview = { navController.navigate(AppRoute.DeclarationPreview(it)) },
                    onBack = navController::popBackStack,
                )
            }
            screen<AppRoute.DeclarationPreview>(navDirection) { entry ->
                DeclarationPreviewRoute(
                    archive = container.declarationArchive,
                    declarationId = entry.toRoute<AppRoute.DeclarationPreview>().declarationId,
                    onBack = navController::popBackStack,
                )
            }
            screen<AppRoute.More>(navDirection) {
                MoreScreen(
                    themeMode = themeMode,
                    loadDatasetStatus = container.mapRepository::getDatasetStatus,
                    onThemeModeChange = onThemeModeChange,
                )
            }
        }
        }
        }
    }
}

/** Set while a tab switch is being laid out, so its transitions are told apart from going back. */
private class TabSwitch {
    var inProgress = false
}

/** A destination decorated for going back, see [backNavigationDecoration]. */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    direction: NavDirection,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
            Box(Modifier.fillMaxSize().then(backNavigationDecoration(direction))) { content(entry) }
        }
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

private const val TAB_SWITCH_START_MS = 300L
private const val TAB_SWITCH_TIMEOUT_MS = 5_000L

/** The declaration history opens from the form's FAB and closes back into it. */
private fun isFabMorph(from: NavDestination, to: NavDestination): Boolean {
    val form = from.hasRoute<AppRoute.Declaration>() && to.hasRoute<AppRoute.DeclarationHistory>()
    val back = from.hasRoute<AppRoute.DeclarationHistory>() && to.hasRoute<AppRoute.Declaration>()
    return form || back
}
