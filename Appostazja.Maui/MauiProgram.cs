using Appostazja.Maui.Controls;
using Appostazja.Maui.ViewModels;
using Appostazja.Maui.Views;
using CommunityToolkit.Maui;
using Microsoft.Extensions.Logging;

namespace Appostazja.Maui;

public static class MauiProgram
{
	public static MauiApp CreateMauiApp()
	{
		var builder = MauiApp.CreateBuilder();
		builder
			.UseMauiApp<App>()
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
			})
			.UseMauiCommunityToolkit()
			.Services
			.AddTransient<MainView>()
			.AddTransient<MainViewModel>();

		Microsoft.Maui.Handlers.EntryHandler.Mapper.AppendToMapping("Borderless", (handler, view) =>
		{
			if (view is BorderlessEntry)
			{
#if ANDROID
				handler.PlatformView.Background = null;
				handler.PlatformView.SetBackgroundColor(Android.Graphics.Color.Transparent);
#elif IOS || MACCATALYST
				handler.PlatformView.BackgroundColor = UIKit.UIColor.Clear;
				handler.PlatformView.Layer.BorderWidth = 0;
				handler.PlatformView.BorderStyle = UIKit.UITextBorderStyle.None;
#elif WINDOWS
				handler.PlatformView.BorderThickness = new Microsoft.UI.Xaml.Thickness(0);
#endif
			}
		});

        Microsoft.Maui.Handlers.EditorHandler.Mapper.AppendToMapping("Borderless", (handler, view) =>
        {
            if (view is BorderlessEditor)
            {
#if ANDROID
				handler.PlatformView.Background = null;
				handler.PlatformView.SetBackgroundColor(Android.Graphics.Color.Transparent);
#elif IOS || MACCATALYST
                handler.PlatformView.BackgroundColor = UIKit.UIColor.Clear;
                handler.PlatformView.Layer.BorderWidth = 0;
#elif WINDOWS
				handler.PlatformView.BorderThickness = new Microsoft.UI.Xaml.Thickness(0);
#endif
            }
        });

        Microsoft.Maui.Handlers.DatePickerHandler.Mapper.AppendToMapping("Borderless", (handler, view) =>
        {
            if (view is BorderlessDatePicker)
            {
#if ANDROID
				handler.PlatformView.Background = null;
				handler.PlatformView.SetBackgroundColor(Android.Graphics.Color.Transparent);
#elif IOS || MACCATALYST
                handler.PlatformView.BackgroundColor = UIKit.UIColor.Clear;
                handler.PlatformView.Layer.BorderWidth = 0;
#elif WINDOWS
				handler.PlatformView.BorderThickness = new Microsoft.UI.Xaml.Thickness(0);
#endif
            }
        });

#if DEBUG
        builder.Logging.AddDebug();
#endif

		return builder.Build();
	}
}
