using Appostazja.Maui.Models;
using Appostazja.Maui.Models.Map;
using Microsoft.Maui.Controls.Maps;

namespace Appostazja.Maui.Controls.Map;

public class ChurchPin : Pin
{
    public required PropertiesModel Data { get; set; }

    public static readonly BindableProperty ImageSourceProperty = BindableProperty.Create(nameof(ImageSource), typeof(ImageSource), typeof(ChurchPin));

    public ImageSource? ImageSource
    {
        get => (ImageSource?)GetValue(ImageSourceProperty);
        set => SetValue(ImageSourceProperty, value);
    }
}
