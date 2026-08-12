#if ANDROID
using Appostazja.Maui.Platforms.Android.Handlers.Map;
#endif
using Microsoft.Extensions.Logging;
using Plainer.Maui;
using QuestPDF.Infrastructure;
using RestSharp;

namespace Appostazja.Maui;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .UseMauiMaps()
            .UseSentry(options =>
            {
                options.Dsn = "https://23629d2cfec54e50ac8b5acd39a6188a@o866902.ingest.sentry.io/6779536";
#if DEBUG
                options.Debug = true;
#endif

                // Set TracesSampleRate to 1.0 to capture 100% of transactions for performance monitoring.
                // We recommend adjusting this value in production.
                options.TracesSampleRate = 1.0;
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
            .ConfigureMauiHandlers(handlers =>
            {
                handlers.AddPlainer();

#if ANDROID
                handlers.AddHandler<ApostasyMap, ApostasyMapHandler>();
#endif
            })
            .UseMauiCommunityToolkit()
            .RegisterViewsAndViewModels()
            .Services
            .RegisterServices()
            .RegisterEssentials();
            //.AddSingleton<GeoJsonMap>();

#if DEBUG
        builder.Logging.AddDebug();
#endif

        QuestPDF.Settings.License = LicenseType.Community;

        return builder.BuildWithMavvm();
    }

    private static IServiceCollection RegisterServices(this IServiceCollection services)
    {
        services.AddSingleton<IRestClient, RestClient>();
        services.AddSingleton<IDataService, DataService>();

        return services;
    }

    private static IServiceCollection RegisterEssentials(this IServiceCollection services)
    {
        services.AddSingleton<IAppInfo>(AppInfo.Current);

        return services;
    }

    private static MauiAppBuilder RegisterViewsAndViewModels(this MauiAppBuilder builder)
    {
        builder.AddRoute<MainView, MainViewModel>();
        builder.AddRoute<AboutView, AboutViewModel>();
        builder.AddRoute<SettingsView, SettingsViewModel>();
        builder.AddRoute<FormView, FormViewModel>();
        builder.AddRoute<MapView, MapViewModel>();

        return builder;
    }
}