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
 * Corners follow the container's size rather than time: the FAB's 16 dp at button size, straightening to square
 * at full width. The button itself takes the same [fabMorphShape], so its own corners never stick out of the
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
    val landing = if (role == FabMorphRole.Fab) landingBounce(scope) else Modifier
    return with(shared) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
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
    }.then(landing)
}

/**
 * The button settling back after the screen shrank into it: a small spring dip and rebound of a few percent,
 * measured on the button itself rather than on the distance the container travelled.
 */
@Composable
private fun landingBounce(scope: AnimatedVisibilityScope): Modifier {
    val scale = remember { Animatable(1f) }
    val transition = scope.transition
    var arriving by remember { mutableStateOf(false) }
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (transition.currentState == EnterExitState.PreEnter) arriving = true
        val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
        if (settled && arriving) {
            arriving = false
            scale.animateTo(1f, LandingSpring, initialVelocity = LANDING_VELOCITY)
        }
    }
    return Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** The shape of a morphing FAB and its container: 16 dp corners at button size, square at full width. */
@Composable
fun fabMorphShape(): Shape {
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    return remember(windowWidth) { FabMorphShape(windowWidth) }
}

/**
 * Corner radius from the width: [FAB_CORNER_RADIUS] up to the FAB's width, then shrinking linearly to zero at
 * the window width. Below button size (the landing bounce) it keeps the button's radius, never more than half
 * the shorter side.
 */
internal fun fabMorphCornerRadius(width: Float, height: Float, windowWidth: Float, density: Density): Float {
    val fab = with(density) { FAB_SIZE.toPx() }
    val maxRadius = with(density) { FAB_CORNER_RADIUS.toPx() }
    val growth = if (windowWidth > fab) ((width - fab) / (windowWidth - fab)).coerceIn(0f, 1f) else 1f
    return (maxRadius * (1f - growth)).coerceAtMost(minOf(width, height) / 2f)
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
