namespace Appostazja.Maui.Services;

public interface INavigationService
{
    Task NavigateToAsync(string route);
}