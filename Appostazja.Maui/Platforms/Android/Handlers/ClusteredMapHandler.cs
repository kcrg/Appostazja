using Appostazja.Maui.Controls;
using Appostazja.Maui.Platforms.Android.Maps;
using Android.Gms.Maps;
using Microsoft.Maui.Maps.Handlers;
using NativeMapView = Android.Gms.Maps.MapView;
using MauiMap = Microsoft.Maui.Maps.IMap;

namespace Appostazja.Maui.Platforms.Android.Handlers;

public sealed class ClusteredMapHandler : MapHandler
{
    public static new readonly IPropertyMapper<MauiMap, IMapHandler> Mapper =
        new PropertyMapper<MauiMap, IMapHandler>(MapHandler.Mapper)
        {
            [nameof(ClusteredMap.Markers)] = MapMarkers,
        };

    private readonly AndroidMapClusterController clusterController = new();
    private MapReadyCallback? mapReadyCallback;

    public ClusteredMapHandler()
        : base(Mapper, MapHandler.CommandMapper)
    {
    }

    protected override void ConnectHandler(NativeMapView platformView)
    {
        base.ConnectHandler(platformView);

        mapReadyCallback = new MapReadyCallback(this);
        platformView.GetMapAsync(mapReadyCallback);
    }

    protected override void DisconnectHandler(NativeMapView platformView)
    {
        mapReadyCallback?.Disconnect();
        mapReadyCallback?.Dispose();
        mapReadyCallback = null;
        clusterController.Detach();

        base.DisconnectHandler(platformView);
    }

    private static void MapMarkers(IMapHandler handler, MauiMap map)
    {
        if (handler is ClusteredMapHandler clusteredHandler &&
            map is ClusteredMap clusteredMap)
        {
            clusteredHandler.clusterController.SyncMarkers(clusteredMap.Markers);
        }
    }

    private void OnMapReady(MapReadyCallback callback, GoogleMap googleMap)
    {
        PlatformView.Post(() =>
        {
            if (!ReferenceEquals(mapReadyCallback, callback) ||
                VirtualView is not ClusteredMap virtualMap)
            {
                return;
            }

            clusterController.Attach(
                Context,
                googleMap,
                virtualMap);
        });
    }

    private sealed class MapReadyCallback(ClusteredMapHandler handler) :
        Java.Lang.Object,
        IOnMapReadyCallback
    {
        private ClusteredMapHandler? handlerReference = handler;

        public void OnMapReady(GoogleMap googleMap) =>
            handlerReference?.OnMapReady(this, googleMap);

        public void Disconnect() => handlerReference = null;
    }
}
