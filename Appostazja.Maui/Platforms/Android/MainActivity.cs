using Android.App;
using Android.Content.PM;
using Android.OS;
using AndroidX.Core.View;

namespace Appostazja.Maui;

[Activity(
    Theme = "@style/Maui.SplashTheme",
    MainLauncher = true,
    ConfigurationChanges =
        ConfigChanges.ScreenSize |
        ConfigChanges.Orientation |
        ConfigChanges.UiMode |
        ConfigChanges.ScreenLayout |
        ConfigChanges.SmallestScreenSize |
        ConfigChanges.Density)]
public class MainActivity : MauiAppCompatActivity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        WindowCompat.SetDecorFitsSystemWindows(Window, false);
        // Shell uses resource-backed fragments. After incremental deployment, restored
        // fragments can point to obsolete view IDs and crash before MAUI starts.
        base.OnCreate(null);
    }
}