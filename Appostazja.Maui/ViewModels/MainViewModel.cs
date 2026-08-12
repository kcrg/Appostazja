namespace Appostazja.Maui.ViewModels;

[SectionRoute("start")]
public partial class MainViewModel : ObservableObject
{
    public MainViewModel()
    {
    }

    [RelayCommand]
    public async Task NavigateToSettings()
    {
        await BaseMethods.GoToViewModel<SettingsViewModel>();
    }

    [RelayCommand]
    public async Task NavigateToAbout()
    {
        await BaseMethods.GoToViewModel<AboutViewModel>();
    }

    [RelayCommand]
    public async Task NavigateToForm()
    {
        await BaseMethods.GoToViewModel<FormViewModel>();
    }

    [RelayCommand]
    public async Task NavigateToMap()
    {
        await BaseMethods.GoToViewModel<MapViewModel>();
    }
}