package com.tryniecki.appostazja.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigationevent.NavigationEvent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Material motion patterns for moving between screens (m3.material.io/styles/motion/transitions):
 * - fade through between top-level tabs, which are not related to each other; the two screens overlap
 *   slightly instead of leaving an empty frame between them;
 * - shared axis X within a tab, forward into a deeper screen;
 * - going back follows the predictive back gesture like the system's cross-activity animation: the screen
 *   slides away under the finger with rounded corners while the previous one comes in from the left behind a
 *   fading scrim. Navigation Compose seeks these pop transitions with the gesture progress, so they are linear
 *   and without delays: half-way through the gesture they are half-way through the animation.
 */
internal object NavMotion {
    const val DURATION = 300
    private const val TAB_DURATION = 250
    private const val TAB_FADE_OUT = 120
    private const val TAB_FADE_IN_DELAY = 50
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val SharedAxisOffset = 30.dp
    const val BACK_PARALLAX = 0.25f
    const val BACK_SCRIM = 0.32f
    val BackCornerRadius = 28.dp

    // Tabs: the outgoing tab fades quickly, the incoming one fades in over it with a small scale-up.
    val TabFadeInDelay = TAB_FADE_IN_DELAY
    val TabFadeIn = TAB_DURATION - TAB_FADE_IN_DELAY

    fun fadeThroughEnter(): EnterTransition =
        fadeIn(tween(TabFadeIn, delayMillis = TAB_FADE_IN_DELAY, easing = LinearEasing)) +
            scaleIn(tween(TabFadeIn, delayMillis = TAB_FADE_IN_DELAY, easing = Emphasized), initialScale = 0.96f)

    fun fadeThroughExit(): ExitTransition = fadeOut(tween(TAB_FADE_OUT, easing = LinearEasing))

    fun sharedAxisEnter(density: Density): EnterTransition {
        val offset = with(density) { SharedAxisOffset.roundToPx() }
        return slideInHorizontally(tween(DURATION, easing = Emphasized)) { offset } +
            fadeIn(tween(DURATION - 90, delayMillis = 90, easing = LinearEasing))
    }

    fun sharedAxisExit(density: Density): ExitTransition {
        val offset = with(density) { SharedAxisOffset.roundToPx() }
        return slideOutHorizontally(tween(DURATION, easing = Emphasized)) { -offset } +
            fadeOut(tween(90, easing = LinearEasing))
    }

    /**
     * The previous screen comes in from a quarter of the way on the side the finger is moving away from, at a
     * slower pace than the screen leaving (parallax), settling from a slight scale-down.
     */
    fun backEnter(swipeEdge: Int = NavigationEvent.EDGE_LEFT): EnterTransition {
        val side = if (swipeEdge == NavigationEvent.EDGE_RIGHT) 1 else -1
        return slideInHorizontally(tween(DURATION, easing = LinearEasing)) { (side * it * BACK_PARALLAX).toInt() } +
            scaleIn(tween(DURATION, easing = LinearEasing), initialScale = BACK_ENTER_SCALE)
    }

    /** Follows the finger towards the edge it moves to, shrinking a little so it lifts off the screen below. */
    fun backExit(swipeEdge: Int = NavigationEvent.EDGE_LEFT): ExitTransition {
        val side = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
        return slideOutHorizontally(tween(DURATION, easing = LinearEasing)) { side * it } +
            scaleOut(tween(DURATION, easing = LinearEasing), targetScale = BACK_EXIT_SCALE)
    }

    private const val BACK_ENTER_SCALE = 0.96f
    private const val BACK_EXIT_SCALE = 0.92f
}

/** Whether the transition being shown is going back; screens add their back decoration only then. */
internal class NavDirection {
    var goingBack = false
}

/**
 * Back decoration of a screen: rounded corners while it slides away, and a scrim over the screen revealed
 * underneath that clears as it comes in, so the two read as separate layers.
 */
@Composable
internal fun AnimatedContentScope.backNavigationDecoration(direction: NavDirection): Modifier {
    val radius by transition.animateDp(
        transitionSpec = { tween(NavMotion.DURATION, easing = LinearEasing) },
        label = "screen-corners",
    ) { state -> if (state == EnterExitState.PostExit && direction.goingBack) NavMotion.BackCornerRadius else 0.dp }
    val scrim by transition.animateFloat(
        transitionSpec = { tween(NavMotion.DURATION, easing = LinearEasing) },
        label = "screen-scrim",
    ) { state -> if (state == EnterExitState.PreEnter && direction.goingBack) NavMotion.BACK_SCRIM else 0f }
    val clipped = if (radius > 0.dp) Modifier.clip(RoundedCornerShape(radius)) else Modifier
    return clipped.drawWithContent {
        drawContent()
        if (scrim > 0f) drawRect(Color.Black, alpha = scrim)
    }
}
