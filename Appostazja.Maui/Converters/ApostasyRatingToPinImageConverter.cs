using Appostazja.Maui.Models.Map;
using System.Globalization;

namespace Appostazja.Maui.Converters;

public class ApostasyRatingToPinImageConverter : IValueConverter
{
    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        return value is null || value is not RatingsModel ratingModel ? null : (object)ratingModel;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        throw new NotImplementedException();
    }
}
