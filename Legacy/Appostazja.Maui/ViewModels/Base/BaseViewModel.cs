using Appostazja.Maui.Models;

namespace Appostazja.Maui.ViewModels.Base;

public partial class BaseViewModel : ObservableObject
{
    [ObservableProperty]
    public partial string? CurrentState { get; set; } = States.Loading;

    public BaseViewModel()
    {
    }

    public virtual Task OnNavigatedToAsync()
    {
        CurrentState = null;

        return Task.CompletedTask;
    }
}