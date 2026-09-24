using Microsoft.Extensions.Logging;

namespace Appostazja.Maui.ViewModels;

public sealed partial class MainViewModel(
    INavigationService navigationService,
    ILogger<MainViewModel> logger) : ObservableObject
{
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasFeedbackError))]
    public partial string? FeedbackError { get; set; }

    public bool HasFeedbackError => !string.IsNullOrWhiteSpace(FeedbackError);

    [RelayCommand]
    private Task NavigateToAboutAsync() =>
        navigationService.NavigateToAsync(AppShell.AboutRoute);

    [RelayCommand]
    private Task NavigateToFormAsync() =>
        navigationService.NavigateToAsync(AppShell.FormRoute);

    [RelayCommand]
    private Task NavigateToMapAsync() =>
        navigationService.NavigateToAsync(AppShell.MapRoute);

    [RelayCommand]
    private async Task AddFeedbackAsync()
    {
        FeedbackError = null;

        try
        {
            if (!await Browser.Default.OpenAsync(AppLinks.FeedbackForm, BrowserLaunchMode.SystemPreferred))
            {
                FeedbackError = "Nie udało się otworzyć formularza opinii.";
            }
        }
        catch (Exception exception)
        {
            logger.LogWarning(exception, "Opening the feedback form failed.");
            FeedbackError = "Nie udało się otworzyć formularza opinii.";
        }
    }
}