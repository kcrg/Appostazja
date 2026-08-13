using System.Collections.ObjectModel;
using Appostazja.Core.Services;
using Appostazja.Maui.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Maps;

namespace Appostazja.Maui.ViewModels;

public sealed partial class MapViewModel(
    IMapDataService dataService,
    ILogger<MapViewModel> logger) : BaseViewModel
{
    private const int MaximumRenderedPins = 48;

    private ChurchMapPin[] allChurchPins = [];
    private ulong currentContentVersion;
    private MapSpan visibleRegion = MapSpan.FromCenterAndRadius(
        new Location(52.1, 19.4),
        Distance.FromKilometers(430));

    public int TotalChurchCount => allChurchPins.Length;

    [ObservableProperty]
    public partial ObservableCollection<ChurchMapPin> ChurchPins { get; set; } = [];

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasSelectedChurch))]
    [NotifyCanExecuteChangedFor(nameof(OpenReviewsCommand))]
    [NotifyCanExecuteChangedFor(nameof(NavigateCommand))]
    public partial ChurchMapPin? SelectedChurch { get; set; }

    [ObservableProperty]
    public partial string? ErrorMessage { get; set; }

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasExternalActionError))]
    public partial string? ExternalActionError { get; set; }

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(RefreshDataCommand))]
    public partial bool IsRefreshing { get; set; }

    [ObservableProperty]
    public partial string DataUpdatedText { get; set; } = "Data danych: —";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasDataStatusMessage))]
    public partial string? DataStatusMessage { get; set; }

    public bool HasSelectedChurch => SelectedChurch is not null;

    public bool HasExternalActionError => !string.IsNullOrWhiteSpace(ExternalActionError);

    public bool HasDataStatusMessage => !string.IsNullOrWhiteSpace(DataStatusMessage);

    public override async Task OnNavigatedToAsync()
    {
        await LoadAsync(false);
    }

    public void UpdateVisibleRegion(MapSpan region)
    {
        visibleRegion = region;
        ApplyPins(SelectPinsForRegion(allChurchPins, region));
    }

    [RelayCommand]
    private void SelectChurch(ChurchMapPin church)
    {
        SelectedChurch = church;
        ExternalActionError = null;
    }

    [RelayCommand]
    private void ClearSelection()
    {
        SelectedChurch = null;
        ExternalActionError = null;
    }

    [RelayCommand]
    private Task RetryAsync(CancellationToken cancellationToken) =>
        LoadAsync(true, cancellationToken);

    private bool CanRefreshData() => !IsRefreshing;

    [RelayCommand(CanExecute = nameof(CanRefreshData))]
    private Task RefreshDataAsync(CancellationToken cancellationToken) =>
        LoadAsync(true, cancellationToken);

    [RelayCommand(CanExecute = nameof(HasSelectedChurch))]
    private async Task OpenReviewsAsync()
    {
        ChurchMapPin? church = SelectedChurch;
        if (church is null)
        {
            return;
        }

        await RunExternalActionAsync(
            () => Launcher.Default.OpenAsync(
                new Uri($"https://mapaapostazji.pl/parafia/{Uri.EscapeDataString(church.Id)}")),
            "Nie udało się otworzyć opinii o parafii.");
    }

    [RelayCommand]
    private Task AddFeedbackAsync() =>
        RunExternalActionAsync(
            () => Launcher.Default.OpenAsync(AppLinks.FeedbackForm),
            "Nie udało się otworzyć formularza opinii.");

    [RelayCommand(CanExecute = nameof(HasSelectedChurch))]
    private async Task NavigateAsync()
    {
        ChurchMapPin? church = SelectedChurch;
        if (church is null)
        {
            return;
        }

        await RunExternalActionAsync(
            async () =>
            {
                await Microsoft.Maui.ApplicationModel.Map.Default.OpenAsync(
                    church.Location,
                    new MapLaunchOptions
                    {
                        Name = church.Label,
                        NavigationMode = NavigationMode.Driving,
                    });

                return true;
            },
            "Nie udało się uruchomić nawigacji.");
    }

    private async Task LoadAsync(
        bool forceRefresh,
        CancellationToken cancellationToken = default)
    {
        bool hasExistingData = ChurchPins.Count > 0;
        CurrentState = hasExistingData ? null : States.Loading;
        IsRefreshing = hasExistingData;
        ErrorMessage = null;
        DataStatusMessage = null;

        try
        {
            var snapshot = await dataService
                .GetChurchesAsync(forceRefresh, cancellationToken);

            if (!hasExistingData || currentContentVersion != snapshot.ContentVersion)
            {
                ChurchMapPin[] pins = await Task.Run(
                    () => snapshot.Features.Select(CreatePin).ToArray(),
                    cancellationToken);

                allChurchPins = pins;
                OnPropertyChanged(nameof(TotalChurchCount));
                ApplyPins(SelectPinsForRegion(pins, visibleRegion));
                currentContentVersion = snapshot.ContentVersion;
                SelectedChurch = null;
            }

            DateTimeOffset localTimestamp = snapshot.RetrievedAt.ToLocalTime();
            DataUpdatedText = $"Dane z: {localTimestamp:dd.MM.yyyy, HH:mm}";
            DataStatusMessage = snapshot.IsStale
                ? "Brak połączenia — wyświetlam ostatnią zapisaną kopię."
                : null;
            CurrentState = null;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            CurrentState = null;
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Downloading map data failed.");
            if (hasExistingData)
            {
                DataStatusMessage = "Nie udało się zaktualizować danych. Wyświetlam ostatnią kopię.";
            }
            else
            {
                ErrorMessage = "Nie udało się pobrać danych mapy. Sprawdź połączenie i spróbuj ponownie.";
                CurrentState = States.Error;
            }
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private void ApplyPins(IReadOnlyList<ChurchMapPin> incomingPins)
    {
        if (ChurchPins.Count == 0)
        {
            ChurchPins = new ObservableCollection<ChurchMapPin>(incomingPins);
            return;
        }

        var remainingPins = new Dictionary<string, ChurchMapPin>(
            incomingPins.Count,
            StringComparer.Ordinal);

        foreach (ChurchMapPin pin in incomingPins)
        {
            remainingPins[pin.Id] = pin;
        }

        for (int index = ChurchPins.Count - 1; index >= 0; index--)
        {
            ChurchMapPin currentPin = ChurchPins[index];
            if (!remainingPins.Remove(currentPin.Id, out ChurchMapPin? replacement))
            {
                ChurchPins.RemoveAt(index);
            }
            else if (!PinsAreEquivalent(currentPin, replacement))
            {
                ChurchPins[index] = replacement;
            }
        }

        foreach (ChurchMapPin pin in incomingPins)
        {
            if (remainingPins.Remove(pin.Id))
            {
                ChurchPins.Add(pin);
            }
        }
    }

    private static ChurchMapPin CreatePin(Appostazja.Core.Models.MapFeature feature)
    {
        double longitude = feature.Geometry.Coordinates[0];
        double latitude = feature.Geometry.Coordinates[1];

        return new ChurchMapPin(
            feature.Properties.Id,
            feature.Properties.Name,
            feature.Properties.Address,
            feature.Properties.AverageRating,
            new Location(latitude, longitude));
    }

    private static bool PinsAreEquivalent(ChurchMapPin left, ChurchMapPin right) =>
        string.Equals(left.Label, right.Label, StringComparison.Ordinal) &&
        string.Equals(left.Address, right.Address, StringComparison.Ordinal) &&
        left.AverageRating.Equals(right.AverageRating) &&
        left.Location.Latitude.Equals(right.Location.Latitude) &&
        left.Location.Longitude.Equals(right.Location.Longitude);

    private static IReadOnlyList<ChurchMapPin> SelectPinsForRegion(
        IReadOnlyList<ChurchMapPin> pins,
        MapSpan region)
    {
        double latitudeSpan = Math.Max(region.LatitudeDegrees * 1.2, 0.01);
        double longitudeSpan = Math.Max(region.LongitudeDegrees * 1.2, 0.01);
        double south = region.Center.Latitude - (latitudeSpan / 2);
        double north = region.Center.Latitude + (latitudeSpan / 2);
        double west = region.Center.Longitude - (longitudeSpan / 2);
        double east = region.Center.Longitude + (longitudeSpan / 2);

        ChurchMapPin[] candidates = pins
            .Where(pin =>
                pin.Location.Latitude >= south &&
                pin.Location.Latitude <= north &&
                pin.Location.Longitude >= west &&
                pin.Location.Longitude <= east)
            .ToArray();

        if (candidates.Length <= MaximumRenderedPins)
        {
            return candidates;
        }

        double aspectRatio = longitudeSpan / latitudeSpan;
        int columns = Math.Clamp(
            (int)Math.Round(Math.Sqrt(MaximumRenderedPins * aspectRatio)),
            1,
            MaximumRenderedPins);
        int rows = Math.Max(1, MaximumRenderedPins / columns);
        var buckets = new Dictionary<int, ChurchMapPin>(MaximumRenderedPins);

        foreach (ChurchMapPin pin in candidates)
        {
            int column = Math.Clamp(
                (int)(((pin.Location.Longitude - west) / longitudeSpan) * columns),
                0,
                columns - 1);
            int row = Math.Clamp(
                (int)(((pin.Location.Latitude - south) / latitudeSpan) * rows),
                0,
                rows - 1);

            buckets.TryAdd((row * columns) + column, pin);
        }

        return buckets.Values.ToArray();
    }

    private async Task RunExternalActionAsync(
        Func<Task<bool>> action,
        string userError)
    {
        ExternalActionError = null;

        try
        {
            if (!await action())
            {
                ExternalActionError = userError;
            }
        }
        catch (Exception exception)
        {
            logger.LogWarning(exception, "Opening an external application failed.");
            ExternalActionError = userError;
        }
    }
}