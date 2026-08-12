namespace Appostazja.Maui.Views;

public partial class FormView : ContentPage
{
    public FormView(FormViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }
}