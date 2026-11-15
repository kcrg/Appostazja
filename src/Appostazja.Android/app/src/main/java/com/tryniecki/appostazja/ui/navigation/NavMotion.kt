package com.tryniecki.appostazja.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Moving between screens, on the theme's expressive motion scheme:
 * - top-level tabs fade through: the old tab fades out while the new one fades in with a gentle scale-up;
 * - within a tab, shared axis X: the deeper screen comes in from the end over a short fixed distance, and going
 *   back mirrors it. Position is the main cue, fading only a secondary one. Back, predictive back included, uses
 *   the same mirrored motion.
 * The container transform between a FAB and the screen it opens has its own motion (see FabMorph).
 */
internal class NavMotionSpecs(
    private val hierarchySpatial: FiniteAnimationSpec<IntOffset>,
    private val hierarchyEffects: FiniteAnimationSpec<Float>,
    private val hierarchySlideDistancePx: Int,
    val tabEnter: FiniteAnimationSpec<Float>,
    private val tabExit: FiniteAnimationSpec<Float>,
    private val tabSpatial: FiniteAnimationSpec<Float>,
) {
    fun tabEnterTransition(): EnterTransition =
        fadeIn(tabEnter) + scaleIn(tabSpatial, initialScale = TAB_INITIAL_SCALE)

    fun tabExitTransition(): ExitTransition = fadeOut(tabExit)

    fun hierarchyEnter(forward: Boolean): EnterTransition {
        val distance = if (forward) hierarchySlideDistancePx else -hierarchySlideDistancePx
        return slideInHorizontally(hierarchySpatial) { distance } +
            fadeIn(hierarchyEffects, initialAlpha = HIERARCHY_INITIAL_ALPHA)
    }

    /** Fades out completely: a screen disposed while still partly visible snaps away for a frame. */
    fun hierarchyExit(forward: Boolean): ExitTransition {
        val distance = if (forward) hierarchySlideDistancePx else -hierarchySlideDistancePx
        return slideOutHorizontally(hierarchySpatial) { -distance } + fadeOut(hierarchyEffects, targetAlpha = 0f)
    }

    private companion object {
        const val TAB_INITIAL_SCALE = 0.96f
        const val HIERARCHY_INITIAL_ALPHA = 0.93f
    }
}

@Composable
internal fun navMotionSpecs(): NavMotionSpecs {
    val motion = MaterialTheme.motionScheme
    return NavMotionSpecs(
        hierarchySpatial = motion.defaultSpatialSpec(),
        hierarchyEffects = motion.fastEffectsSpec(),
        hierarchySlideDistancePx = with(LocalDensity.current) { HierarchySlideDistance.roundToPx() },
        tabEnter = motion.defaultEffectsSpec(),
        tabExit = motion.fastEffectsSpec(),
        tabSpatial = motion.fastSpatialSpec(),
    )
}

/** A fixed distance, so moving deeper looks the same on phones, tablets and foldables. */
private val HierarchySlideDistance = 64.dp

/** The FAB container transform. */
internal object NavMotion {
    const val DURATION = 300
    val CardCornerRadius = 28.dp
}
