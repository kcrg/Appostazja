namespace Appostazja.Maui.Controls;

public partial class BaseToolbarItem : ToolbarItem
{
    private readonly FontImageSource fontImageSource = new();

#pragma warning disable CS0169

    [AutoBindable(DefaultBindingMode = nameof(BindingMode.OneTime))]
    private readonly string? glyph;

    [AutoBindable(DefaultBindingMode = nameof(BindingMode.OneTime))]
    private readonly Color? glyphColor;

#pragma warning restore CS0169

    public BaseToolbarItem()
    {
        fontImageSource.FontFamily = "FontIcons";
        fontImageSource.Size = 28;
        IconImageSource = fontImageSource;
    }

    protected override void OnPropertyChanged(string? propertyName = null)
    {
        base.OnPropertyChanged(propertyName);

        if (propertyName == GlyphProperty.PropertyName)
        {
            fontImageSource.Glyph = Glyph;
        }
        else if (propertyName == GlyphColorProperty.PropertyName)
        {
            fontImageSource.Color = GlyphColor;
        }
    }
}