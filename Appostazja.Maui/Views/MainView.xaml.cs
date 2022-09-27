using Appostazja.Maui.ViewModels;

namespace Appostazja.Maui.Views;

public partial class MainView : ContentPage
{
	public MainView(IServiceProvider provider)
	{
		InitializeComponent();

		BindingContext = provider.GetService<MainViewModel>();
    }
}