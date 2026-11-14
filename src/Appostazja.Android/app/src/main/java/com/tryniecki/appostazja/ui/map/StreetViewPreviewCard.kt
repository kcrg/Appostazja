package com.tryniecki.appostazja.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.composables.icons.tabler.outline.R as Tabler

private val PreviewShape = RoundedCornerShape(24.dp)

@Composable
fun StreetViewPreviewCard(
    place: PlaceDto,
    loadPreview: suspend (place: PlaceDto, userRetry: Boolean) -> StreetViewPreview,
    /** Opens the in-app panorama; null when there is no preview and the panorama is looked up by location. */
    onOpen: (StreetViewPreview.Available?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var attempt by remember(place.id) { mutableIntStateOf(0) }
    var preview by remember(place.id) { mutableStateOf<StreetViewPreview?>(null) }
    LaunchedEffect(place.id, attempt) {
        preview = null
        preview = try {
            loadPreview(place, attempt > 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            StreetViewPreview.Failed
        }
    }

    // Nothing to show without a key; keep the sheet clean instead of displaying a dead card.
    if (preview == StreetViewPreview.Disabled) return

    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = preview,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        contentKey = { it?.javaClass },
        label = "street-view-preview",
    ) { state ->
        when (state) {
            null -> LoadingPreview()
            is StreetViewPreview.Available -> AvailablePreview(
                preview = state,
                onOpen = { onOpen(state) },
            )
            StreetViewPreview.PreviewDenied -> MessagePreview(
                icon = Tabler.drawable.tabler_ic_panorama_horizontal_outline,
                title = stringResource(R.string.street_view_label),
                body = stringResource(R.string.street_view_explore_body),
                compact = true,
                actions = {
                    FilledTonalButton(onClick = { onOpen(null) }) {
                        Text(stringResource(R.string.street_view_open))
                    }
                },
            )
            StreetViewPreview.NotAvailable -> MessagePreview(
                icon = Tabler.drawable.tabler_ic_map_pin_off_outline,
                title = stringResource(R.string.street_view_unavailable_title),
                body = stringResource(R.string.street_view_unavailable_body),
                compact = true,
            )
            StreetViewPreview.QuotaExceeded -> MessagePreview(
                icon = Tabler.drawable.tabler_ic_hourglass_outline,
                title = stringResource(R.string.street_view_quota_title),
                body = stringResource(R.string.street_view_quota_body),
                tint = MaterialTheme.colorScheme.tertiary,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { attempt++ }) {
                        Text(stringResource(R.string.action_retry))
                    }
                    // Opening Google Maps is free for us, so it stays usable while our quota is exhausted.
                    FilledTonalButton(onClick = { openStreetViewInGoogleMaps(context, place) }) {
                        Text(stringResource(R.string.street_view_open_in_maps))
                    }
                }
            }
            StreetViewPreview.Failed -> MessagePreview(
                icon = Tabler.drawable.tabler_ic_wifi_off_outline,
                title = stringResource(R.string.street_view_error_title),
                body = stringResource(R.string.street_view_error_body),
            ) {
                OutlinedButton(onClick = { attempt++ }) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            StreetViewPreview.Disabled -> Unit
        }
    }
}

@Composable
private fun LoadingPreview() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = PreviewShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveLoadingIndicator(Modifier.size(24.dp))
            Text(
                stringResource(R.string.street_view_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** No thumbnail on purpose: the Static API image is the only billed Street View call, the panorama is free. */
@Composable
private fun AvailablePreview(preview: StreetViewPreview.Available, onOpen: () -> Unit) {
    MessagePreview(
        icon = Tabler.drawable.tabler_ic_panorama_horizontal_outline,
        title = stringResource(R.string.street_view_label),
        body = preview.date?.let { stringResource(R.string.street_view_photo_date, it) }
            ?: stringResource(R.string.street_view_explore_body),
        compact = true,
        actions = {
            FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.street_view_open)) }
        },
    )
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
        modifier = Modifier.fillMaxWidth(),
        shape = PreviewShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (compact) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = tint)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                actions?.invoke()
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

/**
 * Last resort once the free quota is used up: Street View in Google Maps costs nothing against it. Otherwise
 * the panorama opens in the app ([StreetViewScreen]).
 */
private fun openStreetViewInGoogleMaps(context: Context, place: PlaceDto) {
    val uri = Uri.parse("https://www.google.com/maps/@").buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("map_action", "pano")
        .appendQueryParameter("viewpoint", "${place.latitude},${place.longitude}")
        .build()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // No browser or Maps app; nothing sensible to fall back to.
    }
}
