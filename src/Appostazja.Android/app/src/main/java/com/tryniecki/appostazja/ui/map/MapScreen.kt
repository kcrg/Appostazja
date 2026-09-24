package com.tryniecki.appostazja.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.data.MapRepository
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.theme.RatingBad
import com.tryniecki.appostazja.ui.theme.RatingGood
import com.tryniecki.appostazja.ui.theme.RatingNeutral
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MapRoute(repository: MapRepository) {
    val viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MapScreen(state = state, onRefresh = viewModel::refresh)
}

@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(
    state: MapUiState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(52.05, 19.20), 5.7f)
    }
    val clusterItems = remember(state.places) { state.places.map(::ChurchClusterItem) }
    var selectedPlace by remember { mutableStateOf<PlaceDto?>(null) }
    val motion = MaterialTheme.motionScheme

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isBuildingEnabled = true),
            uiSettings = MapUiSettings(
                compassEnabled = true,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapClick = { selectedPlace = null },
        ) {
            Clustering(
                items = clusterItems,
                onClusterClick = { cluster ->
                    scope.launch {
                        zoomIntoCluster(cluster, cameraState)
                    }
                    true
                },
                onClusterItemClick = { item ->
                    selectedPlace = item.place
                    true
                },
                clusterContent = { cluster -> ClusterMarker(cluster) },
                clusterItemContent = { item -> ChurchMarker(item.place) },
                clusterContentAnchor = Offset(0.5f, 0.5f),
                clusterItemContentAnchor = Offset(0.5f, 1f),
            )
        }

        AnimatedContent(
            targetState = when {
                state.isLoading -> MapOverlayState.LOADING
                state.error -> MapOverlayState.ERROR
                else -> MapOverlayState.CONTENT
            },
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "map-status",
        ) { overlay ->
            when (overlay) {
                MapOverlayState.LOADING -> StatusCard(
                    modifier = Modifier.align(Alignment.Center),
                    content = {
                        ExpressiveLoadingIndicator(Modifier.size(32.dp))
                        Text(stringResource(R.string.map_loading), style = MaterialTheme.typography.titleMedium)
                    },
                )
                MapOverlayState.ERROR -> ErrorCard(
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = onRefresh,
                )
                MapOverlayState.CONTENT -> Unit
            }
        }

        if (state.places.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
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
            }
        }
    }

    selectedPlace?.let { place ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedPlace = null },
            sheetState = sheetState,
        ) {
            ChurchDetailsSheet(
                place = place,
                onNavigate = { openGoogleMapsNavigation(context, place) },
            )
        }
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
private fun ErrorCard(modifier: Modifier = Modifier, onRetry: () -> Unit) {
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
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun ClusterMarker(cluster: Cluster<ChurchClusterItem>) {
    val composition = remember(cluster.items) {
        cluster.items.fold(RelationComposition()) { acc, item ->
            acc + RelationComposition(
                positive = item.place.ratings.positive,
                neutral = item.place.ratings.neutral,
                negative = item.place.ratings.negative,
            )
        }
    }
    val diameter = when {
        cluster.size < 10 -> 44.dp
        cluster.size < 100 -> 48.dp
        cluster.size < 1_000 -> 52.dp
        else -> 58.dp
    }
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val haloRadius = size.minDimension / 2f - 1.dp.toPx()
            val ringWidth = 5.dp.toPx()
            val ringRadius = haloRadius - ringWidth / 2f - 1.dp.toPx()
            drawCircle(Color.White, haloRadius, center)
            drawCircle(outline, ringRadius, center, style = Stroke(ringWidth))
            drawRelationArcs(composition, ringRadius, ringWidth, center)
            drawCircle(surface, ringRadius - ringWidth / 2f - 3.dp.toPx(), center)
        }
        Text(
            cluster.size.toString(),
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRelationArcs(
    composition: RelationComposition,
    radius: Float,
    strokeWidth: Float,
    center: Offset,
) {
    val total = composition.total
    if (total <= 0) return
    val segments = listOf(
        composition.negative to RatingBad,
        composition.neutral to RatingNeutral,
        composition.positive to RatingGood,
    ).filter { it.first > 0 }
    val gap = 3f
    val drawable = 360f - segments.size * gap
    var angle = -90f + gap / 2f
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
    segments.forEach { (count, color) ->
        val sweep = drawable * count / total.toFloat()
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
        angle += sweep + gap
    }
}

@Composable
private fun ChurchMarker(place: PlaceDto) {
    val color = when {
        place.score < -0.2 -> RatingBad
        place.score <= 0.2 -> RatingNeutral
        else -> RatingGood
    }
    val outline = Color.White
    Canvas(Modifier.size(width = 38.dp, height = 46.dp)) {
        val center = Offset(size.width / 2f, size.width / 2f)
        val radius = size.width * 0.42f
        val tailTop = center.y + radius * 0.48f
        val path = Path().apply {
            moveTo(center.x - radius * 0.58f, tailTop)
            lineTo(center.x, size.height - 1.dp.toPx())
            lineTo(center.x + radius * 0.58f, tailTop)
            close()
        }
        drawPath(path, color)
        drawCircle(color, radius, center)
        drawPath(path, outline, style = Stroke(2.dp.toPx()))
        drawCircle(outline, radius, center, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, radius * 0.34f, center)
    }
}

@Composable
private fun ChurchDetailsSheet(place: PlaceDto, onNavigate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
            val displayRating = ((place.score + 1.0) * 2.5).coerceIn(0.0, 5.0)
            Text(
                "Ocena ${String.format(java.util.Locale.getDefault(), "%.1f", displayRating)}/5",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalDivider()
        Text(stringResource(R.string.church_relations), style = MaterialTheme.typography.titleMedium)
        if (place.ratings.total > 0) {
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
        } else {
            Text(
                stringResource(R.string.church_no_relations),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.church_comments_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.church_comments_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_directions_outline),
                contentDescription = null,
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.church_navigate))
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

private data class RelationComposition(
    val positive: Int = 0,
    val neutral: Int = 0,
    val negative: Int = 0,
) {
    val total: Int get() = positive + neutral + negative
    operator fun plus(other: RelationComposition) = RelationComposition(
        positive + other.positive,
        neutral + other.neutral,
        negative + other.negative,
    )
}

private enum class MapOverlayState { LOADING, ERROR, CONTENT }
