using Appostazja.Maui.Models;
using Microsoft.Maui.Controls.Maps;

namespace Appostazja.Maui.Controls;

public sealed class ClusteredMap : Microsoft.Maui.Controls.Maps.Map
{
    public static readonly BindableProperty MarkersProperty = BindableProperty.Create(
        nameof(Markers),
        typeof(IReadOnlyList<ChurchMapPin>),
        typeof(ClusteredMap),
        Array.Empty<ChurchMapPin>(),
        propertyChanged: OnMarkersChanged);

#if !ANDROID
    private readonly Dictionary<string, Pin> fallbackPins = new(StringComparer.Ordinal);
#endif

    public IReadOnlyList<ChurchMapPin> Markers
    {
        get => (IReadOnlyList<ChurchMapPin>)GetValue(MarkersProperty);
        set => SetValue(MarkersProperty, value ?? Array.Empty<ChurchMapPin>());
    }

    public event EventHandler<ClusteredMapMarkerClickedEventArgs>? MarkerClicked;

    internal void SendMarkerClicked(ChurchMapPin marker) =>
        MarkerClicked?.Invoke(this, new ClusteredMapMarkerClickedEventArgs(marker));

    private static void OnMarkersChanged(
        BindableObject bindable,
        object oldValue,
        object newValue)
    {
#if !ANDROID
        var map = (ClusteredMap)bindable;
        map.SyncFallbackMarkers(
            newValue as IReadOnlyList<ChurchMapPin> ?? Array.Empty<ChurchMapPin>());
#endif
    }

#if !ANDROID
    private void SyncFallbackMarkers(IReadOnlyList<ChurchMapPin> markers)
    {
        var incomingIds = new HashSet<string>(markers.Count, StringComparer.Ordinal);

        foreach (ChurchMapPin marker in markers)
        {
            if (!incomingIds.Add(marker.Id))
            {
                continue;
            }

            if (fallbackPins.TryGetValue(marker.Id, out Pin? pin))
            {
                UpdateFallbackPin(pin, marker);
                continue;
            }

            pin = new Pin();
            pin.MarkerClicked += OnFallbackMarkerClicked;
            UpdateFallbackPin(pin, marker);
            fallbackPins.Add(marker.Id, pin);
            Pins.Add(pin);
        }

        foreach ((string id, Pin pin) in fallbackPins.ToArray())
        {
            if (incomingIds.Contains(id))
            {
                continue;
            }

            pin.MarkerClicked -= OnFallbackMarkerClicked;
            Pins.Remove(pin);
            fallbackPins.Remove(id);
        }
    }

    private static void UpdateFallbackPin(Pin pin, ChurchMapPin marker)
    {
        pin.Address = marker.Address;
        pin.BindingContext = marker;
        pin.ImageSource = marker.PinImageSource;
        pin.Label = marker.Label;
        pin.Location = marker.Location;
    }

    private void OnFallbackMarkerClicked(object? sender, PinClickedEventArgs args)
    {
        args.HideInfoWindow = true;

        if (sender is Pin { BindingContext: ChurchMapPin marker })
        {
            SendMarkerClicked(marker);
        }
    }
#endif
}

public sealed class ClusteredMapMarkerClickedEventArgs(ChurchMapPin marker) : EventArgs
{
    public ChurchMapPin Marker { get; } = marker;
}
