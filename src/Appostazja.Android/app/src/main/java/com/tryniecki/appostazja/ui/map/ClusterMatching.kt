package com.tryniecki.appostazja.ui.map

import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem

/**
 * Pairs each current cluster with the previous cluster that holds exactly the same items.
 *
 * Re-clustering after a small zoom or pan often recomputes a cluster with the same items but a slightly
 * different center. The library treats it as a new cluster and replaces its marker; such a cluster must
 * not animate as if it split or merged.
 *
 * @return current cluster to previous cluster, for clusters whose items did not change.
 */
internal fun <T : ClusterItem> sameItemClusters(
    previous: Iterable<Cluster<T>>,
    current: Iterable<Cluster<T>>,
    minClusterSize: Int,
): Map<Cluster<T>, Cluster<T>> {
    val previousOf = HashMap<T, Cluster<T>>()
    for (cluster in previous) {
        if (cluster.size >= minClusterSize) for (item in cluster.items) previousOf[item] = cluster
    }
    val result = HashMap<Cluster<T>, Cluster<T>>()
    for (cluster in current) {
        if (cluster.size < minClusterSize) continue
        val old = previousOf[cluster.items.first()] ?: continue
        if (old.size == cluster.size && cluster.items.all { previousOf[it] === old }) result[cluster] = old
    }
    return result
}
