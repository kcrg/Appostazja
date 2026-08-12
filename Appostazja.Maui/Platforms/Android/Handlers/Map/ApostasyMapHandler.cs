using Android.Gms.Maps;
using Android.Gms.Maps.Model;
using Android.Gms.Maps.Utils.Clustering;
using Android.Graphics.Drawables;
using Android.Widget;
using Appostazja.Maui.Controls.Map;
using Microsoft.Maui.Maps;
using Microsoft.Maui.Maps.Handlers;
using Microsoft.Maui.Platform;
using IMap = Microsoft.Maui.Maps.IMap;
using MapView = Android.Gms.Maps.MapView;

namespace Appostazja.Maui.Platforms.Android.Handlers.Map;

// https://vladislavantonyuk.github.io/articles/Customize-map-pins-in-.NET-MAUI/
public class ApostasyMapHandler : MapHandler
{
    public static readonly IPropertyMapper<IMap, IMapHandler> CustomMapper =
        new PropertyMapper<IMap, IMapHandler>(Mapper)
        {
            [nameof(IMap.Pins)] = MapPins,
        };

    public ApostasyMapHandler() : base(CustomMapper, CommandMapper)
    {
    }

    public ApostasyMapHandler(IPropertyMapper? mapper = null, CommandMapper? commandMapper = null) : base(
        mapper ?? CustomMapper, commandMapper ?? CommandMapper)
    {
    }

    public List<Marker> Markers { get; } = new();

    protected override MapView CreatePlatformView()
    {
        return base.CreatePlatformView();
    }

    protected override void ConnectHandler(MapView platformView)
    {
        base.ConnectHandler(platformView);
        var mapReady = new MapCallbackHandler(this);
        PlatformView.GetMapAsync(mapReady);
    }

    private static new void MapPins(IMapHandler handler, IMap map)
    {
        if (handler is ApostasyMapHandler mapHandler)
        {
            foreach (var marker in mapHandler.Markers)
            {
                marker.Remove();
            }

            mapHandler.AddPins(map.Pins);
        }
    }

    private void AddPins(IEnumerable<IMapPin> mapPins)
    {
        if (Map is null || MauiContext is null)
        {
            return;
        }

        foreach (var pin in mapPins)
        {
            var pinHandler = pin.ToHandler(MauiContext);
            if (pinHandler is IMapPinHandler mapPinHandler)
            {
                var markerOption = mapPinHandler.PlatformView;
                if (pin is ChurchPin cp)
                {
                    cp.ImageSource.LoadImage(MauiContext, result =>
                    {
                        if (result?.Value is BitmapDrawable bitmapDrawable)
                        {
                            markerOption.SetIcon(BitmapDescriptorFactory.FromBitmap(bitmapDrawable.Bitmap));
                        }

                        AddMarker(Map, pin, Markers, markerOption);
                    });
                }
                else
                {
                    AddMarker(Map, pin, Markers, markerOption);
                }
            }
        }
    }

    private static void AddMarker(GoogleMap map, IMapPin pin, ICollection<Marker> markers, MarkerOptions markerOption)
    {
        var marker = map.AddMarker(markerOption);
        pin.MarkerId = marker.Id;
        markers.Add(marker);
    }
}

class MapCallbackHandler : Java.Lang.Object, IOnMapReadyCallback, ClusterManager.IOnClusterClickListener, ClusterManager.IOnClusterItemClickListener
{
    private readonly IMapHandler mapHandler;

    private ClusterManager? mClusterManager;
    private GoogleMap? nativeMap;

    public MapCallbackHandler(IMapHandler mapHandler)
    {
        this.mapHandler = mapHandler;
        this.nativeMap = this.mapHandler.Map;
    }

    public bool OnClusterClick(ICluster? cluster)
    {
        if (nativeMap is null || cluster is null)
        {
            return false;
        }

        if (cluster.Items is null)
        {
            return false;
        }

        // Show a toast with some info when the cluster is clicked.
        while (cluster.Items.GetEnumerator().MoveNext())
        {
            var person = cluster.Items.GetEnumerator().Current as ChurchPin;

            if (person is null)
            {
                return false;
            }

            //Toast.MakeText(Platform.AppContext, $"{cluster} (including {person.Address} )", ToastLength.Short).Show();
        }

        // Zoom in the cluster. Need to create LatLngBounds and including all the cluster items
        // inside of bounds, then animate to center of the bounds.

        // Create the builder to collect all essential cluster items for the bounds.
        LatLngBounds.Builder builder = new();
        foreach (IClusterItem item in cluster.Items)
        {
            builder.Include(item.Position);
        }
        // Get the LatLngBounds
        LatLngBounds bounds = builder.Build();

        // Animate camera to the bounds
        try
        {
            nativeMap.AnimateCamera(CameraUpdateFactory.NewLatLngBounds(bounds, 100));
        }
        catch (Exception e)
        {
            Console.WriteLine(e.StackTrace);
        }

        return true;
    }

    public bool OnClusterItemClick(Java.Lang.Object? item)
    {
        if(item is null)
        {
            return false;
        }

        return true;
    }

    protected void ConfigureClustering(GoogleMap googleMap)
    {
        if (nativeMap is null)
        {
            return;
        }

        mClusterManager = new ClusterManager(Platform.AppContext, googleMap);
        mClusterManager.Renderer = new ChurchCluster(Platform.AppContext, googleMap, mClusterManager);
        googleMap.SetOnCameraIdleListener(mClusterManager);
        googleMap.SetOnMarkerClickListener(mClusterManager);
        googleMap.SetOnInfoWindowClickListener(mClusterManager);
        mClusterManager.SetOnClusterClickListener(this);
        mClusterManager.SetOnClusterItemClickListener(this);

        mClusterManager.Cluster();
    }

    public void OnMapReady(GoogleMap googleMap)
    {
        mapHandler.UpdateValue(nameof(IMap.Pins));
        nativeMap = mapHandler.Map;
        ConfigureClustering(mapHandler.Map!);
    }
}