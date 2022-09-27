using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace Appostazja.Maui.ViewModels;

public partial class MainViewModel : ObservableObject
{
    public MainViewModel()
    {
    }

    [RelayCommand]
    public async Task NavigateToSettings()
    {
        await Shell.Current.GoToAsync("//Main/Settings");
    }

    [RelayCommand]
    public async Task NavigateToForm()
    {
        await Shell.Current.GoToAsync("//Main/Form");
    }
}

