package com.tryniecki.appostazja.ui.map

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.algo.AbstractAlgorithm
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.algo.PreCachingAlgorithmDecorator
import com.google.maps.android.clustering.algo.ScreenBasedAlgorithm

/**
 * Clusters the whole dataset, then returns only the clusters around the viewport.
 *
 * A view-based algorithm clusters just the items in view, so every pan changes its input and the greedy
 * clustering regroups pins: clusters jump and change size although the zoom did not change. Here the
 * clustering depends only on the integer zoom level and is cached per level, so panning returns the very
 * same cluster objects and the renderer keeps their markers. Only rendering is limited to the viewport.
 */
internal class StableViewportAlgorithm<T : ClusterItem>(
    private var viewWidthDp: Int,
    private var viewHeightDp: Int,
) : AbstractAlgorithm<T>(), ScreenBasedAlgorithm<T> {
    private val clustering = PreCachingAlgorithmDecorator(
        NonHierarchicalDistanceBasedAlgorithm<T>().apply { maxDistanceBetweenClusteredItems = CLUSTER_DISTANCE_DP },
    )

    @Volatile
    private var center = LatLng(0.0, 0.0)

    fun updateViewSize(widthDp: Int, heightDp: Int) {
        viewWidthDp = widthDp
        viewHeightDp = heightDp
    }

    override fun getClusters(zoom: Float): Set<Cluster<T>> {
        val target = center
        val width = viewWidthDp.toFloat()
        val height = viewHeightDp.toFloat()
        // Density 1 keeps everything in dp, the unit of the viewport size.
        val projection = MapProjection(target.latitude, target.longitude, zoom, 0f, width, height, density = 1f)
        return clustering.getClusters(zoom).filterTo(HashSet()) { cluster ->
            val (x, y) = projection.toScreen(cluster.position.latitude, cluster.position.longitude)
            x in 0f..width && y in 0f..height
        }
    }

    override fun shouldReclusterOnMapMovement(): Boolean = true

    override fun onCameraChange(position: CameraPosition) {
        center = position.target
    }

    override fun addItem(item: T): Boolean = clustering.addItem(item)
    override fun addItems(items: Collection<T>): Boolean = clustering.addItems(items)
    override fun clearItems() = clustering.clearItems()
    override fun removeItem(item: T): Boolean = clustering.removeItem(item)
    override fun updateItem(item: T): Boolean = clustering.updateItem(item)
    override fun removeItems(items: Collection<T>): Boolean = clustering.removeItems(items)
    override val items: Collection<T> get() = clustering.items
    override var maxDistanceBetweenClusteredItems: Int
        get() = clustering.maxDistanceBetweenClusteredItems
        set(value) {
            clustering.maxDistanceBetweenClusteredItems = value
        }
}

/** How close, in dp on screen, pins have to be to join a cluster. The library's 100 dp left too many pins at once. */
private const val CLUSTER_DISTANCE_DP = 140
