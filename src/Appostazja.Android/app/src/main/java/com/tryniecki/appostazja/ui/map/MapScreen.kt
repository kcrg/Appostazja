package com.tryniecki.appostazja.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.tryniecki.appostazja.ui.formatApiDate
import com.tryniecki.appostazja.ui.formatRating
import com.tryniecki.appostazja.ui.declaration.ParishField
import com.tryniecki.appostazja.ui.search.PlaceSearchScreen
import com.tryniecki.appostazja.ui.search.PlaceSearchViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.rememberCameraPositionState
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.api.model.RatingBreakdownDto
import com.tryniecki.appostazja.api.model.PlaceReportsDto
import com.tryniecki.appostazja.api.model.ReportDto
import com.tryniecki.appostazja.browser.rememberOpenCustomTab
import com.tryniecki.appostazja.data.MapRepository
import com.tryniecki.appostazja.data.StreetViewPreview
import com.tryniecki.appostazja.data.StreetViewRepository
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.components.SheetMotion
import com.tryniecki.appostazja.ui.theme.RatingBad
import com.tryniecki.appostazja.ui.theme.RatingGood
import com.tryniecki.appostazja.ui.theme.RatingNeutral
import com.tryniecki.appostazja.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.hypot

@Composable
fun MapRoute(
    repository: MapRepository,
    streetViewRepository: StreetViewRepository,
    themeMode: ThemeMode,
    onUseInDeclaration: (PlaceDto, ParishField) -> Unit,
) {
    val viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(repository))
    val searchViewModel: PlaceSearchViewModel = viewModel(factory = PlaceSearchViewModel.factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapColors = when (themeMode) {
        ThemeMode.SYSTEM -> ComposeMapColorScheme.FOLLOW_SYSTEM
        ThemeMode.LIGHT -> ComposeMapColorScheme.LIGHT
        ThemeMode.DARK -> ComposeMapColorScheme.DARK
    }
    MapScreen(
        state = state,
        mapColors = mapColors,
        onRefresh = viewModel::refresh,
        onUseInDeclaration = onUseInDeclaration,
        placeReports = repository::placeReports,
        // Failures are not cached, so a retry simply loads again.
        loadStreetView = { place, _ -> streetViewRepository.load(place.id) },
        search = { onClose, onPlaceClick ->
            val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { searchViewModel.ensureLoaded() }
            PlaceSearchScreen(
                state = searchState,
                onQueryChange = searchViewModel::onQueryChange,
                onSortChange = searchViewModel::onSortChange,
                onWithReportsOnlyChange = searchViewModel::onWithReportsOnlyChange,
                onLoadMore = searchViewModel::loadMore,
                onRetry = searchViewModel::retry,
                onPlaceClick = onPlaceClick,
                onClose = onClose,
            )
        },
    )
}

private const val PLACE_ZOOM = 15f

