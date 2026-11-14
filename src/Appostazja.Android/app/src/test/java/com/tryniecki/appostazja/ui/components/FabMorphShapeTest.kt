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
    fun `a screen that has just started to shrink is already rounded like a card`() {
        // 5 % of the way from window width (400) to button width (56): half of the 28 card radius.
        assertEquals(14f, fabMorphCornerRadius(382.8f, 800f, window, density), 0.01f)
        // 10 % of the way: the full card radius.
        assertEquals(28f, fabMorphCornerRadius(365.6f, 800f, window, density), 0.01f)
    }

    @Test
    fun `the card radius eases to the button radius as the container nears button size`() {
        // Half way: 16 + 12 * 0.5 / 0.9.
        assertEquals(22.667f, fabMorphCornerRadius(228f, 500f, window, density), 0.01f)
    }

    @Test
    fun `a container smaller than the button never gets more than half its side`() {
        // The landing dip makes it a little smaller than the button: still the button's radius...
        assertEquals(16f, fabMorphCornerRadius(50f, 50f, window, density), 0.001f)
        // ...but a sliver of a container cannot have corners larger than itself.
        assertEquals(10f, fabMorphCornerRadius(20f, 56f, window, density), 0.001f)
    }
}
