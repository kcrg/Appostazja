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
            .ConfigureMauiHandlers(handlers =>
            {
#if ANDROID
                handlers.AddHandler<Controls.ClusteredMap, Platforms.Android.Handlers.ClusteredMapHandler>();
#else
                handlers.AddHandler<Controls.ClusteredMap, Microsoft.Maui.Maps.Handlers.MapHandler>();
#endif
            })
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