using Appostazja.Maui.Models;
using Microsoft.Maui.Controls.Maps;

namespace Appostazja.Maui.Views;

public partial class MapView : BasePage
{
    public MapView(MapViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();

        ApostasyMap.MoveToRegion(
            Microsoft.Maui.Maps.MapSpan.FromCenterAndRadius(
                new Location(52.1, 19.4),
                Microsoft.Maui.Maps.Distance.FromKilometers(430)));
    }


    private void OnMarkerClicked(object? sender, PinClickedEventArgs args)
    {
        args.HideInfoWindow = true;

        if (sender is Pin { BindingContext: ChurchMapPin church } &&
            BindingContext is MapViewModel viewModel)
        {
            viewModel.SelectChurchCommand.Execute(church);
        }
    }

    private void OnMapClicked(object? sender, MapClickedEventArgs args)
    {
        if (BindingContext is MapViewModel viewModel)
        {
            viewModel.ClearSelectionCommand.Execute(null);
        }
    }
}