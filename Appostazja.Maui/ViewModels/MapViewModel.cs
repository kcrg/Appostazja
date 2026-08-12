using Appostazja.Core.Services;
using Appostazja.Maui.Models;
using Microsoft.Extensions.Logging;

namespace Appostazja.Maui.ViewModels;

public sealed partial class MapViewModel(
    IMapDataService dataService,
    ILogger<MapViewModel> logger) : BaseViewModel
{
    [ObservableProperty]
    public partial IReadOnlyList<ChurchMapPin> ChurchPins { get; set; } = [];

    [ObservableProperty]
    public partial string? ErrorMessage { get; set; }

    public override async Task OnNavigatedToAsync()
    {
        if (ChurchPins.Count == 0)
        {
            await LoadAsync(false);
        }
    }

    [RelayCommand]
    private Task RetryAsync(CancellationToken cancellationToken) =>
        LoadAsync(true, cancellationToken);

    private async Task LoadAsync(
        bool forceRefresh,
        CancellationToken cancellationToken = default)
    {
        CurrentState = States.Loading;
        ErrorMessage = null;

        try
        {
            var features = await dataService
                .GetChurchesAsync(forceRefresh, cancellationToken);

            ChurchPins = [.. features
                .Select(feature =>
                {
                    double longitude = feature.Geometry.Coordinates[0];
                    double latitude = feature.Geometry.Coordinates[1];
                    return new ChurchMapPin(
                        feature.Properties.Name,
                        feature.Properties.Address,
                        new Location(latitude, longitude));
                })];

            CurrentState = null;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            CurrentState = null;
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Downloading map data failed.");
            ErrorMessage = "Nie udało się pobrać danych mapy. Sprawdź połączenie i spróbuj ponownie.";
            CurrentState = States.Error;
        }
    }
}
