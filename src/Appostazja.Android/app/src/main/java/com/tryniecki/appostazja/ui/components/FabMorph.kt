package com.tryniecki.appostazja.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.ui.navigation.NavMotion

/** The shared transition scope FABs and the screens they open morph within. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The visibility of the current navigation destination, for elements shared with another destination. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Which end of a FAB container transform an element is. */
enum class FabMorphRole { Fab, Screen }

/**
 * Material's container transform between a FAB and the full-screen container it opens: the FAB's bounds grow
 * into the screen and its corners straighten, and closing (including the back gesture, seeked with the finger)
 * shrinks the screen back into the button. The container stays opaque throughout, with the screen drawn above the
 * button.
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
    val radius by scope.transition.animateDp(
        transitionSpec = { tween(NavMotion.DURATION, easing = Emphasized) },
        label = "fab-morph-corners",
    ) { state ->
        val atRest = state == EnterExitState.Visible
        when (role) {
            FabMorphRole.Fab -> if (atRest) FabCornerRadius else 0.dp
            FabMorphRole.Screen -> if (atRest) 0.dp else FabCornerRadius
        }
    }
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
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(radius)),
        )
    }
}

private const val SCREEN_FADE = 150
private const val FAB_FADE_IN = 30
private const val FAB_FADE_OUT = 100
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val FabCornerRadius = 16.dp

@OptIn(ExperimentalSharedTransitionApi::class)
private val MorphBounds = BoundsTransform { _, _ -> tween(NavMotion.DURATION, easing = Emphasized) }
