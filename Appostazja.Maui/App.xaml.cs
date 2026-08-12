namespace Appostazja.Maui;

public partial class App : Application
{
    private readonly IServiceProvider serviceProvider;

    public App(IServiceProvider serviceProvider)
    {
        InitializeComponent();
        this.serviceProvider = serviceProvider;
    }

    protected override Window CreateWindow(IActivationState? activationState) =>
        new(serviceProvider.GetRequiredService<AppShell>());
}
