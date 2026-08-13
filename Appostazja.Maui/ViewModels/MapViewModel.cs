using System.Collections.ObjectModel;
using Appostazja.Core.Services;
using Appostazja.Maui.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.ApplicationModel;

namespace Appostazja.Maui.ViewModels;

public sealed partial class MapViewModel(
    IMapDataService dataService,
    ILogger<MapViewModel> logger) : BaseViewModel
{
    private ulong currentContentVersion;

    public int TotalChurchCount => ChurchPins.Count;

    [ObservableProperty]
    public partial IReadOnlyList<ChurchMapPin> ChurchPins { get; set; } = [];

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
            () => Browser.Default.OpenAsync(
                new Uri($"https://mapaapostazji.pl/parafia/{Uri.EscapeDataString(church.Id)}"), BrowserLaunchMode.SystemPreferred),
            "Nie udało się otworzyć opinii o parafii.");
    }

    [RelayCommand]
    private Task AddFeedbackAsync() =>
        RunExternalActionAsync(
            () => Browser.Default.OpenAsync(AppLinks.FeedbackForm, BrowserLaunchMode.SystemPreferred),
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

                ChurchPins = pins;
                OnPropertyChanged(nameof(TotalChurchCount));
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