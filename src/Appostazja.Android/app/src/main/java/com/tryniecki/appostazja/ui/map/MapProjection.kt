package com.tryniecki.appostazja.ui.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Web Mercator projection matching Google Maps (256 dp world at zoom 0, camera target at the view center,
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
        return (viewWidthPx / 2 + x).toFloat() to (viewHeightPx / 2 + y).toFloat()
    }

    private fun mercatorX(longitude: Double) = (longitude + 180.0) / 360.0 * worldPx

    private fun mercatorY(latitude: Double): Double {
        val sinLat = sin(latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180.0)
        return (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * worldPx
    }
}
