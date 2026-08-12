using Appostazja.Maui.Controls.Map;
using Appostazja.Maui.Models;
using Appostazja.Maui.Models.Map;
using System.Collections.ObjectModel;

namespace Appostazja.Maui.ViewModels;

public partial class MapViewModel : BaseViewModel
{
    private readonly IDataService dataService;

    [ObservableProperty]
    private RangeObservableCollection<PinData>? churchPins;

    [ObservableProperty]
    private RangeObservableCollection<ChurchPin>? churchPinList;

    public MapViewModel(IDataService dataService)
    {
        this.dataService = dataService;

        ChurchPinList = new RangeObservableCollection<ChurchPin>();
    }

    public override async Task OnNavigatedToAsync()
    {
        await Task.Delay(100);

        await AddPinsAsync();

        await base.OnNavigatedToAsync();
    }

    private async Task AddPinsAsync()
    {
        var response = await dataService.GetGeoJsonAsync();

        if (response is null)
        {
            return;
        }

        Image asdasds;

        IEnumerable<ChurchPin> pinList;

        foreach (FeatureModel item in response.Features)
        {
            if (item is null)
            {
                continue;
            }

            ChurchPinList?.Add(new ChurchPin()
            {
                ImageSource = ImageSource.FromFile("drawing.png"),
                Location = new Location(item?.Geometry?.Coordinates?.First() ?? 0, item?.Geometry?.Coordinates?.Last() ?? 0),
                Data = item?.Properties ?? new PropertiesModel(),
                Label = item?.Properties?.ChurchName ?? string.Empty,
            });
        }
    }
}