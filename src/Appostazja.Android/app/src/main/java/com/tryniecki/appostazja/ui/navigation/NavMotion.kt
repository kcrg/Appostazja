package com.tryniecki.appostazja.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Material motion patterns for moving between screens (m3.material.io/styles/motion/transitions):
 * - fade through between top-level tabs, which are not related to each other;
 * - shared axis X within a tab, forward into a deeper screen and back out of it;
 * - leaving a screen with back follows the predictive back gesture: the screen shrinks towards 90 % with
 *   rounded corners and the previous screen waits unchanged behind it. Navigation Compose seeks these pop
 *   transitions with the gesture progress, so they must not depend on delays to look right half-way.
 */
internal object NavMotion {
    private const val DURATION = 300
    private const val FADE_OUT = 90
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val Linear = CubicBezierEasing(0f, 0f, 1f, 1f)
    private val SharedAxisOffset = 30.dp

    fun fadeThroughEnter(): EnterTransition =
        fadeIn(tween(DURATION - FADE_OUT, delayMillis = FADE_OUT, easing = Linear)) +
            scaleIn(tween(DURATION - FADE_OUT, delayMillis = FADE_OUT, easing = Emphasized), initialScale = 0.92f)

    fun fadeThroughExit(): ExitTransition = fadeOut(tween(FADE_OUT, easing = Linear))

    fun sharedAxisEnter(density: Density): EnterTransition {
        val offset = with(density) { SharedAxisOffset.roundToPx() }
        return slideInHorizontally(tween(DURATION, easing = Emphasized)) { offset } +
            fadeIn(tween(DURATION - FADE_OUT, delayMillis = FADE_OUT, easing = Linear))
    }

    fun sharedAxisExit(density: Density): ExitTransition {
        val offset = with(density) { SharedAxisOffset.roundToPx() }
        return slideOutHorizontally(tween(DURATION, easing = Emphasized)) { -offset } +
            fadeOut(tween(FADE_OUT, easing = Linear))
    }

    /** The screen underneath stays still and fully visible, as the gesture reveals it. */
    fun backEnter(): EnterTransition = EnterTransition.None

    /** Shrinks with the gesture and only fades near the end, so the screen stays readable while peeking. */
    fun backExit(): ExitTransition =
        scaleOut(
            tween(DURATION, easing = Linear),
            targetScale = BACK_SCALE,
            transformOrigin = TransformOrigin.Center,
        ) + fadeOut(tween(DURATION, easing = EmphasizedAccelerate))

    const val BACK_SCALE = 0.9f
    val BackCornerRadius = 28.dp
    val Duration = DURATION
}

/**
 * Rounds the corners of a screen while it leaves, so a screen shrinking under the back gesture reads as a card
 * lifted off the one below. Entering and resting screens keep square corners.
 */
@Composable
internal fun AnimatedContentScope.leavingScreenClip(): Modifier {
    val radius by transition.animateDp(
        transitionSpec = { tween(NavMotion.Duration) },
        label = "screen-corners",
    ) { state -> if (state == EnterExitState.PostExit) NavMotion.BackCornerRadius else 0.dp }
    return if (radius > 0.dp) Modifier.clip(RoundedCornerShape(radius)) else Modifier
}
