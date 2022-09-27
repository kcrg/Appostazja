namespace Appostazja.Maui.Controls;

public class BorderlessEditor : Editor
{
    public BorderlessEditor()
    {
        var transparentBackgroundSetter = new Setter
        {
            Property = BackgroundColorProperty,
            Value = Colors.Transparent
        };

        Trigger focusedTrigger = new(typeof(Editor))
        {
            Property = IsFocusedProperty,
            Value = true
        };
        focusedTrigger.Setters.Add(transparentBackgroundSetter);

        Triggers.Add(focusedTrigger);
    }
}