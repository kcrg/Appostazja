package com.tryniecki.appostazja.ui.map

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.data.StreetViewPreview
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.composables.icons.tabler.outline.R as Tabler


private const val PreviewAspectRatio = 16f / 10f
private const val LOADING_PLACEHOLDER_DELAY_MS = 600L

/** At least as tall as [ratio] at the available width, taller when the content needs it (large fonts). */
private fun Modifier.minAspectRatio(ratio: Float) = layout { measurable, constraints ->
    val minHeight = (constraints.maxWidth / ratio).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(minHeight = minHeight))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** The Street View preview of one parish; null while loading. Shared by the card and the sheet's action row. */
@Stable
class StreetViewPreviewState internal constructor() {
    var preview by mutableStateOf<StreetViewPreview?>(null)
        internal set
    internal var attempt by mutableIntStateOf(0)

    /** Set once loading takes long enough to be worth a placeholder; fast loads never show one. */
    internal var slowLoad by mutableStateOf(false)

    fun retry() {
        attempt++
    }

    /**
     * No thumbnail (refused, out of quota, or the Static API is not enabled), but the in-app panorama runs on the
     * Maps SDK and still works, so a plain button next to the sheet's other actions opens it.
     */
    val offersButton: Boolean
        get() = when (val current = preview) {
            is StreetViewPreview.Available -> current.image == null
            StreetViewPreview.PreviewDenied -> true
            else -> false
        }

    /**
     * The card only appears for something worth looking at: a thumbnail, a slow load, or a failure the user can
     * retry. Without a thumbnail it never shows, so opening a sheet does not flash a card that then disappears.
     */
    val showsCard: Boolean
        get() = when (val current = preview) {
            null -> slowLoad
            is StreetViewPreview.Available -> current.image != null
            StreetViewPreview.Failed -> true
            else -> false
        }

    /** The panorama to open; null means it is looked up by the parish location. */
    val panorama: StreetViewPreview.Available?
        get() = preview as? StreetViewPreview.Available
}

@Composable
fun rememberStreetViewPreview(
    place: PlaceDto,
    loadPreview: suspend (place: PlaceDto, userRetry: Boolean) -> StreetViewPreview,
): StreetViewPreviewState {
    val state = remember(place.id) { StreetViewPreviewState() }
    LaunchedEffect(state, state.attempt) {
        val userRetry = state.attempt > 0
        state.preview = null
        // A retry comes from the visible card, so it shows progress at once.
        state.slowLoad = userRetry
        val placeholder = launch {
            delay(LOADING_PLACEHOLDER_DELAY_MS)
            state.slowLoad = true
        }
        state.preview = try {
            loadPreview(place, userRetry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            StreetViewPreview.Failed
        } finally {
            placeholder.cancel()
        }
    }
    return state
}

@Composable
fun StreetViewPreviewCard(
    state: StreetViewPreviewState,
    /** Opens the in-app panorama; null when there is no preview and the panorama is looked up by location. */
    onOpen: (StreetViewPreview.Available?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.showsCard) return
    val preview = state.preview
    val retry = state::retry

    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = preview,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        contentKey = { it?.javaClass },
        label = "street-view-preview",
    ) { current ->
        when (val state = current) {
            null -> LoadingPreview()
            is StreetViewPreview.Available -> state.image?.let { image ->
                AvailablePreview(preview = state, image = image, onOpen = { onOpen(state) })
            }
            // Shown as a button in the sheet's action row instead.
            StreetViewPreview.PreviewDenied -> Unit
            // No panorama at all: the card is not shown.
            StreetViewPreview.NotAvailable -> Unit
            StreetViewPreview.Failed -> MessagePreview(
                icon = Tabler.drawable.tabler_ic_wifi_off_outline,
                title = stringResource(R.string.street_view_error_title),
                body = stringResource(R.string.street_view_error_body),
            ) {
                OutlinedButton(onClick = retry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            StreetViewPreview.Disabled -> Unit
        }
    }
}

@Composable
private fun LoadingPreview() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PreviewAspectRatio),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExpressiveLoadingIndicator(Modifier.size(36.dp))
            Text(
                stringResource(R.string.street_view_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AvailablePreview(preview: StreetViewPreview.Available, image: ImageBitmap, onOpen: () -> Unit) {
    val openLabel = stringResource(R.string.street_view_open)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PreviewAspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen),
    ) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.street_view_image_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        ) {
            Text(
                stringResource(R.string.street_view_label),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                listOfNotNull(preview.copyright, preview.date).joinToString(" · "),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f),
            )
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Tabler.drawable.tabler_ic_arrows_maximize_outline),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(openLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun StreetViewButton(onClick: () -> Unit) {
        FilledTonalButton(onClick = onClick) {
            Icon(
                painter = painterResource(Tabler.drawable.tabler_ic_panorama_horizontal_outline),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.street_view_label))
        }
}

@Composable
private fun MessagePreview(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    compact: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actions: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Full-size states match the thumbnail and loading card, so the card keeps its size between states.
            .then(if (compact) Modifier else Modifier.minAspectRatio(PreviewAspectRatio)),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (compact) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = tint)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(28.dp),
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (actions != null) {
                    Spacer(Modifier.size(4.dp))
                    actions()
                }
            }
        }
    }
}

