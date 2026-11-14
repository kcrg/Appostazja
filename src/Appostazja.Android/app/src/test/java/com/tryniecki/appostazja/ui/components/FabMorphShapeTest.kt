package com.tryniecki.appostazja.ui.components

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class FabMorphShapeTest {
    // 1 dp = 1 px keeps the numbers readable: the FAB is 56 wide with 16 corners, the window 400 wide.
    private val density = Density(1f)
    private val window = 400f

    @Test
    fun `the button keeps its corners and the full-width screen is square`() {
        assertEquals(16f, fabMorphCornerRadius(56f, 56f, window, density), 0.001f)
        assertEquals(0f, fabMorphCornerRadius(400f, 800f, window, density), 0.001f)
    }

    @Test
    fun `corners straighten in proportion to how far the container has grown`() {
        // Half way from button width to window width.
        assertEquals(8f, fabMorphCornerRadius(228f, 500f, window, density), 0.001f)
    }

    @Test
    fun `a container smaller than the button never gets more than half its side`() {
        // The landing dip makes it a little smaller than the button: still the button's radius...
        assertEquals(16f, fabMorphCornerRadius(50f, 50f, window, density), 0.001f)
        // ...but a sliver of a container cannot have corners larger than itself.
        assertEquals(10f, fabMorphCornerRadius(20f, 56f, window, density), 0.001f)
    }
}
