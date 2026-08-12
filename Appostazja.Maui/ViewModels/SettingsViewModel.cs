using System.Reflection;

namespace Appostazja.Maui.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    private readonly IAppInfo appInfoService;

    [ObservableProperty]
    string? appVersion;

    [ObservableProperty]
    string? mauiVersion;

    public SettingsViewModel(IAppInfo appInfoService)
    {
        this.appInfoService = appInfoService;

        var attr = typeof(MauiApp).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion;
        int? index = attr?.LastIndexOf('+');
        attr = attr?.Substring(0, index ?? 0);

        MauiVersion = $".NET MAUI version: {attr}";
        AppVersion = $"Made using .NET MAUI with ♥ v{this.appInfoService.VersionString}.{this.appInfoService.BuildString}";
    }
}