namespace Appostazja.Maui.Views;

public partial class FormView : BasePage
{
    public FormView(FormViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }

    protected override async void OnDisappearing()
    {
        base.OnDisappearing();

        if (BindingContext is FormViewModel viewModel)
        {
            await viewModel.SaveDraftNowAsync();
        }
    }
}