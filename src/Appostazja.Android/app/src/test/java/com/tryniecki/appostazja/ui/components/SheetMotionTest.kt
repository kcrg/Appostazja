package com.tryniecki.appostazja.ui.components

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetMotionTest {
    private val spec = SkipWhenSettled(tween<Float>(400))

    @Test
    fun `a sheet already at its anchor settles at once instead of waiting out the tween`() {
        // Sheets settle after every fling of their list; sub-pixel leftovers count as already there.
        val animation = TargetBasedAnimation(spec, Float.VectorConverter, initialValue = 1200.3f, targetValue = 1200f)

        assertEquals(0L, animation.durationNanos)
        assertEquals(1200f, animation.getValueFromNanos(0L), 0f)
    }

    @Test
    fun `a sheet that has to move keeps the full tween`() {
        val animation = TargetBasedAnimation(spec, Float.VectorConverter, initialValue = 2400f, targetValue = 1200f)

        assertEquals(400_000_000L, animation.durationNanos)
        assertEquals(1200f, animation.getValueFromNanos(animation.durationNanos), 0f)
    }
}
