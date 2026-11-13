package com.tryniecki.appostazja.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.tryniecki.appostazja.ui.formatApiDate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.algo.NonHierarchicalViewBasedAlgorithm
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
import com.tryniecki.appostazja.ui.theme.RatingBad
import com.tryniecki.appostazja.ui.theme.RatingGood
import com.tryniecki.appostazja.ui.theme.RatingNeutral
import com.tryniecki.appostazja.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.hypot

@Composable
fun MapRoute(
    repository: MapRepository,
    streetViewRepository: StreetViewRepository,
    themeMode: ThemeMode,
) {
    val viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(repository))
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
        loadReports = repository::getPlaceReports,
        loadStreetView = { place, userRetry ->
            streetViewRepository.load(place.id, place.latitude, place.longitude, userRetry)
        },
    )
}

@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(
    state: MapUiState,
    mapColors: ComposeMapColorScheme,
    onRefresh: () -> Unit,
    loadReports: suspend (String) -> PlaceReportsDto,
    loadStreetView: suspend (PlaceDto, Boolean) -> StreetViewPreview,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(52.05, 19.20), 5.7f)
    }
    var selectedPlace by remember { mutableStateOf<PlaceDto?>(null) }
    var nearbyPlaces by remember { mutableStateOf<List<PlaceDto>>(emptyList()) }
    val motion = MaterialTheme.motionScheme
    val openCustomTab = rememberOpenCustomTab()
    val dataSourceUrl = stringResource(R.string.map_data_source_url)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The diagonal covers map rotation, with room to preload nearby pins.
        val clusterViewport = ceil(hypot(maxWidth.value, maxHeight.value)).toInt() + 160
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            mapColorScheme = mapColors,
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

        AnimatedContent(
            targetState = when {
                state.isLoading -> MapOverlayState.LOADING
                state.error -> MapOverlayState.ERROR
                else -> MapOverlayState.CONTENT
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
            contentAlignment = Alignment.Center,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "map-status",
        ) { overlay ->
            when (overlay) {
                MapOverlayState.LOADING -> StatusCard(
                    content = {
                        ExpressiveLoadingIndicator(Modifier.size(32.dp))
                        Text(stringResource(R.string.map_loading), style = MaterialTheme.typography.titleMedium)
                    },
                )
                MapOverlayState.ERROR -> ErrorCard(errorDetails = state.errorDetails, onRetry = onRefresh)
                MapOverlayState.CONTENT -> Unit
            }
        }

        if (state.places.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Parafie na mapie", style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.map_loaded_count, state.places.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.isRefreshing) {
                        ExpressiveLoadingIndicator(Modifier.size(30.dp))
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_refresh_outline),
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { openCustomTab(dataSourceUrl) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.map_data_source))
                }
            }
        }
    }

    if (nearbyPlaces.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { nearbyPlaces = emptyList() }) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                item(key = "title", contentType = "title") {
                    Text(stringResource(R.string.map_nearby_places), style = MaterialTheme.typography.titleMedium)
                }
                items(nearbyPlaces, key = { it.id }, contentType = { "place" }) { place ->
                    TextButton(
                        onClick = {
                            nearbyPlaces = emptyList()
                            selectedPlace = place
                        },
                    ) {
                        Column {
                            Text(place.name)
                            Text(place.address, style = MaterialTheme.typography.bodySmall)
                        }
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
        ModalBottomSheet(
            onDismissRequest = { selectedPlace = null },
            sheetState = sheetState,
        ) {
            ChurchDetailsSheet(
                place = place,
                loadReports = loadReports,
                loadStreetView = loadStreetView,
                onNavigate = { openGoogleMapsNavigation(context, place) },
            )
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
private fun ChurchClustering(
    items: List<ChurchClusterItem>,
    viewport: Int,
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
    val algorithm = remember(manager) { NonHierarchicalViewBasedAlgorithm<ChurchClusterItem>(viewport, viewport) }
    var ready by remember(manager) { mutableStateOf(false) }

    MapEffect(manager, colors) { map ->
        manager.renderer = ChurchClusterRenderer(context, map, manager, colors)
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
private fun StatusCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Card(modifier = modifier.padding(24.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun ErrorCard(
    errorDetails: String?,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Card(modifier = modifier.padding(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_info_circle_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(stringResource(R.string.map_error_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.map_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            errorDetails?.let { details ->
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun ChurchDetailsSheet(
    place: PlaceDto,
    loadReports: suspend (String) -> PlaceReportsDto,
    loadStreetView: suspend (PlaceDto, Boolean) -> StreetViewPreview,
    onNavigate: () -> Unit,
) {
    val openCustomTab = rememberOpenCustomTab()
    var details by remember(place.id) { mutableStateOf<PlaceReportsDto?>(null) }
    var failed by remember(place.id) { mutableStateOf(false) }
    var attempt by remember(place.id) { mutableStateOf(0) }
    LaunchedEffect(place.id, attempt) {
        failed = false
        try {
            details = loadReports(place.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
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
                if (place.ratings.total > 0) {
                    val displayRating = ((place.score + 1.0) * 2.5).coerceIn(0.0, 5.0)
                    Text(
                        "Ocena ${String.format(LocalLocale.current.platformLocale, "%.1f", displayRating)}/5",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
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

        item(key = "street-view") { StreetViewPreviewCard(place = place, loadPreview = loadStreetView) }

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

        details?.website?.let { url ->
            item {
                TextButton(onClick = { openCustomTab(url) }) {
                    Text(stringResource(R.string.church_website))
                }
            }
        }

        item {
            Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_directions_outline),
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.church_navigate))
            }
        }

        when {
            failed -> item {
                Column {
                    Text(stringResource(R.string.church_reports_error))
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.action_retry)) }
                }
            }
            details == null -> item { Text(stringResource(R.string.church_reports_loading)) }
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
            report.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
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

private enum class MapOverlayState { LOADING, ERROR, CONTENT }
