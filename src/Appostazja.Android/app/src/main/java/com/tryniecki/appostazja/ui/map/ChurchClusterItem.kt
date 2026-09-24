package com.tryniecki.appostazja.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.tryniecki.appostazja.api.model.PlaceDto

internal class ChurchClusterItem(val place: PlaceDto) : ClusterItem {
    override val position: LatLng = LatLng(place.latitude, place.longitude)
    override val title: String = place.name
    override val snippet: String = place.address
    override val zIndex: Float? = null
}
