using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace Appostazja.Maui.ViewModels;

public partial class FormViewModel : ObservableObject
{
    [ObservableProperty]
    string? name;

    [ObservableProperty]
    DateTime baptismDate;

    [ObservableProperty]
    string? baptismParish;

    [ObservableProperty]
    bool submittedInBaptismParish;

    [ObservableProperty]
    string? homeAddress;

    [ObservableProperty]
    string? reason;

    public FormViewModel()
    {
    }

    [RelayCommand]
    public void GenerateApplication()
    {

    }
}

