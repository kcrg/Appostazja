using Appostazja.Maui.Controls;
using Appostazja.Maui.Models;
using Android.Gms.Maps;
using Android.Gms.Maps.Model;
using Android.Gms.Maps.Utils.Clustering;
using Android.Gms.Maps.Utils.Clustering.Algorithm;

namespace Appostazja.Maui.Platforms.Android.Maps;

internal sealed class AndroidMapClusterController : IDisposable
{
    private const int ClusterZoomStep = 2;
    private const float MaximumClusterZoom = 18;

    private readonly Dictionary<string, ChurchClusterItem> itemsById =
        new(StringComparer.Ordinal);
    private readonly HashSet<string> incomingIds = new(StringComparer.Ordinal);
    private readonly List<ChurchClusterItem> additions = [];
    private readonly List<ChurchClusterItem> removals = [];

    private GoogleMap? map;
    private ClusterManager? clusterManager;
    private ContinuousZoomEuclideanCentroidAlgorithm? algorithm;
    private ChurchClusterRenderer? renderer;
    private ClusterInteractionListener? interactionListener;

    public void Attach(
        global::Android.Content.Context context,
        GoogleMap googleMap,
        ClusteredMap virtualMap)
    {
        Detach();

        map = googleMap;
        clusterManager = new ClusterManager(context, googleMap);
        algorithm = new ContinuousZoomEuclideanCentroidAlgorithm
        {
            MaxDistanceBetweenClusteredItems = 160,
        };
        renderer = new ChurchClusterRenderer(context, googleMap, clusterManager)
        {
            MinClusterSize = 2,
        };
        interactionListener = new ClusterInteractionListener(virtualMap, googleMap);

        clusterManager.Algorithm = algorithm;
        clusterManager.Renderer = renderer;
        clusterManager.SetAnimation(true);
        clusterManager.SetOnClusterClickListener(interactionListener);
        clusterManager.SetOnClusterItemClickListener(interactionListener);

        googleMap.SetOnCameraIdleListener(clusterManager);
        googleMap.SetOnMarkerClickListener(clusterManager);

        SyncMarkers(virtualMap.Markers);
    }

    public void SyncMarkers(IReadOnlyList<ChurchMapPin> markers)
    {
        ClusterManager? manager = clusterManager;
        if (manager is null)
        {
            return;
        }

        incomingIds.Clear();
        additions.Clear();
        removals.Clear();
        bool changed = false;

        foreach (ChurchMapPin marker in markers)
        {
            if (!incomingIds.Add(marker.Id))
            {
                continue;
            }

            if (itemsById.TryGetValue(marker.Id, out ChurchClusterItem? existing))
            {
                if (existing.UpdateFrom(marker))
                {
                    changed |= manager.UpdateItem(existing);
                }

                continue;
            }

            var item = new ChurchClusterItem(marker);
            itemsById.Add(marker.Id, item);
            additions.Add(item);
            changed = true;
        }

        foreach ((string id, ChurchClusterItem item) in itemsById)
        {
            if (!incomingIds.Contains(id))
            {
                removals.Add(item);
            }
        }

        if (removals.Count > 0)
        {
            manager.RemoveItems(removals);
            foreach (ChurchClusterItem item in removals)
            {
                itemsById.Remove(item.Marker.Id);
                item.Dispose();
            }

            changed = true;
        }

        if (additions.Count > 0)
        {
            manager.AddItems(additions);
        }

        if (changed)
        {
            manager.Cluster();

        }
    }

    public void Detach()
    {
        if (map is not null)
        {
            map.SetOnCameraIdleListener(null);
            map.SetOnMarkerClickListener(null);
        }

        if (clusterManager is not null)
        {
            clusterManager.SetOnClusterClickListener(null);
            clusterManager.SetOnClusterItemClickListener(null);
            clusterManager.ClearItems();
        }

        renderer?.OnRemove();

        foreach (ChurchClusterItem item in itemsById.Values)
        {
            item.Dispose();
        }

        itemsById.Clear();
        incomingIds.Clear();
        additions.Clear();
        removals.Clear();

        interactionListener?.Disconnect();
        interactionListener?.Dispose();
        renderer?.Dispose();
        algorithm?.Dispose();
        clusterManager?.Dispose();

        interactionListener = null;
        renderer = null;
        algorithm = null;
        clusterManager = null;
        map = null;
    }

    public void Dispose() => Detach();

    private sealed class ClusterInteractionListener(
        ClusteredMap virtualMap,
        GoogleMap googleMap) : Java.Lang.Object,
        ClusterManager.IOnClusterClickListener,
        ClusterManager.IOnClusterItemClickListener
    {
        private ClusteredMap? mapControl = virtualMap;
        private GoogleMap? map = googleMap;

        public bool OnClusterClick(ICluster? cluster)
        {
            if (map is null || cluster?.Position is not LatLng position)
            {
                return false;
            }

            float zoom = Math.Min(
                map.CameraPosition.Zoom + ClusterZoomStep,
                MaximumClusterZoom);
            map.AnimateCamera(CameraUpdateFactory.NewLatLngZoom(position, zoom));
            return true;
        }

        public bool OnClusterItemClick(Java.Lang.Object? item)
        {
            if (item is not ChurchClusterItem church || mapControl is null)
            {
                return false;
            }

            mapControl.SendMarkerClicked(church.Marker);
            return true;
        }

        public void Disconnect()
        {
            mapControl = null;
            map = null;
        }
    }
}
