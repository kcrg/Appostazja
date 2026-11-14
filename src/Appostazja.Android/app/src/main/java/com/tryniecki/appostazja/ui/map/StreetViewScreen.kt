package com.tryniecki.appostazja.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.StreetViewPanoramaOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.google.android.gms.maps.model.StreetViewSource
import com.google.maps.android.MapsExperimentalFeature
import com.google.maps.android.compose.streetview.StreetView
import com.google.maps.android.compose.streetview.rememberStreetViewCameraPositionState
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.data.StreetViewPreview
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.components.EmptyState
import com.tryniecki.appostazja.data.bearing
import com.composables.icons.tabler.outline.R as Tabler

/**
 * Interactive Street View inside the app, opened on the same panorama as the preview and facing the
 * parish. A full-screen dialog, so it sits above the parish sheet and Back returns to it.
 */
@OptIn(MapsExperimentalFeature::class)
@Composable
internal fun StreetViewScreen(
    place: PlaceDto,
    /** The previewed panorama; without it the nearest outdoor panorama to the parish is looked up. */
    preview: StreetViewPreview.Available?,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val camera = rememberStreetViewCameraPositionState()
        var oriented by remember { mutableStateOf(false) }
        var timedOut by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(LOAD_TIMEOUT_MS)
            timedOut = camera.location == null
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
        // The panorama runs under the info panel so the panel's rounded corners sit on the picture.
        Box(Modifier.fillMaxSize()) {
            StreetView(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                // The start position must go in the options: setPosition before the panorama exists is a no-op.
                streetViewPanoramaOptionsFactory = {
                    StreetViewPanoramaOptions().apply {
                        if (preview != null) {
                            panoramaId(preview.panoId)
                            // Same direction as the thumbnail, from the first frame.
                            panoramaCamera(StreetViewPanoramaCamera.Builder().bearing(preview.heading.toFloat()).build())
                        } else {
                            position(LatLng(place.latitude, place.longitude), SEARCH_RADIUS_METERS, StreetViewSource.OUTDOOR)
                        }
                    }
                },
                isStreetNamesEnabled = true,
                isUserNavigationEnabled = true,
            )
            // The panorama starts facing north; turn it towards the parish once, when it first loads, and leave
            // the view alone while the user walks around.
            val location = camera.location
            LaunchedEffect(location != null) {
                if (location != null && !oriented && preview == null) {
                    oriented = true
                    val heading = preview?.heading ?: bearing(
                        location.position.latitude, location.position.longitude, place.latitude, place.longitude,
                    )
                    camera.animateTo(
                        StreetViewPanoramaCamera.Builder()
                            .bearing(heading.toFloat())
                            .tilt(0f)
                            .zoom(0f)
                            .build(),
                        0,
                    )
                }
            }

            if (location == null && !timedOut) {
                ExpressiveLoadingIndicator(
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                )
            }

            if (location == null && timedOut) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Tabler.drawable.tabler_ic_map_pin_off_outline,
                            title = stringResource(R.string.street_view_unavailable_title),
                            body = stringResource(R.string.street_view_unavailable_body),
                        )
                    }
                }
            }

            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            ) {
                Icon(
                    painterResource(Tabler.drawable.tabler_ic_arrow_left_outline),
                    contentDescription = stringResource(R.string.street_view_close),
                )
            }
        }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        place.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    preview?.date?.let { date ->
                        Text(
                            stringResource(R.string.street_view_photo_date, date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val SEARCH_RADIUS_METERS = 120
private const val LOAD_TIMEOUT_MS = 10_000L
