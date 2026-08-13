using Appostazja.Core.Pdf;
using Appostazja.Core.Services;

namespace Appostazja.Maui;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .UseMauiMaps()
            .UseSharedRatingPinIcons()
            .ConfigureFonts(fonts =>
            {
                fonts.AddFont("tabler-icons.ttf", "FontIcons");
                fonts.AddFont("Nunito-Bold.ttf", "NunitoBold");
                fonts.AddFont("Nunito-Light.ttf", "NunitoLight");
                fonts.AddFont("Nunito-Medium.ttf", "NunitoMedium");
                fonts.AddFont("Nunito-Regular.ttf", "NunitoRegular");
                fonts.AddFont("Nunito-SemiBold.ttf", "NunitoSemiBold");
            })
            .UseMauiCommunityToolkit()
            .RegisterViewsAndViewModels()
            .Services
            .RegisterServices()
            .RegisterEssentials();

        return builder.Build();
    }

    private static MauiAppBuilder UseSharedRatingPinIcons(this MauiAppBuilder builder)
    {
#if ANDROID
        Microsoft.Maui.Maps.Handlers.MapPinHandler.Mapper.ModifyMapping(
            nameof(Microsoft.Maui.Maps.IMapPin.ImageSource),
            static (handler, pin, _) =>
            {
                string? fileName =
                    (pin.ImageSource as Microsoft.Maui.IFileImageSource)?.File;

                float hue = fileName switch
                {
                    "pin_bad.png" => Android.Gms.Maps.Model.BitmapDescriptorFactory.HueRed,
                    "pin_average.png" => Android.Gms.Maps.Model.BitmapDescriptorFactory.HueYellow,
                    _ => Android.Gms.Maps.Model.BitmapDescriptorFactory.HueGreen,
                };

                handler.PlatformView.SetIcon(
                    Android.Gms.Maps.Model.BitmapDescriptorFactory.DefaultMarker(hue));
            });
#endif

        return builder;
    }

    private static IServiceCollection RegisterServices(this IServiceCollection services)
    {
        services.AddSingleton(new HttpClient { Timeout = TimeSpan.FromSeconds(20) });
        services.AddSingleton(TimeProvider.System);
        services.AddSingleton<IFileSystem>(FileSystem.Current);
        services.AddSingleton<ISecureStorage>(SecureStorage.Default);
        services.AddSingleton<IMapDataCache, FileMapDataCache>();
        services.AddSingleton<IMapDataService, MapDataService>();
        services.AddSingleton<IPdfDeclarationGenerator, PdfDeclarationGenerator>();
        services.AddSingleton<IPdfExportService, PdfExportService>();
        services.AddSingleton<INavigationService, ShellNavigationService>();

        return services;
    }

    private static IServiceCollection RegisterEssentials(this IServiceCollection services)
    {
        services.AddSingleton<IAppInfo>(AppInfo.Current);

        return services;
    }

    private static MauiAppBuilder RegisterViewsAndViewModels(this MauiAppBuilder builder)
    {
        builder.Services.AddSingleton<AppShell>();
        builder.Services.AddTransient<MainViewModel>();
        builder.Services.AddTransient<MainView>();

        builder.Services.AddTransient<AboutView>();
        builder.Services.AddTransient<AboutViewModel>();
        builder.Services.AddTransient<FormViewModel>();
        builder.Services.AddTransient<FormView>();
        builder.Services.AddSingleton<MapViewModel>();
        builder.Services.AddSingleton<MapView>();

        return builder;
    }
}