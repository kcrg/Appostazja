using Appostazja.Maui.Views;

namespace Appostazja.Maui;

public partial class AppShell : Shell
{
	public AppShell()
	{
		InitializeComponent();

        Routing.RegisterRoute("Main/Settings", typeof(SettingsView));
        Routing.RegisterRoute("Main/Form", typeof(FormView));
    }
}
