using System.Runtime.InteropServices;

namespace Appostazja.Maui.ViewModels;

public sealed partial class AboutViewModel : ObservableObject
{
    public AboutViewModel(IAppInfo appInfo)
    {
        MauiVersion = $"{RuntimeInformation.FrameworkDescription} / MAUI 11 Preview 7";
        AppVersion = $"Appostazja {appInfo.VersionString} ({appInfo.BuildString})";
#if DEBUG
        BuildConfiguration = "Konfiguracja: Debug";
#else
        BuildConfiguration = "Konfiguracja: Release";
#endif
    }

    [ObservableProperty]
    public partial string? FeedbackError { get; set; }

    public string MauiVersion { get; }

    public string AppVersion { get; }


    public string BuildConfiguration { get; }
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
        catch
        {
            FeedbackError = "Nie udało się otworzyć formularza opinii.";
        }
    }
}