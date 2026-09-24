using Android.Gms.Maps;
using Android.Gms.Maps.Model;
using Android.Gms.Maps.Utils.Clustering;
using Android.Gms.Maps.Utils.Clustering.View;

namespace Appostazja.Maui.Platforms.Android.Maps;

internal sealed class ChurchClusterRenderer(
    global::Android.Content.Context context,
    GoogleMap map,
    ClusterManager clusterManager) :
    DefaultClusterRenderer(context, map, clusterManager)
{
    private readonly ChurchMarkerIconFactory iconFactory = new(context);

    protected override void OnBeforeClusterItemRendered(
        Java.Lang.Object item,
        MarkerOptions markerOptions)
    {
        if (item is not ChurchClusterItem church)
        {
            base.OnBeforeClusterItemRendered(item, markerOptions);
            return;
        }

        ApplyChurchMarker(church, markerOptions);
    }

    protected override void OnBeforeClusterRendered(
        ICluster cluster,
        MarkerOptions markerOptions)
    {
        markerOptions.SetIcon(iconFactory.GetClusterIcon(cluster));
        markerOptions.Anchor(0.5f, 0.5f);
    }

    protected override void OnClusterItemUpdated(
        Java.Lang.Object item,
        Marker marker)
    {
        if (item is not ChurchClusterItem church)
        {
            base.OnClusterItemUpdated(item, marker);
            return;
        }

        marker.Position = church.Position;
        marker.SetIcon(iconFactory.GetPinIcon(church.Marker.RatingCategory));
        marker.SetAnchor(0.5f, 1);
    }

    protected override void OnClusterUpdated(
        ICluster cluster,
        Marker marker)
    {
        if (cluster.Position is LatLng position)
        {
            marker.Position = position;
        }

        marker.SetIcon(iconFactory.GetClusterIcon(cluster));
        marker.SetAnchor(0.5f, 0.5f);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            iconFactory.Dispose();
        }

        base.Dispose(disposing);
    }

    private void ApplyChurchMarker(
        ChurchClusterItem church,
        MarkerOptions markerOptions)
    {
        markerOptions.SetIcon(iconFactory.GetPinIcon(church.Marker.RatingCategory));
        markerOptions.Anchor(0.5f, 1);
    }
}
