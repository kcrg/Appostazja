package com.tryniecki.appostazja.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
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
    private val map: GoogleMap,
    manager: ClusterManager<ChurchClusterItem>,
    private val colors: MarkerColors,
    private val transitions: MarkerTransitions,
) : DefaultClusterRenderer<ChurchClusterItem>(context, map, manager) {
    private val resources = context.resources
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val pins = mutableMapOf<Int, Icon>()
    private val clusterIcons = LruCache<ClusterIcon, Icon>(96)

    // State for the current cluster transition; see MarkerTransitions.
    private var lastClusters: Set<Cluster<ChurchClusterItem>> = emptySet()
    private var lastZoom = Float.NaN
    private var lastBounds: LatLngBounds? = null
    private var visibleBounds: LatLngBounds? = null
    private var enterOrigins: Map<ChurchClusterItem, LatLng> = emptyMap()
    /** New cluster to the previous cluster with the same items; only its center moved. */
    private var unchanged: Map<Cluster<ChurchClusterItem>, Cluster<ChurchClusterItem>> = emptyMap()
    private var persisting: Set<Cluster<ChurchClusterItem>> = emptySet()
    private var popDelayNanos = 0L
    /** Clusters that pins fly into when zooming out; only these pop in. */
    private val mergeTargets = HashSet<Cluster<ChurchClusterItem>>()
    private val pendingEnters = HashSet<Any>()

    init {
        minClusterSize = 2
        // The library animation moves real markers every frame, which is too slow; MarkerTransitions draws it.
        setAnimation(false)
    }

    override fun onClustersChanged(clusters: Set<Cluster<ChurchClusterItem>>) {
        val camera = map.cameraPosition
        val bounds = map.projection.visibleRegion.latLngBounds
        enterOrigins = emptyMap()
        unchanged = emptyMap()
        persisting = emptySet()
        popDelayNanos = 0L
        mergeTargets.clear()
        pendingEnters.clear()
        if (transitions.enabled) {
            transitions.begin()
            unchanged = sameItemClusters(lastClusters, clusters, minClusterSize)
            persisting = unchanged.values.toHashSet()
            val zoomChange = if (lastZoom.isNaN()) 0f else camera.zoom - lastZoom
            if (zoomChange > ZOOM_EPSILON) {
                enterOrigins = originsForSplit()
                addSplitHolds(clusters)
            } else if (zoomChange < -ZOOM_EPSILON) {
                addMergeExits(clusters)
                popDelayNanos = transitions.scaled(MarkerTransitions.EXIT_MS * 3 / 5)
            }
        }
        lastClusters = clusters
        lastZoom = camera.zoom
        lastBounds = bounds
        visibleBounds = bounds
        super.onClustersChanged(clusters)
    }

    /** Zooming in: every item of a previously visible cluster starts at that cluster. */
    private fun originsForSplit(): Map<ChurchClusterItem, LatLng> {
        val bounds = lastBounds ?: return emptyMap()
        val origins = HashMap<ChurchClusterItem, LatLng>()
        for (cluster in lastClusters) {
            if (cluster.size < minClusterSize || cluster in persisting || !bounds.contains(cluster.position)) continue
            for (item in cluster.items) origins[item] = cluster.position
        }
        return origins
    }

    /**
     * Zooming in: the library removes a splitting cluster before its pins exist. Keep drawing it until the
     * pins fly out from under it, then let it shrink away.
     */
    private fun addSplitHolds(clusters: Set<Cluster<ChurchClusterItem>>) {
        val bounds = lastBounds ?: return
        val duration = transitions.scaled(MarkerTransitions.EXIT_MS)
        for (previous in lastClusters) {
            if (previous.size < minClusterSize || previous in clusters || previous in persisting ||
                !bounds.contains(previous.position)
            ) {
                continue
            }
            if (!transitions.canAdd()) return
            transitions.add(
                MarkerTransitions.Sprite(
                    bitmap = clusterIcon(previous).bitmap,
                    anchorX = 0.5f,
                    anchorY = 0.5f,
                    from = previous.position,
                    to = previous.position,
                    kind = MarkerTransitions.Kind.Exit,
                    delayNanos = 0L,
                    durationNanos = duration,
                    marker = null,
                ),
            )
        }
    }

    /** Zooming out: previously visible markers fly into the cluster that now contains them. */
    private fun addMergeExits(clusters: Set<Cluster<ChurchClusterItem>>) {
        val bounds = lastBounds ?: return
        val clusterOf = HashMap<ChurchClusterItem, Cluster<ChurchClusterItem>>()
        for (cluster in clusters) {
            if (cluster.size >= minClusterSize) for (item in cluster.items) clusterOf[item] = cluster
        }
        val duration = transitions.scaled(MarkerTransitions.EXIT_MS)
        for (previous in lastClusters) {
            if (previous in persisting || !bounds.contains(previous.position) || !transitions.canAdd()) continue
            // Items that stay single keep their marker, so they have nowhere to go.
            val target = clusterOf[previous.items.first()] ?: continue
            if (target == previous) continue
            mergeTargets += target
            val isCluster = previous.size >= minClusterSize
            val icon = if (isCluster) clusterIcon(previous) else pin(previous.items.first())
            transitions.add(
                MarkerTransitions.Sprite(
                    bitmap = icon.bitmap,
                    anchorX = 0.5f,
                    anchorY = if (isCluster) 0.5f else 1f,
                    from = previous.position,
                    to = target.position,
                    kind = MarkerTransitions.Kind.Exit,
                    delayNanos = 0L,
                    durationNanos = duration,
                    marker = null,
                ),
            )
        }
    }

    private fun hideForEnter(key: Any, position: LatLng, markerOptions: MarkerOptions) {
        if (transitions.canAdd() && visibleBounds?.contains(position) == true) {
            markerOptions.alpha(0f)
            pendingEnters += key
        }
    }

    private fun addEnter(
        icon: Icon,
        anchorY: Float,
        origin: LatLng?,
        position: LatLng,
        marker: Marker,
        mergeTarget: Boolean = false,
    ) {
        val from = origin?.takeIf { it != position }
        val kind = when {
            from != null -> MarkerTransitions.Kind.Move
            mergeTarget -> MarkerTransitions.Kind.Pop
            else -> MarkerTransitions.Kind.Fade
        }
        transitions.add(
            MarkerTransitions.Sprite(
                bitmap = icon.bitmap,
                anchorX = 0.5f,
                anchorY = anchorY,
                from = from ?: position,
                to = position,
                kind = kind,
                delayNanos = if (kind == MarkerTransitions.Kind.Pop) popDelayNanos else 0L,
                durationNanos = transitions.scaled(
                    when (kind) {
                        MarkerTransitions.Kind.Move -> MarkerTransitions.MOVE_MS
                        MarkerTransitions.Kind.Pop -> MarkerTransitions.POP_MS
                        else -> MarkerTransitions.FADE_MS
                    },
                ),
                marker = marker,
            ),
        )
    }

    override fun onBeforeClusterItemRendered(item: ChurchClusterItem, markerOptions: MarkerOptions) {
        super.onBeforeClusterItemRendered(item, markerOptions)
        markerOptions.icon(pin(item).descriptor).anchor(0.5f, 1f)
        hideForEnter(item, item.position, markerOptions)
    }

    override fun onClusterItemRendered(clusterItem: ChurchClusterItem, marker: Marker) {
        super.onClusterItemRendered(clusterItem, marker)
        if (pendingEnters.remove(clusterItem)) {
            addEnter(pin(clusterItem), 1f, enterOrigins[clusterItem], clusterItem.position, marker)
        }
    }

    override fun onBeforeClusterRendered(cluster: Cluster<ChurchClusterItem>, markerOptions: MarkerOptions) {
        markerOptions.icon(clusterIcon(cluster).descriptor).anchor(0.5f, 0.5f)
            .title(clusterTitle(cluster.size))
        hideForEnter(cluster, cluster.position, markerOptions)
    }

    override fun onClusterRendered(cluster: Cluster<ChurchClusterItem>, marker: Marker) {
        super.onClusterRendered(cluster, marker)
        if (!pendingEnters.remove(cluster)) return
        val previous = unchanged[cluster]
        if (previous != null) {
            // Same items, recomputed center: glide to the new spot without any split or merge effect.
            transitions.add(
                MarkerTransitions.Sprite(
                    bitmap = clusterIcon(cluster).bitmap,
                    anchorX = 0.5f,
                    anchorY = 0.5f,
                    from = previous.position,
                    to = cluster.position,
                    kind = MarkerTransitions.Kind.Move,
                    delayNanos = 0L,
                    durationNanos = transitions.scaled(MarkerTransitions.SHIFT_MS),
                    marker = marker,
                    steady = true,
                ),
            )
        } else {
            addEnter(
                clusterIcon(cluster), 0.5f, enterOrigins[cluster.items.first()], cluster.position, marker,
                mergeTarget = cluster in mergeTargets,
            )
        }
    }

    override fun onRemove() {
        transitions.clear()
        super.onRemove()
    }

    override fun onClusterUpdated(cluster: Cluster<ChurchClusterItem>, marker: Marker) {
        super.onClusterUpdated(cluster, marker)
        val title = clusterTitle(cluster.size)
        if (marker.title != title) marker.title = title
    }

    private fun clusterTitle(size: Int): String =
        resources.getQuantityString(R.plurals.map_cluster_count, size, size)

    override fun getDescriptorForCluster(cluster: Cluster<ChurchClusterItem>): BitmapDescriptor =
        clusterIcon(cluster).descriptor

    private fun clusterIcon(cluster: Cluster<ChurchClusterItem>): Icon {
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


    private fun drawCluster(icon: ClusterIcon): Icon {
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
        return Icon(bitmap)
    }

    private fun drawBar(canvas: Canvas, left: Float, start: Int, end: Int, color: Int) {
        if (end == start) return
        paint.color = color
        canvas.drawRect(left + start, 31f, left + end, 34f, paint)
    }

    private fun pin(item: ChurchClusterItem): Icon {
        // The API decides the verdict so every client colours pins the same way.
        val color = when (item.place.tone) {
            "positive" -> colors.good
            "negative" -> colors.bad
            else -> colors.neutral
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
            Icon(bitmap)
        }
    }

    /** The same pixels as a map marker icon and as a sprite for MarkerTransitions. */
    private class Icon(bitmap: Bitmap) {
        val bitmap: ImageBitmap = bitmap.asImageBitmap()
        val descriptor: BitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private companion object {
        const val ZOOM_EPSILON = 0.01f
    }

    private data class ClusterIcon(val size: Int, val easy: Int, val easyAndMedium: Int, val hasRatings: Boolean)
}
