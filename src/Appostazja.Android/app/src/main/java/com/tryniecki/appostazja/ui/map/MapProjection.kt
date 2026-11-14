package com.tryniecki.appostazja.ui.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Web Mercator projection matching Google Maps (256 dp world at zoom 0, camera target at the center of the view minus the map content padding,
 * bearing clockwise from north, no tilt). Lets the transition overlay place sprites without calling
 * `GoogleMap.projection`, which is a synchronous call into the map renderer.
 */
internal class MapProjection(
    targetLatitude: Double,
    targetLongitude: Double,
    zoom: Float,
    bearingDegrees: Float,
    private val viewWidthPx: Float,
    private val viewHeightPx: Float,
    density: Float,
    /** GoogleMap contentPadding in px; the map centers its camera target inside the padded area. */
    private val paddingPx: MapPaddingPx = MapPaddingPx.None,
) {
    private val worldPx = 256.0 * density * 2.0.pow(zoom.toDouble())
    private val targetX = mercatorX(targetLongitude)
    private val targetY = mercatorY(targetLatitude)
    private val bearing = bearingDegrees * PI / 180.0
    private val cosBearing = cos(bearing)
    private val sinBearing = sin(bearing)

    /** Screen position in px of a coordinate, relative to the top-left corner of the map view. */
    fun toScreen(latitude: Double, longitude: Double): Pair<Float, Float> {
        var dx = mercatorX(longitude) - targetX
        // Take the short way around the antimeridian.
        if (dx > worldPx / 2) dx -= worldPx else if (dx < -worldPx / 2) dx += worldPx
        val dy = mercatorY(latitude) - targetY
        val x = dx * cosBearing + dy * sinBearing
        val y = -dx * sinBearing + dy * cosBearing
        val centerX = paddingPx.left + (viewWidthPx - paddingPx.left - paddingPx.right) / 2
        val centerY = paddingPx.top + (viewHeightPx - paddingPx.top - paddingPx.bottom) / 2
        return (centerX + x).toFloat() to (centerY + y).toFloat()
    }

    private fun mercatorX(longitude: Double) = (longitude + 180.0) / 360.0 * worldPx

    private fun mercatorY(latitude: Double): Double {
        val sinLat = sin(latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180.0)
        return (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * worldPx
    }
}

internal data class MapPaddingPx(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val None = MapPaddingPx(0f, 0f, 0f, 0f)
    }
}
