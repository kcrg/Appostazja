namespace Appostazja.Maui.Controls;

public class LazyView<TView> : ALazyView where TView : View, new()
{
    public override void LoadView()
    {
        IsLoaded = true;

        Content = new TView { BindingContext = BindingContext };
    }
}
public interface ILazyView
{
    View Content { get; set; }

    Color AccentColor { get; }

    bool IsLoaded { get; }

    void LoadView();
}

public abstract class ALazyView : ContentView, ILazyView, IDisposable
{
    public static readonly BindableProperty AccentColorProperty = BindableProperty.Create(
        nameof(AccentColor),
        typeof(Color),
        typeof(ILazyView),
        Colors.DodgerBlue,
        propertyChanged: AccentColorChanged);

    public static readonly BindableProperty UseActivityIndicatorProperty = BindableProperty.Create(
        nameof(UseActivityIndicator),
        typeof(bool),
        typeof(ILazyView),
        false,
        propertyChanged: UseActivityIndicatorChanged);

    public static readonly BindableProperty AnimateProperty = BindableProperty.Create(
        nameof(Animate),
        typeof(bool),
        typeof(ILazyView),
        false);

    public Color AccentColor
    {
        get => (Color)GetValue(AccentColorProperty);
        set => SetValue(AccentColorProperty, value);
    }

    public bool UseActivityIndicator
    {
        get => (bool)GetValue(UseActivityIndicatorProperty);
        set => SetValue(UseActivityIndicatorProperty, value);
    }

    public bool Animate
    {
        get => (bool)GetValue(AnimateProperty);
        set => SetValue(AnimateProperty, value);
    }

    public new bool IsLoaded { get; protected set; }

    public abstract void LoadView();

    public void Dispose()
    {
        if (Content is IDisposable disposable)
        {
            disposable.Dispose();
        }
    }

    protected override void OnBindingContextChanged()
    {
        if (Content is not null and not ActivityIndicator)
        {
            Content.BindingContext = BindingContext;
        }
    }

    private static void AccentColorChanged(BindableObject bindable, object oldvalue, object newvalue)
    {
        ILazyView lazyView = (ILazyView)bindable;
        if (lazyView.Content is ActivityIndicator activityIndicator)
        {
            activityIndicator.Color = (Color)newvalue;
        }
    }

    private static void UseActivityIndicatorChanged(BindableObject bindable, object oldvalue, object newvalue)
    {
        ILazyView lazyView = (ILazyView)bindable;
        bool useActivityIndicator = (bool)newvalue;

        if (useActivityIndicator)
        {
            lazyView.Content = new ActivityIndicator
            {
                Color = lazyView.AccentColor,
                HorizontalOptions = LayoutOptions.Center,
                VerticalOptions = LayoutOptions.Center,
                IsRunning = true,
            };
        }
    }
}