@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(
    state: MapUiState,
    mapColors: ComposeMapColorScheme,
    onRefresh: () -> Unit,
    onUseInDeclaration: (PlaceDto, ParishField) -> Unit,
    placeReports: (String) -> Flow<PlaceReportsDto>,
    loadStreetView: suspend (PlaceDto, Boolean) -> StreetViewPreview,
    search: @Composable (onClose: () -> Unit, onPlaceClick: (PlaceDto) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(52.05, 19.20), 5.7f)
    }
    var selectedPlace by remember { mutableStateOf<PlaceDto?>(null) }
    var nearbyPlaces by remember { mutableStateOf<List<PlaceDto>>(emptyList()) }
    val markerTransitions = remember(context) {
        val resolver = context.contentResolver
        // ValueAnimator.getDurationScale() needs API 33; the setting itself works everywhere.
        MarkerTransitions { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    }
    val motion = MaterialTheme.motionScheme

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The diagonal covers map rotation; the extra half preloads neighbours so short pans reveal no gaps.
        val clusterViewport = ceil(hypot(maxWidth.value, maxHeight.value) * 1.5f).toInt()
        // Keeps the compass and Google logo clear of the status bar; the overlay must project with it too.
        val mapPadding = WindowInsets.statusBars.asPaddingValues()
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            mapColorScheme = mapColors,
            contentPadding = mapPadding,
            uiSettings = MapUiSettings(
                compassEnabled = true,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                tiltGesturesEnabled = false,
            ),
            onMapClick = { selectedPlace = null },
        ) {
            ChurchClustering(
                items = state.places,
                viewport = clusterViewport,
                transitions = markerTransitions,
                onClusterClick = { cluster ->
                    val first = cluster.items.first().position
                    if (cameraState.position.zoom >= 19f || cluster.items.all { it.position == first }) {
                        nearbyPlaces = cluster.items.map { it.place }.sortedBy { it.name }
                    } else {
                        scope.launch { zoomIntoCluster(cluster, cameraState) }
                    }
                    true
                },
                onClusterItemClick = { item ->
                    selectedPlace = item.place
                    true
                },
            )
        }
        MarkerTransitionOverlay(markerTransitions, cameraState, mapPadding)

        // Only the status card itself takes space: a full-size AnimatedContent would hand its minimum size to
        // the Surface (propagateMinConstraints) and stretch the card over the whole map.
        AnimatedContent(
            targetState = when {
                state.isLoading -> MapOverlayState.LOADING
                state.error -> MapOverlayState.ERROR
                else -> MapOverlayState.CONTENT
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f))
            },
            label = "map-status",
        ) { overlay ->
            when (overlay) {
                MapOverlayState.LOADING -> LoadingPill()
                MapOverlayState.ERROR -> ErrorCard(errorDetails = state.errorDetails, onRetry = onRefresh)
                MapOverlayState.CONTENT -> Unit
            }
        }

        AnimatedVisibility(
            visible = !searchOpen && !state.isLoading,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            FloatingActionButton(onClick = { searchOpen = true }) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_search_outline),
                    contentDescription = stringResource(R.string.search_open),
                )
            }
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = slideInVertically(motion.defaultSpatialSpec()) { it / 8 } + fadeIn(motion.defaultEffectsSpec()),
            exit = slideOutVertically(motion.fastSpatialSpec()) { it / 8 } + fadeOut(motion.fastEffectsSpec()),
        ) {
            search(
                { searchOpen = false },
                { place ->
                    searchOpen = false
                    selectedPlace = place
                    scope.launch {
                        cameraState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), PLACE_ZOOM),
                            durationMs = 700,
                        )
                    }
                },
            )
        }
    }
    BackHandler(enabled = searchOpen) { searchOpen = false }

    // Sheets slide in with a tween: any spring overshoots visibly on a tall sheet.
    if (nearbyPlaces.isNotEmpty()) SheetMotion {
        ModalBottomSheet(onDismissRequest = { nearbyPlaces = emptyList() }) {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "title", contentType = "title") {
                    Text(
                        stringResource(R.string.map_nearby_places),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(nearbyPlaces, key = { it.id }, contentType = { "place" }) { place ->
                    ListItem(
                        onClick = {
                            nearbyPlaces = emptyList()
                            selectedPlace = place
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        supportingContent = { Text(place.address, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { ToneDot(place.tone) },
                    ) {
                        Text(place.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    selectedPlace?.let { place ->
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        SheetMotion {
            ModalBottomSheet(
                onDismissRequest = { selectedPlace = null },
                sheetState = sheetState,
            ) {
                ChurchDetailsSheet(
                    place = place,
                    placeReports = placeReports,
                    loadStreetView = loadStreetView,
                    onNavigate = { openGoogleMapsNavigation(context, place) },
                    onUseInDeclaration = { field ->
                        selectedPlace = null
                        onUseInDeclaration(place, field)
                    },
                )
            }
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
private fun ChurchClustering(
    items: List<ChurchClusterItem>,
    viewport: Int,
    transitions: MarkerTransitions,
    onClusterClick: (Cluster<ChurchClusterItem>) -> Boolean,
    onClusterItemClick: (ChurchClusterItem) -> Boolean,
) {
    val manager = rememberClusterManager<ChurchClusterItem>() ?: return
    val scheme = MaterialTheme.colorScheme
    val colors = MarkerColors(
        container = scheme.primaryContainer.toArgb(),
        onContainer = scheme.onPrimaryContainer.toArgb(),
        pin = scheme.surfaceContainerHigh.toArgb(),
        onPin = scheme.onSurface.toArgb(),
        outline = scheme.outline.toArgb(),
        shadow = scheme.scrim.copy(alpha = 0.24f).toArgb(),
        good = RatingGood.toArgb(),
        neutral = RatingNeutral.toArgb(),
        bad = RatingBad.toArgb(),
    )
    val context = LocalContext.current
    val algorithm = remember(manager) { StableViewportAlgorithm<ChurchClusterItem>(viewport, viewport) }
    var ready by remember(manager) { mutableStateOf(false) }

    MapEffect(manager, colors) { map ->
        manager.renderer = ChurchClusterRenderer(context, map, manager, colors, transitions)
        if (manager.algorithm !== algorithm) manager.setAlgorithm(algorithm)
        ready = true
    }
    SideEffect {
        manager.setOnClusterClickListener(onClusterClick)
        manager.setOnClusterItemClickListener(onClusterItemClick)
    }
    LaunchedEffect(manager, viewport, ready) {
        if (ready) {
            algorithm.lock()
            try {
                algorithm.updateViewSize(viewport, viewport)
            } finally {
                algorithm.unlock()
            }
            manager.onCameraIdle()
        }
    }
    if (ready && items.isNotEmpty()) {
        Clustering(items = items, clusterManager = manager)
    }
}

@Composable
private fun LoadingPill() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveLoadingIndicator(Modifier.size(28.dp))
            Text(stringResource(R.string.map_loading), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ErrorCard(
    errorDetails: String?,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 400.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_wifi_off_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.map_error_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.map_error_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Debug builds only, to tell a dead local API from a bad response.
                    errorDetails?.let { details ->
                        Text(
                            details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
internal fun ToneDot(tone: String?) {
    val color = when (tone) {
        "positive" -> RatingGood
        "mixed" -> RatingNeutral
        "negative" -> RatingBad
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
private fun ChurchDetailsSheet(
    place: PlaceDto,
    placeReports: (String) -> Flow<PlaceReportsDto>,
    loadStreetView: suspend (PlaceDto, Boolean) -> StreetViewPreview,
    onNavigate: () -> Unit,
    onUseInDeclaration: (ParishField) -> Unit,
) {
    // Null while closed; open with the previewed panorama or, without a preview, a lookup by location.
    val streetViewPreview = rememberStreetViewPreview(place, loadStreetView)
    var streetView by remember(place.id) { mutableStateOf<StreetViewRequest?>(null) }
    streetView?.let { request -> StreetViewScreen(place, request.preview, onClose = { streetView = null }) }
    val openCustomTab = rememberOpenCustomTab()
    var details by remember(place.id) { mutableStateOf<PlaceReportsDto?>(null) }
    var failed by remember(place.id) { mutableStateOf(false) }
    var attempt by remember(place.id) { mutableStateOf(0) }
    LaunchedEffect(place.id, attempt) {
        failed = false
        try {
            // Cached reports arrive first, current ones follow when they changed.
            placeReports(place.id).collect { details = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = details == null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    place.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                place.rating?.let { rating ->
                    Text(
                        stringResource(R.string.place_rating, formatRating(rating, LocalLocale.current.platformLocale)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                place.lastActionDate?.let { date ->
                    Text(
                        stringResource(R.string.church_last_action, formatApiDate(date, LocalLocale.current.platformLocale)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "actions") {
            // Wraps on narrow screens once the Street View button joins Navigate and Website.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onNavigate) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_directions_outline),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.church_navigate_short))
                }
                details?.website?.let { url ->
                    OutlinedButton(onClick = { openCustomTab(url) }) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_world_outline),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.church_website))
                    }
                }
                if (streetViewPreview.offersButton) {
                    StreetViewButton(onClick = { streetView = StreetViewRequest(streetViewPreview.panorama) })
                }
                UseInDeclarationButton(onPick = onUseInDeclaration)
            }
        }

        if (streetViewPreview.showsCard) item(key = "street-view") {
            StreetViewPreviewCard(
                state = streetViewPreview,
                onOpen = { streetView = StreetViewRequest(it) },
                // Fades in, and the reports below slide down to make room instead of jumping.
                modifier = Modifier.animateItem(),
            )
        }

        item { HorizontalDivider() }
        item { Text(stringResource(R.string.church_relations), style = MaterialTheme.typography.titleMedium) }
        if (place.ratings.total > 0) item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RelationStat(
                    label = stringResource(R.string.church_positive),
                    count = place.ratings.positive,
                    color = RatingGood,
                    modifier = Modifier.weight(1f),
                )
                RelationStat(
                    label = stringResource(R.string.church_neutral),
                    count = place.ratings.neutral,
                    color = RatingNeutral,
                    modifier = Modifier.weight(1f),
                )
                RelationStat(
                    label = stringResource(R.string.church_negative),
                    count = place.ratings.negative,
                    color = RatingBad,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        place.stages?.let { stages ->
            val rows = listOf(
                R.string.church_report_before to stages.beforeApostasy,
                R.string.church_report_apostasy to stages.apostasy,
                R.string.church_report_after to stages.afterApostasy,
            ).filter { (_, ratings) -> ratings.total > 0 }
            // A single stage would only repeat the totals above.
            if (rows.size > 1) item(key = "stages") { StageBreakdown(rows) }
        }
        if (place.ratings.total == 0) item {
            Text(
                stringResource(R.string.church_no_relations),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }


        when {
            failed -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.church_reports_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.action_retry)) }
                }
            }
            details == null -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator(Modifier.size(40.dp))
                }
            }
            else -> itemsIndexed(
                details?.reports.orEmpty(),
                key = { index, _ -> index },
                contentType = { _, _ -> "report" },
            ) { _, report -> ReportCard(report) }
        }
    }
}

@Composable
private fun ReportCard(report: ReportDto) {
    val stage = when (report.stage) {
        "before_apostasy" -> stringResource(R.string.church_report_before)
        "after_apostasy" -> stringResource(R.string.church_report_after)
        "apostasy" -> stringResource(R.string.church_report_apostasy)
        else -> report.stage
    }
    val rating = when (report.rating) {
        "easy" -> stringResource(R.string.church_positive)
        "medium" -> stringResource(R.string.church_neutral)
        "hard" -> stringResource(R.string.church_negative)
        else -> report.rating
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.church_report_heading, stage, rating), style = MaterialTheme.typography.titleSmall)
            report.date?.let {
                Text(formatApiDate(it, LocalLocale.current.platformLocale), style = MaterialTheme.typography.labelSmall)
            }
            val comment = report.comment?.takeIf { it.isNotBlank() }
            if (comment != null) {
                Text(comment, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.church_report_no_comment),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RelationStat(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.height(6.dp))
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StageBreakdown(rows: List<Pair<Int, RatingBreakdownDto>>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.church_stages_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rows.forEach { (labelRes, ratings) ->
                val label = stringResource(labelRes)
                val summary = stringResource(
                    R.string.church_stage_summary, label, ratings.positive, ratings.neutral, ratings.negative,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { contentDescription = summary },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    StageCount(ratings.positive, RatingGood)
                    StageCount(ratings.neutral, RatingNeutral)
                    StageCount(ratings.negative, RatingBad)
                }
            }
        }
    }
}

@Composable
private fun StageCount(count: Int, color: Color) {
    Row(
        modifier = Modifier.widthIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private suspend fun zoomIntoCluster(
    cluster: Cluster<ChurchClusterItem>,
    cameraState: com.google.maps.android.compose.CameraPositionState,
) {
    val update = runCatching {
        val builder = LatLngBounds.builder()
        cluster.items.forEach { builder.include(it.position) }
        CameraUpdateFactory.newLatLngBounds(builder.build(), 120)
    }.getOrElse {
        CameraUpdateFactory.newLatLngZoom(cluster.position, (cameraState.position.zoom + 2f).coerceAtMost(18f))
    }
    cameraState.animate(update, durationMs = 420)
}

private fun openGoogleMapsNavigation(context: Context, place: PlaceDto) {
    val navigationUri = Uri.parse("google.navigation:q=${place.latitude},${place.longitude}&mode=d")
    val mapsIntent = Intent(Intent.ACTION_VIEW, navigationUri).setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(mapsIntent)
    } catch (_: ActivityNotFoundException) {
        val fallback = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${place.latitude},${place.longitude}",
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, fallback))
    }
}

private data class StreetViewRequest(val preview: StreetViewPreview.Available?)

private enum class MapOverlayState { LOADING, ERROR, CONTENT }

/** Sends this church to the declaration form; the menu asks which of its two parish fields it fills. */
@Composable
private fun UseInDeclarationButton(onPick: (ParishField) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menuOpen = true }) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_file_description_outline),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.church_use_in_declaration))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.church_use_as_baptism)) },
                leadingIcon = {
                    Icon(painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_droplet_outline), null)
                },
                onClick = {
                    menuOpen = false
                    onPick(ParishField.BAPTISM)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.church_use_as_residence)) },
                leadingIcon = {
                    Icon(painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_home_outline), null)
                },
                onClick = {
                    menuOpen = false
                    onPick(ParishField.RESIDENCE)
                },
            )
        }
    }
}
