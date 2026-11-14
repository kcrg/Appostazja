package com.tryniecki.appostazja.ui.components

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import kotlin.math.abs

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

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = settledTween(400)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = settledTween(300)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = settledTween(500)

    private fun <T> settledTween(durationMillis: Int): FiniteAnimationSpec<T> =
        SkipWhenSettled(tween(durationMillis, easing = emphasizedDecelerate))
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = effects.defaultEffectsSpec()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = effects.fastEffectsSpec()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = effects.slowEffectsSpec()
}

/**
 * Finishes at once when the value is already at its target. A sheet settles after every fling of its list,
 * even when it is not moving; a plain tween would still run its full duration there, and the list's
 * overscroll stretch only gets the leftover velocity once that animation ends, so it showed up late.
 */
internal class SkipWhenSettled<T>(private val spec: FiniteAnimationSpec<T>) : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<T, V>): VectorizedFiniteAnimationSpec<V> {
        val animation = spec.vectorize(converter)
        fun isSettled(initialValue: V, targetValue: V) =
            isSettled(converter.convertFromVector(initialValue), converter.convertFromVector(targetValue))
        return object : VectorizedFiniteAnimationSpec<V> {
            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
                if (isSettled(initialValue, targetValue)) 0L
                else animation.getDurationNanos(initialValue, targetValue, initialVelocity)

            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                if (isSettled(initialValue, targetValue)) targetValue
                else animation.getValueFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)

            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                // When settled there is no distance to cover, so the tween's own velocity is already zero.
                animation.getVelocityFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)
        }
    }
}

/** Equal, or for Float offsets (sheet positions in pixels) within half a pixel. */
internal fun isSettled(initialValue: Any?, targetValue: Any?): Boolean =
    if (initialValue is Float && targetValue is Float) abs(initialValue - targetValue) < 0.5f
    else initialValue == targetValue
