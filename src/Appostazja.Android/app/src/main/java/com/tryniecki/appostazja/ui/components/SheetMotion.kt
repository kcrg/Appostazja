package com.tryniecki.appostazja.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * Runs [content] with [SheetMotionScheme]. ModalBottomSheet opens and settles with the theme's default
 * spatial spec; any spring there overshoots on a tall sheet, so sheets get a tween instead.
 */
@Composable
internal fun SheetMotion(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        motionScheme = SheetMotionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/** Spatial motion as tweens with the Material emphasized-decelerate curve: no spring, no overshoot. */
private object SheetMotionScheme : MotionScheme {
    private val effects = MotionScheme.standard()
    private val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = tween(400, easing = emphasizedDecelerate)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(300, easing = emphasizedDecelerate)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(500, easing = emphasizedDecelerate)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = effects.defaultEffectsSpec()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = effects.fastEffectsSpec()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = effects.slowEffectsSpec()
}
