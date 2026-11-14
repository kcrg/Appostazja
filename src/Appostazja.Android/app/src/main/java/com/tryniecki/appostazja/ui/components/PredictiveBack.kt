package com.tryniecki.appostazja.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.ui.navigation.NavMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** How far a predictive back gesture on a full-screen overlay has gone, and from which edge. */
@Stable
class PredictiveBackState internal constructor() {
    internal val progress = Animatable(0f)
    internal var edge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
}

/**
 * Lets the back gesture close a full-screen overlay the way Material's predictive back does: while the finger
 * moves the overlay shrinks towards 90 %, rounds its corners and drifts away from the swiped edge; letting go
 * closes it with [onBack], cancelling springs it back. The overlay keeps that shape for its own exit animation.
 */
@Composable
fun rememberPredictiveBack(enabled: Boolean, onBack: () -> Unit): PredictiveBackState {
    val state = remember { PredictiveBackState() }
    val scope = rememberCoroutineScope()
    // Back to full size whenever the overlay is shown again.
    LaunchedEffect(enabled) { if (enabled) state.progress.snapTo(0f) }
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                state.edge = event.swipeEdge
                state.progress.snapTo(event.progress)
            }
            onBack()
        } catch (cancelled: CancellationException) {
            scope.launch { state.progress.animateTo(0f, tween(NavMotion.Duration)) }
            throw cancelled
        }
    }
    return state
}

fun Modifier.predictiveBack(state: PredictiveBackState): Modifier = graphicsLayer {
    val progress = state.progress.value
    if (progress == 0f) return@graphicsLayer
    val scale = 1f - (1f - NavMotion.BACK_SCALE) * progress
    scaleX = scale
    scaleY = scale
    // Drifts a little away from the edge the gesture started at, as the system animation does.
    val direction = if (state.edge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    translationX = direction * 8.dp.toPx() * progress
    shape = RoundedCornerShape(NavMotion.BackCornerRadius * progress)
    clip = true
}
