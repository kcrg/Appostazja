namespace Appostazja.Maui.ViewModels;

public sealed partial class MainViewModel(INavigationService navigationService) : ObservableObject
{
    [RelayCommand]
    private Task NavigateToAboutAsync() =>
        navigationService.NavigateToAsync(AppShell.AboutRoute);

    [RelayCommand]
    private Task NavigateToFormAsync() =>
        navigationService.NavigateToAsync(AppShell.FormRoute);

    [RelayCommand]
    private Task NavigateToMapAsync() =>
        navigationService.NavigateToAsync(AppShell.MapRoute);
}