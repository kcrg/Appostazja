using Appostazja.Maui.Models;
using Android.Gms.Maps.Model;
using Android.Gms.Maps.Utils.Clustering;

namespace Appostazja.Maui.Platforms.Android.Maps;

internal sealed class ChurchClusterItem : Java.Lang.Object, IClusterItem
{
    private readonly LatLng position;

    public ChurchClusterItem(ChurchMapPin marker)
    {
        Marker = marker;
        position = CreatePosition(marker);
    }

    public ChurchMapPin Marker { get; private set; }

    public LatLng Position => position;

    public string? Title => Marker.Label;

    public string? Snippet => Marker.Address;

    public Java.Lang.Float? ZIndex => null;

    public bool HasSamePosition(ChurchMapPin marker) =>
        Marker.Location.Latitude.Equals(marker.Location.Latitude) &&
        Marker.Location.Longitude.Equals(marker.Location.Longitude);

    public bool UpdateFrom(ChurchMapPin marker)
    {
        bool changed =
            !string.Equals(Marker.Label, marker.Label, StringComparison.Ordinal) ||
            !string.Equals(Marker.Address, marker.Address, StringComparison.Ordinal) ||
            !Marker.AverageRating.Equals(marker.AverageRating);

        if (!changed)
        {
            return false;
        }

        Marker = marker;

        return true;
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            position.Dispose();
        }

        base.Dispose(disposing);
    }

    private static LatLng CreatePosition(ChurchMapPin marker) =>
        new(marker.Location.Latitude, marker.Location.Longitude);
}
