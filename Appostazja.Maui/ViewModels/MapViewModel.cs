using Appostazja.Core.Services;
using Appostazja.Maui.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.ApplicationModel;

namespace Appostazja.Maui.ViewModels;

public sealed partial class MapViewModel(
    IMapDataService dataService,
    ILogger<MapViewModel> logger) : BaseViewModel
{
    private const string FeedbackFormUrl =
        "https://docs.google.com/forms/d/e/1FAIpQLSehJc7aLTbzapw-8H79rq8gZxNQWLQb5cqJR_lb7qcfQmb5fg/viewform";

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

    public bool HasSelectedChurch => SelectedChurch is not null;

    public bool HasExternalActionError => !string.IsNullOrWhiteSpace(ExternalActionError);

    public override async Task OnNavigatedToAsync()
    {
        if (ChurchPins.Count == 0)
        {
            await LoadAsync(false);
        }
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
            () => Launcher.Default.OpenAsync(new Uri(FeedbackFormUrl)),
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
        CurrentState = States.Loading;
        ErrorMessage = null;
        SelectedChurch = null;

        try
        {
            var features = await dataService
                .GetChurchesAsync(forceRefresh, cancellationToken);

            ChurchPins = [.. features.Select(feature =>
            {
                double longitude = feature.Geometry.Coordinates[0];
                double latitude = feature.Geometry.Coordinates[1];

                return new ChurchMapPin(
                    feature.Properties.Id,
                    feature.Properties.Name,
                    feature.Properties.Address,
                    feature.Properties.AverageRating,
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