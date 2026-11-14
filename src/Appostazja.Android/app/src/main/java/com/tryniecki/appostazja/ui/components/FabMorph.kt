package com.tryniecki.appostazja.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.ui.navigation.NavMotion

/** The shared transition scope FABs and the screens they open morph within. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The visibility of the current navigation destination, for elements shared with another destination. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Which end of a FAB container transform an element is. */
enum class FabMorphRole { Fab, Screen }

/**
 * Material's container transform between a FAB and the full-screen destination it opens; every FAB that opens
 * a screen uses it, through the same NavHost transitions. Opening grows the FAB into the screen on an emphasized
 * curve; closing (the back arrow, a picked result, or the back gesture seeked with the finger) shrinks the screen
 * back the same way, and the button then settles with a slight spring bounce (M3 Expressive).
 *
 * Corners follow the container's size rather than time: square at full width, rounded like a card (28 dp) as soon
 * as it starts to shrink, the FAB's 16 dp at button size. The button itself takes the same [fabMorphShape], so its own corners never stick out of the
 * container. The container stays opaque throughout, with the screen drawn above the button.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.fabMorph(
    key: Any,
    role: FabMorphRole,
    visibilityScope: AnimatedVisibilityScope? = LocalNavAnimatedScope.current,
): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = visibilityScope ?: return this
    val shape = fabMorphShape()
    val contentState = with(shared) { rememberSharedContentState(key) }
    return with(shared) {
        sharedBounds(
            sharedContentState = contentState,
            animatedVisibilityScope = scope,
            // The button stays opaque under the screen fading in, and is back (almost at once) under the screen
            // fading out: the large container shows the screen, the small one the button, and nothing between
            // them ever lets the content behind show through.
            enter = when (role) {
                FabMorphRole.Fab -> fadeIn(tween(FAB_FADE_IN, easing = LinearEasing))
                FabMorphRole.Screen -> fadeIn(tween(SCREEN_FADE, easing = LinearEasing))
            },
            exit = when (role) {
                FabMorphRole.Fab -> fadeOut(tween(FAB_FADE_OUT, delayMillis = SCREEN_FADE, easing = LinearEasing))
                FabMorphRole.Screen -> fadeOut(tween(SCREEN_FADE - FAB_FADE_IN, delayMillis = FAB_FADE_IN, easing = LinearEasing))
            },
            boundsTransform = MorphBounds,
            // The button just lays its icon out at each size; the screen keeps its layout and scales with the
            // container, so its text does not reflow on every frame.
            resizeMode = when (role) {
                FabMorphRole.Fab -> RemeasureToBounds
                FabMorphRole.Screen -> scaleToBounds(ContentScale.FillWidth, Alignment.Center)
            },
            zIndexInOverlay = if (role == FabMorphRole.Screen) 1f else 0f,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }.let { if (role == FabMorphRole.Fab) it.landingBounce(scope, matched = { contentState.isMatchFound }) else it }
}

/**
 * The button settling back after the screen shrank into it: a small spring dip and rebound of a few percent,
 * measured on the button itself rather than on the distance the container travelled. Only when it actually took
 * over from its screen ([matched]): appearing with its destination on a tab switch, it just appears.
 */
@Composable
private fun Modifier.landingBounce(scope: AnimatedVisibilityScope, matched: () -> Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    val transition = scope.transition
    var arriving by remember { mutableStateOf(false) }
    LaunchedEffect(transition.currentState, transition.targetState) {
        // The match is found once both ends are laid out, and is not observable state: check it every frame
        // while the button comes in.
        if (transition.currentState == EnterExitState.PreEnter) {
            while (!matched()) withFrameNanos { }
            arriving = true
        }
        val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
        if (settled && arriving) {
            arriving = false
            scale.animateTo(1f, LandingSpring, initialVelocity = LANDING_VELOCITY)
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** The shape of a morphing FAB and its container, see [fabMorphCornerRadius]. */
@Composable
fun fabMorphShape(): Shape {
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    return remember(windowWidth) { FabMorphShape(windowWidth) }
}

/**
 * Corner radius from the width. A screen that has only just started to shrink (a slow back gesture) already
 * reads as a card: its corners reach [CARD_CORNER_RADIUS], the predictive back radius, within the first
 * [CARD_ROUNDING_SPAN] of the way, then ease to the button's [FAB_CORNER_RADIUS] as it nears button size. At
 * full width it is square, below button size (the landing dip) it keeps the button's radius, and it is never
 * more than half the shorter side.
 */
internal fun fabMorphCornerRadius(width: Float, height: Float, windowWidth: Float, density: Density): Float {
    val fab = with(density) { FAB_SIZE.toPx() }
    val fabRadius = with(density) { FAB_CORNER_RADIUS.toPx() }
    val cardRadius = with(density) { CARD_CORNER_RADIUS.toPx() }
    // 0 at button width, 1 at window width.
    val growth = if (windowWidth > fab) ((width - fab) / (windowWidth - fab)).coerceIn(0f, 1f) else 1f
    val shrunk = 1f - growth
    val radius = if (shrunk < CARD_ROUNDING_SPAN) {
        cardRadius * shrunk / CARD_ROUNDING_SPAN
    } else {
        fabRadius + (cardRadius - fabRadius) * growth / (1f - CARD_ROUNDING_SPAN)
    }
    return radius.coerceAtMost(minOf(width, height) / 2f)
}

private class FabMorphShape(private val windowWidth: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = fabMorphCornerRadius(size.width, size.height, windowWidth, density)
        return Outline.Rounded(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(radius)))
    }

    override fun equals(other: Any?) = other is FabMorphShape && other.windowWidth == windowWidth
    override fun hashCode() = windowWidth.hashCode()
}

/** Both ways on the emphasized curve, so the container arrives exactly at the button's size. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val MorphBounds = BoundsTransform { _, _ -> tween(NavMotion.DURATION, easing = Emphasized) }

// A quick, bouncy spring (M3 Expressive spatial springs go down to 0.6 damping) started with a push inwards:
// the button dips by about 5 % (a few dp) and rebounds once.
private val LandingSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 1_000f)
private const val LANDING_VELOCITY = -3f
private const val SCREEN_FADE = 150
private const val FAB_FADE_IN = 30
private const val FAB_FADE_OUT = 100
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val FAB_SIZE = 56.dp
private val FAB_CORNER_RADIUS = 16.dp
private val CARD_CORNER_RADIUS = NavMotion.BackCornerRadius
private const val CARD_ROUNDING_SPAN = 0.1f
