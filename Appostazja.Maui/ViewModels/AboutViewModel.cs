using System.Runtime.InteropServices;

namespace Appostazja.Maui.ViewModels;

public sealed class AboutViewModel : ObservableObject
{
    public AboutViewModel(IAppInfo appInfo)
    {
        MauiVersion = $"{RuntimeInformation.FrameworkDescription} / MAUI 11 Preview 7";
        AppVersion = $"Appostazja {appInfo.VersionString} ({appInfo.BuildString})";
    }

    public string MauiVersion { get; }

    public string AppVersion { get; }
}