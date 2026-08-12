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
}