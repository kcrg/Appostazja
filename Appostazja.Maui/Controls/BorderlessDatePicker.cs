namespace Appostazja.Maui.Controls;

public class BorderlessDatePicker : DatePicker
{
    public BorderlessDatePicker()
    {
        var transparentBackgroundSetter = new Setter
        {
            Property = BackgroundColorProperty,
            Value = Colors.Transparent
        };

        Trigger focusedTrigger = new(typeof(DatePicker))
        {
            Property = IsFocusedProperty,
            Value = true
        };
        focusedTrigger.Setters.Add(transparentBackgroundSetter);

        Triggers.Add(focusedTrigger);
    }
}