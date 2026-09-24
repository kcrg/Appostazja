namespace Appostazja.Maui.Views.Base;

public class BasePage : ContentPage
{
    public BasePage()
    {
    }

    protected override async void OnNavigatedTo(NavigatedToEventArgs args)
    {
        base.OnNavigatedTo(args);

        if (BindingContext is BaseViewModel vm)
        {
            await vm.OnNavigatedToAsync();
        }
    }
}