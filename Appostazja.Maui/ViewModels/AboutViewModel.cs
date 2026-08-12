using System.Runtime.InteropServices;
using Microsoft.Maui.ApplicationModel;

namespace Appostazja.Maui.ViewModels;

public sealed partial class AboutViewModel : ObservableObject
{
    private const string FeedbackFormUrl =
        "https://docs.google.com/forms/d/e/1FAIpQLSehJc7aLTbzapw-8H79rq8gZxNQWLQb5cqJR_lb7qcfQmb5fg/viewform";

    public AboutViewModel(IAppInfo appInfo)
    {
        MauiVersion = $"{RuntimeInformation.FrameworkDescription} / MAUI 11 Preview 7";
        AppVersion = $"Appostazja {appInfo.VersionString} ({appInfo.BuildString})";
    }

    [ObservableProperty]
    public partial string? FeedbackError { get; set; }

    public string MauiVersion { get; }

    public string AppVersion { get; }

    [RelayCommand]
    private async Task AddFeedbackAsync()
    {
        FeedbackError = null;

        try
        {
            if (!await Launcher.Default.OpenAsync(new Uri(FeedbackFormUrl)))
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