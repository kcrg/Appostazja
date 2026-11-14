package com.tryniecki.appostazja.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapProjectionTest {
    private fun projection(bearing: Float = 0f, lon: Double = 21.0) =
        MapProjection(52.0, lon, zoom = 10f, bearingDegrees = bearing, viewWidthPx = 1000f, viewHeightPx = 2000f, density = 1f)

    @Test
    fun `camera target lands in the view center`() {
        val (x, y) = projection().toScreen(52.0, 21.0)

        assertEquals(500f, x, 0.01f)
        assertEquals(1000f, y, 0.01f)
    }

    @Test
    fun `with a top content padding the camera target sits in the middle of the padded area`() {
        // A status bar padding of 100 px moves the map center 50 px down, as GoogleMap.setPadding does.
        val padded = MapProjection(
            52.0, 21.0, zoom = 10f, bearingDegrees = 0f, viewWidthPx = 1000f, viewHeightPx = 2000f, density = 1f,
            paddingPx = MapPaddingPx(left = 0f, top = 100f, right = 0f, bottom = 0f),
        )

        val (x, y) = padded.toScreen(52.0, 21.0)
        val (_, yNearby) = padded.toScreen(52.1, 21.0)
        val (_, yNearbyUnpadded) = projection().toScreen(52.1, 21.0)

        assertEquals(500f, x, 0.01f)
        assertEquals(1050f, y, 0.01f)
        assertEquals(50f, yNearby - yNearbyUnpadded, 0.01f)
    }

    @Test
    fun `one degree of longitude spans the expected pixels and north is up`() {
        val world = 256.0 * 1024 // zoom 10, density 1
        val (east, _) = projection().toScreen(52.0, 22.0)
        val (_, north) = projection().toScreen(52.5, 21.0)

        assertEquals(500 + world / 360, east.toDouble(), 0.01)
        assert(north < 1000f) { "north should be above the center, was $north" }
    }

    @Test
    fun `with the map facing east a point east of the target appears above it`() {
        val (x, y) = projection(bearing = 90f).toScreen(52.0, 21.1)

        assertEquals(500f, x, 0.5f)
        assert(y < 1000f) { "expected above center, was $y" }
    }

    @Test
    fun `points across the antimeridian take the short way`() {
        val (x, _) = projection(lon = 179.9).toScreen(52.0, -179.9)

        val world = 256.0 * 1024
        assertEquals(500 + world * 0.2 / 360, x.toDouble(), 0.5)
    }
}
