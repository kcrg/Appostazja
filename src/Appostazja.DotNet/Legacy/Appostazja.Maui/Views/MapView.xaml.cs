using Appostazja.Maui.Controls;
using Appostazja.Maui.Models;
using Microsoft.Maui.Controls.Maps;

namespace Appostazja.Maui.Views;

public partial class MapView : BasePage
{
    private bool hasSetInitialRegion;

    public MapView(MapViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();

        if (hasSetInitialRegion)
        {
            return;
        }

        hasSetInitialRegion = true;
        ApostasyMap.MoveToRegion(
            Microsoft.Maui.Maps.MapSpan.FromCenterAndRadius(
                new Location(52.1, 19.4),
                Microsoft.Maui.Maps.Distance.FromKilometers(430)));
    }

    private async void OnMarkerClicked(object? sender, ClusteredMapMarkerClickedEventArgs args)
    {
        if (BindingContext is not MapViewModel viewModel)
        {
            return;
        }

        ChurchMapPin church = args.Marker;

        viewModel.SelectChurchCommand.Execute(church);
        ApostasyMap.MoveToRegion(
            Microsoft.Maui.Maps.MapSpan.FromCenterAndRadius(
                church.Location,
                Microsoft.Maui.Maps.Distance.FromKilometers(3)));

        await ShowChurchDetailsAsync();
    }

    private async void OnMapClicked(object? sender, MapClickedEventArgs args)
    {
        await CloseChurchDetailsAsync();
    }

    private async void OnCloseDetailsClicked(object? sender, EventArgs args)
    {
        await CloseChurchDetailsAsync();
    }

    private async Task ShowChurchDetailsAsync()
    {
        await Task.Yield();
        ChurchDetailsCard.Opacity = 0;
        ChurchDetailsCard.TranslationY = 48;
        ChurchDetailsCard.Scale = 0.98;

        await Task.WhenAll(
            ChurchDetailsCard.FadeToAsync(1, 180, Easing.CubicOut),
            ChurchDetailsCard.TranslateToAsync(0, 0, 220, Easing.CubicOut),
            ChurchDetailsCard.ScaleToAsync(1, 220, Easing.CubicOut));
    }

    private async Task CloseChurchDetailsAsync()
    {
        if (BindingContext is not MapViewModel viewModel ||
            !viewModel.HasSelectedChurch)
        {
            return;
        }

        await Task.WhenAll(
            ChurchDetailsCard.FadeToAsync(0, 140, Easing.CubicIn),
            ChurchDetailsCard.TranslateToAsync(0, 32, 160, Easing.CubicIn),
            ChurchDetailsCard.ScaleToAsync(0.98, 160, Easing.CubicIn));

        viewModel.ClearSelectionCommand.Execute(null);
    }
}