namespace Appostazja.Maui.Controls;

public class BorderlessEntry : Entry
{
    public BorderlessEntry()
    {
        var transparentBackgroundSetter = new Setter
        {
            Property = BackgroundColorProperty,
            Value = Colors.Transparent
        };

        Trigger focusedTrigger = new(typeof(Entry))
        {
            Property = IsFocusedProperty,
            Value = true
        };
        focusedTrigger.Setters.Add(transparentBackgroundSetter);

        Triggers.Add(focusedTrigger);
    }
}