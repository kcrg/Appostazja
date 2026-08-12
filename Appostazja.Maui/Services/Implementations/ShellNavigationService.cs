namespace Appostazja.Maui.Services.Implementations;

public sealed class ShellNavigationService : INavigationService
{
    public Task NavigateToAsync(string route)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(route);
        return Shell.Current.GoToAsync($"//{route}");
    }
}