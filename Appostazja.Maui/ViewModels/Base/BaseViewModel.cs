using Appostazja.Maui.Models;

namespace Appostazja.Maui.ViewModels.Base;

public partial class BaseViewModel : ObservableObject
{
    [ObservableProperty]
    string? currentState = States.Loading;

    public BaseViewModel()
    {
    }

    public virtual Task OnNavigatedToAsync()
    {
        CurrentState = null;

        return Task.CompletedTask;
    }
}