package com.tryniecki.appostazja.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ClusterMatchingTest {
    private class Item(val name: String) : ClusterItem {
        override val position: LatLng get() = error("not used")
        override val title: String = name
        override val snippet: String? = null
        override val zIndex: Float? = null
    }

    /** Distinct instances, like the clusters the algorithm recomputes with a new center. */
    private class TestCluster(override val items: Collection<Item>) : Cluster<Item> {
        override val position: LatLng get() = error("not used")
        override val size: Int get() = items.size
    }

    private val a = Item("a")
    private val b = Item("b")
    private val c = Item("c")
    private val d = Item("d")

    @Test
    fun `recomputed cluster with the same items is matched to the previous one`() {
        val old = TestCluster(listOf(a, b, c))
        val recomputed = TestCluster(listOf(c, a, b))

        val matches = sameItemClusters(listOf(old), listOf(recomputed), minClusterSize = 2)

        assertSame(old, matches[recomputed])
    }

    @Test
    fun `clusters that split, merge or lose an item are not matched`() {
        val previous = listOf(TestCluster(listOf(a, b, c)), TestCluster(listOf(d)))
        val lostItem = TestCluster(listOf(a, b))
        val merged = TestCluster(listOf(c, d))

        val matches = sameItemClusters(previous, listOf(lostItem, merged), minClusterSize = 2)

        assertEquals(emptyMap<Cluster<Item>, Cluster<Item>>(), matches)
    }

    @Test
    fun `items rendered as single pins are never treated as clusters`() {
        val matches = sameItemClusters(listOf(TestCluster(listOf(a))), listOf(TestCluster(listOf(a))), minClusterSize = 2)

        assertEquals(0, matches.size)
    }
}
