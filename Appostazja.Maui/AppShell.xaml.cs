using Microsoft.Extensions.DependencyInjection;

namespace Appostazja.Maui;

public partial class AppShell : Shell
{
    public const string HomeRoute = "home";
    public const string MapRoute = "map";
    public const string FormRoute = "form";
    public const string AboutRoute = "about";

    public AppShell(IServiceProvider services)
    {
        InitializeComponent();
        Shell.SetNavBarIsVisible(this, false);

        var tabBar = new TabBar { Route = "main" };
        tabBar.Items.Add(CreateTab<MainView>(services, HomeRoute, "Start", "IconHome"));
        tabBar.Items.Add(CreateTab<MapView>(services, MapRoute, "Mapa", "IconMap2"));
        tabBar.Items.Add(CreateTab<FormView>(services, FormRoute, "Wniosek", "IconFileText"));
        tabBar.Items.Add(CreateTab<AboutView>(services, AboutRoute, "Info", "IconInfoCircle"));

        Items.Add(tabBar);
    }

    private static Tab CreateTab<TPage>(
        IServiceProvider services,
        string route,
        string title,
        string iconKey)
        where TPage : Page
    {
        var tab = new Tab
        {
            Route = route,
            Title = title,
            Icon = new FontImageSource
            {
                FontFamily = "FontIcons",
                Glyph = GetGlyph(iconKey),
                Size = 24,
            },
        };

        var shellContent = new ShellContent
        {
            Route = $"{route}Page",
            Title = title,
            ContentTemplate = new DataTemplate(
                () => services.GetRequiredService<TPage>()),
        };

        Shell.SetNavBarIsVisible(shellContent, false);
        tab.Items.Add(shellContent);

        return tab;
    }

    private static string GetGlyph(string key) =>
        Application.Current?.Resources.TryGetValue(key, out object? value) == true &&
        value is string glyph
            ? glyph
            : string.Empty;
}