package com.tryniecki.appostazja.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.LruCache
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.tryniecki.appostazja.R
import kotlin.math.roundToInt

internal data class MarkerColors(
    val container: Int,
    val onContainer: Int,
    val pin: Int,
    val onPin: Int,
    val outline: Int,
    val shadow: Int,
    val good: Int,
    val neutral: Int,
    val bad: Int,
)

internal class ChurchClusterRenderer(
    context: Context,
    map: GoogleMap,
    manager: ClusterManager<ChurchClusterItem>,
    private val colors: MarkerColors,
) : DefaultClusterRenderer<ChurchClusterItem>(context, map, manager) {
    private val resources = context.resources
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val pins = mutableMapOf<Int, BitmapDescriptor>()
    private val clusterIcons = LruCache<ClusterIcon, BitmapDescriptor>(96)

    init {
        minClusterSize = 2
        setAnimation(false)
    }

    override fun onBeforeClusterItemRendered(item: ChurchClusterItem, markerOptions: MarkerOptions) {
        super.onBeforeClusterItemRendered(item, markerOptions)
        markerOptions.icon(pin(item)).anchor(0.5f, 1f)
    }

    override fun onBeforeClusterRendered(cluster: Cluster<ChurchClusterItem>, markerOptions: MarkerOptions) {
        markerOptions.icon(getDescriptorForCluster(cluster)).anchor(0.5f, 0.5f)
            .title(clusterTitle(cluster.size))
    }

    override fun onClusterUpdated(cluster: Cluster<ChurchClusterItem>, marker: Marker) {
        super.onClusterUpdated(cluster, marker)
        val title = clusterTitle(cluster.size)
        if (marker.title != title) marker.title = title
    }

    private fun clusterTitle(size: Int): String =
        resources.getQuantityString(R.plurals.map_cluster_count, size, size)

    override fun getDescriptorForCluster(cluster: Cluster<ChurchClusterItem>): BitmapDescriptor {
        var easy = 0
        var medium = 0
        var hard = 0
        for (item in cluster.items) {
            val ratings = item.place.ratings
            easy += ratings.positive
            medium += ratings.neutral
            hard += ratings.negative
        }
        val total = easy + medium + hard
        // Quantize the bar to match its display width and reuse icons across viewports.
        val key = ClusterIcon(
            cluster.size,
            if (total == 0) 0 else (24.0 * easy / total).roundToInt(),
            if (total == 0) 0 else (24.0 * (easy + medium) / total).roundToInt(),
            total != 0,
        )
        return clusterIcons.get(key) ?: drawCluster(key).also { clusterIcons.put(key, it) }
    }

    private fun drawCluster(icon: ClusterIcon): BitmapDescriptor {
        val width = when {
            icon.size < 100 -> 48f
            icon.size < 1_000 -> 56f
            else -> 64f
        }
        val height = 48f
        val bitmap = Bitmap.createBitmap((width * density).roundToInt(), (height * density).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val center = width / 2
        paint.style = Paint.Style.FILL
        paint.color = colors.shadow
        canvas.drawRoundRect(2f, 7f, width - 2f, 45f, 16f, 16f, paint)
        paint.color = colors.outline
        canvas.drawRoundRect(2f, 5f, width - 2f, 43f, 16f, 16f, paint)
        paint.color = colors.container
        canvas.drawRoundRect(3f, 6f, width - 3f, 42f, 15f, 15f, paint)

        paint.color = colors.onContainer
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 14f
        val textCenter = if (icon.hasRatings) 21f else 24f
        canvas.drawText(icon.size.toString(), center, textCenter - (paint.ascent() + paint.descent()) / 2, paint)
        if (icon.hasRatings) {
            val barStart = center - 12f
            val saved = canvas.save()
            path.rewind()
            path.addRoundRect(barStart, 31f, barStart + 24f, 34f, 1.5f, 1.5f, Path.Direction.CW)
            canvas.clipPath(path)
            drawBar(canvas, barStart, 0, icon.easy, colors.good)
            drawBar(canvas, barStart, icon.easy, icon.easyAndMedium, colors.neutral)
            drawBar(canvas, barStart, icon.easyAndMedium, 24, colors.bad)
            canvas.restoreToCount(saved)
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun drawBar(canvas: Canvas, left: Float, start: Int, end: Int, color: Int) {
        if (end == start) return
        paint.color = color
        canvas.drawRect(left + start, 31f, left + end, 34f, paint)
    }

    private fun pin(item: ChurchClusterItem): BitmapDescriptor {
        val color = when {
            item.place.score < -0.2 -> colors.bad
            item.place.score <= 0.2 -> colors.neutral
            else -> colors.good
        }
        return pins.getOrPut(color) {
            val bitmap = Bitmap.createBitmap((48 * density).roundToInt(), (48 * density).roundToInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(density, density)
            path.rewind()
            path.moveTo(24f, 5f)
            path.cubicTo(14f, 5f, 7f, 12f, 7f, 21f)
            path.cubicTo(7f, 31f, 16f, 39f, 22f, 45f)
            path.quadTo(24f, 47f, 26f, 45f)
            path.cubicTo(32f, 39f, 41f, 31f, 41f, 21f)
            path.cubicTo(41f, 12f, 34f, 5f, 24f, 5f)
            path.close()
            paint.style = Paint.Style.FILL
            paint.color = colors.shadow
            canvas.save()
            canvas.translate(0f, 1.5f)
            canvas.drawPath(path, paint)
            canvas.restore()
            paint.color = colors.pin
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = colors.outline
            canvas.drawPath(path, paint)

            path.rewind()
            path.moveTo(17f, 29f)
            path.lineTo(17f, 22f)
            path.lineTo(24f, 17f)
            path.lineTo(31f, 22f)
            path.lineTo(31f, 29f)
            path.moveTo(16f, 29f)
            path.lineTo(32f, 29f)
            path.moveTo(24f, 12f)
            path.lineTo(24f, 17f)
            path.moveTo(21.5f, 14.5f)
            path.lineTo(26.5f, 14.5f)
            path.moveTo(24f, 25f)
            path.lineTo(24f, 29f)
            paint.strokeWidth = 1.8f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = colors.onPin
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(24f, 35f, 2.5f, paint)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private data class ClusterIcon(val size: Int, val easy: Int, val easyAndMedium: Int, val hasRatings: Boolean)
}
