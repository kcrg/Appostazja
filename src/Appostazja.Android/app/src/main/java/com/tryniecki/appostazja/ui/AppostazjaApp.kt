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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.tryniecki.appostazja.ui.navigation.navMotionSpecs
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
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
import com.tryniecki.appostazja.ui.declaration.ParishField
import com.tryniecki.appostazja.ui.declaration.DeclarationHistoryRoute
import com.tryniecki.appostazja.ui.declaration.DeclarationPreviewRoute
import androidx.navigation.toRoute
import com.tryniecki.appostazja.ui.map.MapRoute
import com.tryniecki.appostazja.ui.map.MapFocus
import com.tryniecki.appostazja.ui.map.MapSearchFab
import com.tryniecki.appostazja.ui.map.MapViewModel
import com.tryniecki.appostazja.ui.map.SEARCH_MORPH_KEY
import com.tryniecki.appostazja.ui.search.PlaceSearchRoute
import com.tryniecki.appostazja.ui.search.PlaceSearchViewModel
import com.tryniecki.appostazja.ui.components.FabMorphRole
import com.tryniecki.appostazja.ui.components.fabMorph
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.ui.map.PersistentMapHost
import com.tryniecki.appostazja.ui.more.MoreScreen
import com.tryniecki.appostazja.ui.news.NewsArticleScreen
import com.tryniecki.appostazja.ui.news.NewsListScreen
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
    // Outside the NavHost, so they live as long as the activity: the search keeps its query between openings.
    val mapViewModel: MapViewModel = viewModel(factory = MapViewModel.factory(container.mapRepository))
    val searchViewModel: PlaceSearchViewModel = viewModel(factory = PlaceSearchViewModel.factory(container.mapRepository, container.recentSearches))
    val mapFocus = remember { MapFocus() }
    val motion = navMotionSpecs()
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
            else cover.animateTo(1f, motion.tabEnter)
        }
        val mapVisible = coverHidden || cover.value < 1f
        Box(contentModifier) {
            PersistentMapHost(visible = mapVisible) {
                MapRoute(
                    mapViewModel,
                    container.mapRepository,
                    container.streetViewRepository,
                    themeMode,
                    onUseInDeclaration = { place, field ->
                        container.parishHandoff.send(place, field)
                        switchTab(AppDestination.DECLARATION)
                        // The tab may come back on its history or a preview; the picked church belongs on the form.
                        navController.popBackStack<AppRoute.Declaration>(inclusive = false)
                    },
                    focus = mapFocus,
                )
            }
        }
        // Shared elements (a FAB morphing into the screen it opens) live in the NavHost; the map stays outside,
        // so its overlays do not go through the lookahead passes every frame.
        SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        val coverColor = MaterialTheme.colorScheme.background
        Box(contentModifier.drawBehind { if (cover.value > 0f) drawRect(coverColor, alpha = cover.value) })
        // Between a FAB and the screen it opens the container transform is the whole animation: the screen
        // underneath stays in place and the opened one appears only through the morphing container. Otherwise
        // different tabs fade through and screens of one tab move along the shared X axis.
        // Tapping a tab can also pop (back to the map is a pop), but it is a tab switch, not going back. The
        // predictive back gesture uses the same motion as going back.
        fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
            tabSwitch.inProgress || !sameTab(initialState.destination, targetState.destination)
        fun AnimatedContentTransitionScope<NavBackStackEntry>.isMorph() =
            isFabMorph(initialState.destination, targetState.destination)
        fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(forward: Boolean) = when {
            isMorph() -> EnterTransition.None
            isTabSwitch() -> motion.tabEnterTransition()
            else -> motion.hierarchyEnter(forward)
        }
        fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(forward: Boolean) = when {
            isMorph() -> ExitTransition.KeepUntilTransitionsFinished
            isTabSwitch() -> motion.tabExitTransition()
            else -> motion.hierarchyExit(forward)
        }
        NavHost(
            navController = navController,
            startDestination = AppRoute.Map,
            modifier = contentModifier,
            enterTransition = { enter(forward = true) },
            exitTransition = { exit(forward = true) },
            popEnterTransition = { enter(forward = false) },
            popExitTransition = { exit(forward = false) },
            predictivePopEnterTransition = { enter(forward = false) },
            predictivePopExitTransition = { exit(forward = false) },
        ) {
            screen<AppRoute.Map> {
                // The map itself is drawn by PersistentMapHost underneath; the destination carries its FAB.
                DisposableEffect(Unit) {
                    mapEntryComposed = true
                    onDispose { mapEntryComposed = false }
                }
                val mapState by mapViewModel.uiState.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    MapSearchFab(
                        loading = mapState.isLoading,
                        onClick = { navController.navigate(AppRoute.Search) },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            screen<AppRoute.Search> {
                PlaceSearchRoute(
                    viewModel = searchViewModel,
                    onPlaceClick = { place ->
                        mapFocus.show(place)
                        navController.popBackStack()
                    },
                    onClose = navController::popBackStack,
                    modifier = Modifier.fabMorph(SEARCH_MORPH_KEY, FabMorphRole.Screen),
                )
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
                    onPickParish = { navController.navigate(AppRoute.ParishSearch(it.name)) },
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
            screen<AppRoute.ParishSearch> { entry ->
                val field = ParishField.valueOf(entry.toRoute<AppRoute.ParishSearch>().field)
                // Its own search state, kept apart from the map search.
                val parishSearch: PlaceSearchViewModel = viewModel(
                    factory = PlaceSearchViewModel.factory(container.mapRepository, container.recentSearches),
                )
                PlaceSearchRoute(
                    viewModel = parishSearch,
                    onPlaceClick = { place ->
                        // The form takes the church from the handoff and offers an undo, as for a pick on the map.
                        container.parishHandoff.send(place, field)
                        navController.popBackStack()
                    },
                    onClose = navController::popBackStack,
                )
            }
            screen<AppRoute.More> {
                MoreScreen(
                    themeMode = themeMode,
                    cachedDatasetStatus = container.mapRepository.cachedDatasetStatus,
                    loadDatasetStatus = container.mapRepository::getDatasetStatus,
                    cachedNews = container.newsRepository.cachedArticles,
                    loadNews = container.newsRepository::articles,
                    readNewsIds = container.readArticles.ids.collectAsStateWithLifecycle().value,
                    onOpenArticle = { navController.navigate(AppRoute.NewsArticle(it.id)) },
                    onOpenNews = { navController.navigate(AppRoute.News) },
                    onThemeModeChange = onThemeModeChange,
                )
            }
            screen<AppRoute.News> {
                NewsListScreen(
                    cached = container.newsRepository.cachedArticles,
                    load = container.newsRepository::articles,
                    readIds = container.readArticles.ids.collectAsStateWithLifecycle().value,
                    onOpenArticle = { navController.navigate(AppRoute.NewsArticle(it.id)) },
                    onBack = navController::popBackStack,
                )
            }
            screen<AppRoute.NewsArticle> { entry ->
                NewsArticleScreen(
                    articleId = entry.toRoute<AppRoute.NewsArticle>().articleId,
                    cached = container.newsRepository.cachedArticle(entry.toRoute<AppRoute.NewsArticle>().articleId),
                    load = container.newsRepository::article,
                    onRead = container.readArticles::markRead,
                    onBack = navController::popBackStack,
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

/** A destination whose content can reach the transition it is animated by. */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
            Box(Modifier.fillMaxSize()) { content(entry) }
        }
    }
}

private fun NavDestination?.topLevelDestination(): AppDestination = when {
    this?.hasRoute<AppRoute.Stats>() == true -> AppDestination.STATS
    this?.hasRoute<AppRoute.Declaration>() == true ||
        this?.hasRoute<AppRoute.DeclarationHistory>() == true ||
        this?.hasRoute<AppRoute.DeclarationPreview>() == true ||
        this?.hasRoute<AppRoute.ParishSearch>() == true -> AppDestination.DECLARATION
    this?.hasRoute<AppRoute.More>() == true ||
        this?.hasRoute<AppRoute.News>() == true ||
        this?.hasRoute<AppRoute.NewsArticle>() == true -> AppDestination.MORE
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

/** Screens opened from a FAB, with the destination that holds the FAB. */
private fun NavDestination.fabParent(): AppRoute? = when {
    hasRoute<AppRoute.Search>() -> AppRoute.Map
    hasRoute<AppRoute.DeclarationHistory>() -> AppRoute.Declaration
    else -> null
}

private fun NavDestination.isRoute(route: AppRoute): Boolean = when (route) {
    AppRoute.Map -> hasRoute<AppRoute.Map>()
    AppRoute.Declaration -> hasRoute<AppRoute.Declaration>()
    else -> false
}

/**
 * Between a FAB and the screen it opens, either way: the search from the map, the history from the declaration
 * form. They all morph the same way.
 */
private fun isFabMorph(from: NavDestination, to: NavDestination): Boolean =
    to.fabParent()?.let(from::isRoute) == true || from.fabParent()?.let(to::isRoute) == true
